package com.blackbox.agent.proposal;

import com.blackbox.agent.AgentProperties;
import com.blackbox.agent.AgentProposal;
import com.blackbox.agent.draft.DocxWriter;
import com.blackbox.agent.draft.GeneratedFile;
import com.blackbox.dto.DeliverableDtos.RequirementResponse;
import com.blackbox.dto.ReviewDtos;
import com.blackbox.entity.Deliverable;
import com.blackbox.entity.Meeting;
import com.blackbox.entity.Project;
import com.blackbox.entity.Task;
import com.blackbox.entity.User;
import com.blackbox.exception.NotFoundException;
import com.blackbox.repository.MeetingRepository;
import com.blackbox.repository.TaskRepository;
import com.blackbox.service.DeliverableService;
import com.blackbox.service.DeliverableStatuses;
import com.blackbox.service.FileVaultService;
import com.blackbox.service.ProjectAccessChecker;
import com.blackbox.service.ReviewRoundService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 문서 초안(K-59 3장). 채택하면 .docx를 만들어 파일 금고에 올리고, 그 파일로 제출물의 검토 회차를 연다.
 * 장의 근거(업무·회의·요구사항 id)는 같은 프로젝트의 기록만 남긴다. 없는 id는 빼고, 모델이 쓴 것을 뺐으면 실행의 글에 알린다(DraftDocumentSkill).
 * "[작성 필요: …]"의 개수(missingCount)는 서버가 검증할 때마다 다시 센다
 */
@Component
@RequiredArgsConstructor
public class DocumentDraftKind implements ProposalKind {
    public static final String NAME = "DOCUMENT_DRAFT";
    static final int MAX_SECTIONS = 6, TITLE_MAX = 255, SECTION_TITLE_MAX = 100, BODY_MAX = 1200;
    static final String DRAFT_SUFFIX = " 초안.docx";
    private static final String[] SOURCE_FIELDS = {"taskIds", "meetingIds", "requirementIds"};

    private final ObjectMapper json;
    private final ProjectAccessChecker access;
    private final DeliverableService deliverables;
    private final DeliverableStatuses statuses;
    private final TaskRepository tasks;
    private final MeetingRepository meetings;
    private final FileVaultService files;
    private final ReviewRoundService reviews;
    private final AgentProperties properties;

    @Override public String name() { return NAME; }

    /** 근거로 쓸 수 있는 id: 그 프로젝트의 업무와 회의, 그 제출물의 요구사항. 순서는 taskIds, meetingIds, requirementIds */
    public record Known(Set<UUID> taskIds, Set<UUID> meetingIds, Set<UUID> requirementIds) {
        Set<UUID> of(String field) {
            return switch (field) { case "taskIds" -> taskIds; case "meetingIds" -> meetingIds; default -> requirementIds; };
        }
    }

    public Known known(UUID projectId, User user, UUID deliverableId) {
        Project project = access.getProject(projectId);
        return new Known(
                tasks.findByProjectOrderByCreatedAtDesc(project).stream().map(Task::getId).collect(Collectors.toSet()),
                meetings.findByProjectOrderByMeetingDateDesc(project).stream().map(Meeting::getId).collect(Collectors.toSet()),
                deliverables.get(projectId, deliverableId, user).requirements().stream().map(RequirementResponse::id).collect(Collectors.toSet()));
    }

    /**
     * 장마다 근거에서 known에 없는 id(형식이 틀린 것 포함)를 빼고 그 개수를 돌려준다. 같은 칸에 겹친 id는 한 번만 남기되,
     * 확인할 수 없는 근거가 아니므로 개수에 넣지 않는다. 근거 칸이 없으면 빈 칸으로 채운다
     */
    public static int keepKnown(ObjectNode content, Known known) {
        int dropped = 0;
        JsonNode sections = content.path("sections");
        for (int i = 0; sections.isArray() && i < sections.size(); i++) {
            if (!(sections.get(i) instanceof ObjectNode section)) continue;
            JsonNode given = section.path("sources");
            ObjectNode sources = section.putObject("sources");
            for (String field : SOURCE_FIELDS) {
                ArrayNode kept = sources.putArray(field);
                JsonNode ids = given.path(field);
                for (int j = 0; ids.isArray() && j < ids.size(); j++) {
                    UUID id = uuid(ids.get(j).asString(""));
                    if (id == null || !known.of(field).contains(id)) dropped++;
                    else if (kept.valueStream().noneMatch(v -> v.asString().equals(id.toString()))) kept.add(id.toString());
                }
            }
        }
        return dropped;
    }

    /** "[작성 필요: …]"의 개수 */
    public static int missing(JsonNode content) {
        int count = 0;
        for (JsonNode section : content.path("sections")) {
            var m = DocxWriter.MISSING.matcher(section.path("body").asString(""));
            while (m.find()) count++;
        }
        return count;
    }

    @Override public Checked check(UUID projectId, User user, JsonNode content, boolean forAccept) {
        if (!(content instanceof ObjectNode given)) return Checked.unreadable(content);
        // 근거를 먼저 거른다. 형식이 틀린 id 하나로 카드 전체를 읽지 못하게 되지 않는다
        ObjectNode filtered = given.deepCopy();
        List<String> problems = new ArrayList<>();
        UUID deliverableId = uuid(filtered.path("deliverableId").asString(""));
        if (deliverableId == null) problems.add("deliverableId: 없다");
        else try {
            keepKnown(filtered, known(projectId, user, deliverableId));
        } catch (NotFoundException e) {
            problems.add("deliverableId: 이 프로젝트의 제출물이 아니다");
        }
        Content c;
        try {
            c = json.treeToValue(filtered, Content.class);
        } catch (JacksonException | IllegalArgumentException e) {
            return Checked.unreadable(content);
        }
        ObjectNode kept = json.valueToTree(c);
        if (c.outlineProposalId() == null) problems.add("outlineProposalId: 없다");
        text(problems, "title", c.title(), TITLE_MAX);
        List<Section> sections = c.sections() == null ? List.of() : c.sections();
        if (sections.isEmpty() || sections.size() > MAX_SECTIONS) problems.add("sections: 1-" + MAX_SECTIONS + "개여야 한다");
        for (int i = 0; i < sections.size(); i++) {
            Section s = sections.get(i);
            if (s == null) { problems.add("sections[" + i + "]: 비어 있다"); continue; }
            text(problems, "sections[" + i + "].title", s.title(), SECTION_TITLE_MAX);
            text(problems, "sections[" + i + "].body", s.body(), BODY_MAX);
        }
        kept.put("missingCount", missing(kept));
        return new Checked(kept, problems);
    }

    /**
     * 제출물을 먼저 잠가 확정·제출된 제출물이면 파일을 올리기 전에 409다. 그다음 화면에서 올릴 때와 같은 길로 파일 금고에 올리고(활동 기록 포함)
     * 그 파일로 검토 회차를 연다. 부르는 쪽의 트랜잭션 하나라 하나라도 실패하면 DB 기록이 남지 않고, 디스크의 파일은 파일 금고가 지운다
     */
    @Override public List<AgentProposal.Result> accept(UUID projectId, User user, JsonNode content) {
        Content c = json.convertValue(content, Content.class);
        Deliverable d = statuses.lockEditable(access.getProject(projectId), c.deliverableId());
        byte[] docx = DocxWriter.write(new DocxWriter.Document(c.title(), c.sections().stream()
                .map(s -> new DocxWriter.Section(s.title(), s.body(), sourcesLine(s.sources()))).toList()));
        UUID fileId;
        try {
            fileId = files.upload(projectId, new GeneratedFile(fileName(d.getTitle()), GeneratedFile.DOCX, docx), user).id();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        UUID roundId = reviews.open(projectId, d.getId(), new ReviewDtos.OpenRequest(fileId), user).id();
        return List.of(new AgentProposal.Result(TargetTypes.FILE, fileId), new AgentProposal.Result(TargetTypes.REVIEW_ROUND, roundId));
    }

    /** 제출물과 목차는 바꿀 수 없다. 나머지는 사람이 고친다. 근거는 검증에서 다시 거른다 */
    @Override public JsonNode keepFixed(JsonNode before, JsonNode edited) {
        ObjectNode kept = ((ObjectNode) edited).deepCopy();
        kept.set("deliverableId", before.get("deliverableId"));
        kept.set("outlineProposalId", before.get("outlineProposalId"));
        return kept;
    }

    /** "{제출물 제목} 초안.docx". 경로와 파일 이름에 쓸 수 없는 문자는 _로 바꾸고, 파일 금고의 이름 칸(255자)에 들게 자른다 */
    static String fileName(String deliverableTitle) {
        String safe = deliverableTitle.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").strip();
        int max = TITLE_MAX - DRAFT_SUFFIX.length();
        if (safe.codePointCount(0, safe.length()) > max) safe = safe.substring(0, safe.offsetByCodePoints(0, max));
        return safe + DRAFT_SUFFIX;
    }

    /** 장 끝의 근거 한 줄(K-59 4장): "근거: 업무 「제목」, 회의 「제목」(날짜)". 요구사항은 문서에 넣지 않는다 */
    private String sourcesLine(Sources sources) {
        List<String> parts = new ArrayList<>();
        Map<UUID, Task> byTask = tasks.findAllById(sources.taskIds()).stream().collect(Collectors.toMap(Task::getId, Function.identity()));
        Map<UUID, Meeting> byMeeting = meetings.findAllById(sources.meetingIds()).stream().collect(Collectors.toMap(Meeting::getId, Function.identity()));
        sources.taskIds().stream().map(byTask::get).filter(t -> t != null).forEach(t -> parts.add("업무 「" + t.getTitle() + "」"));
        sources.meetingIds().stream().map(byMeeting::get).filter(m -> m != null).forEach(m -> parts.add("회의 「" + m.getTitle() + "」("
                + m.getMeetingDate().atZoneSameInstant(properties.getZone()).toLocalDate() + ")"));
        return parts.isEmpty() ? null : "근거: " + String.join(", ", parts);
    }

    private static void text(List<String> problems, String at, String value, int max) {
        if (value == null || value.isBlank()) problems.add(at + ": 비어 있다");
        else if (value.length() > max) problems.add(at + ": " + max + "자까지다");
    }

    private static UUID uuid(String s) {
        try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }

    record Content(UUID deliverableId, UUID outlineProposalId, String title, List<Section> sections) {}
    record Section(String title, String body, Sources sources) {
        Section { sources = sources == null ? new Sources(null, null, null) : sources; }
    }
    record Sources(List<UUID> taskIds, List<UUID> meetingIds, List<UUID> requirementIds) {
        Sources {
            taskIds = taskIds == null ? List.of() : taskIds;
            meetingIds = meetingIds == null ? List.of() : meetingIds;
            requirementIds = requirementIds == null ? List.of() : requirementIds;
        }
    }
}
