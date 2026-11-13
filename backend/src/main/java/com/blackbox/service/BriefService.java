package com.blackbox.service;

import com.blackbox.agent.AgentProperties;
import com.blackbox.dto.BriefDtos.*;
import com.blackbox.dto.DeliverableDtos.BriefSummary;
import com.blackbox.entity.*;
import com.blackbox.exception.CodedException;
import com.blackbox.exception.NotFoundException;
import com.blackbox.repository.DeliverableBriefRepository;
import com.blackbox.repository.FileVaultRepository;
import com.blackbox.repository.ProjectBriefRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 과제 안내문 공간과 제출물 연결(K-31 2·3장) */
@Service @RequiredArgsConstructor @Transactional
public class BriefService {
    private final ProjectBriefRepository briefs;
    private final DeliverableBriefRepository links;
    private final FileVaultRepository files;
    private final ProjectAccessChecker access;
    private final DeliverableStatuses statuses;
    private final AgentProperties properties;

    @Transactional(readOnly = true)
    public List<Response> list(UUID projectId, User user) {
        Project project = access.getProject(projectId);
        access.requireMember(project, user);
        return responses(project, briefs.findByProjectOrderByUpdatedAtDesc(project));
    }

    @Transactional(readOnly = true)
    public Response get(UUID projectId, UUID briefId, User user) {
        Project project = access.getProject(projectId);
        access.requireMember(project, user);
        return response(project, find(project, briefId));
    }

    public Response create(UUID projectId, SaveRequest req, User user) {
        Project project = access.getProject(projectId);
        access.requireContributor(project, user);
        ProjectBrief brief = ProjectBrief.create(project, title(req), body(req), sourceFile(project, req), user, now());
        return response(project, briefs.save(brief));
    }

    // ponytail: 걸린 제출물의 확정과 동시에 들어온 수정은 막지 못한다. 문제가 되면 걸린 제출물 행을 잠근 뒤 확인한다
    public Response update(UUID projectId, UUID briefId, SaveRequest req, User user) {
        Project project = access.getProject(projectId);
        access.requireContributor(project, user);
        ProjectBrief brief = find(project, briefId);
        Response current = response(project, brief);
        if (current.locked()) {
            String confirmed = titles(current.usage().deliverables().stream().filter(d -> DeliverableStatusReader.isConfirmed(d.status())).toList());
            throw new CodedException(HttpStatus.CONFLICT, "BRIEF_LOCKED", "확정된 제출물(" + confirmed
                    + ")이 쓰는 안내문은 고칠 수 없습니다. 바뀐 내용은 추가 공지로 새로 등록해 주세요", Map.of());
        }
        brief.edit(title(req), body(req), sourceFile(project, req), user, now());
        return response(project, brief);
    }

    /** 쓰는 곳이 있으면 막는다(2.4). 그 사이에 걸린 연결은 DB의 외래 키가 막아 409가 된다 */
    public void delete(UUID projectId, UUID briefId, User user) {
        Project project = access.getProject(projectId);
        access.requireContributor(project, user);
        ProjectBrief brief = find(project, briefId);
        Usage usage = response(project, brief).usage();
        if (!usage.isEmpty())
            throw new CodedException(HttpStatus.CONFLICT, "BRIEF_IN_USE", inUse(usage), Map.of("usage", usage));
        briefs.delete(brief);
    }

    /** 이미 걸려 있으면 지금 배열을 그대로 돌려준다(3장의 중복 클릭) */
    public List<BriefSummary> link(UUID projectId, UUID deliverableId, UUID briefId, User user) {
        Project project = access.getProject(projectId);
        access.requireContributor(project, user);
        Deliverable deliverable = statuses.lockEditable(project, deliverableId);
        ProjectBrief brief = find(project, briefId);
        if (!links.existsById(new DeliverableBrief.Key(deliverable.getId(), brief.getId())))
            links.save(new DeliverableBrief(deliverable, brief, user, now()));
        return summaries(deliverable);
    }

    /** 걸려 있지 않으면 지금 배열을 그대로 돌려준다 */
    public List<BriefSummary> unlink(UUID projectId, UUID deliverableId, UUID briefId, User user) {
        Project project = access.getProject(projectId);
        access.requireContributor(project, user);
        Deliverable deliverable = statuses.lockEditable(project, deliverableId);
        links.deleteById(new DeliverableBrief.Key(deliverable.getId(), find(project, briefId).getId()));
        return summaries(deliverable);
    }

    /** DB에서 다시 읽은 값과 같은 모양(UTC, 마이크로초)이라, 저장한 요청의 응답과 다음 조회의 응답이 같다 */
    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    private List<BriefSummary> summaries(Deliverable deliverable) {
        return links.findByDeliverable(deliverable).stream().map(BriefSummary::from).toList();
    }

    private ProjectBrief find(Project project, UUID briefId) {
        return briefs.findByIdAndProject(briefId, project).orElseThrow(() -> new NotFoundException("안내문을 찾을 수 없습니다"));
    }

    // 화면은 detail을 그대로 보여 주므로, 무엇이 막는지와 어떻게 풀지를 문구에 담는다
    private static String inUse(Usage usage) {
        List<String> users = new ArrayList<>();
        if (!usage.deliverables().isEmpty()) users.add("제출물 " + titles(usage.deliverables()));
        if (usage.pendingProposals() > 0) users.add("검토 대기 제안 " + usage.pendingProposals() + "개");
        return "이 안내문을 쓰는 곳이 있어 지울 수 없습니다(" + String.join(", ", users) + "). 제출물에서 떼거나 제안을 처리한 뒤 지워 주세요";
    }

    private static String titles(List<UsedBy> deliverables) {
        return deliverables.stream().map(d -> "'" + d.title() + "'").collect(Collectors.joining(", "));
    }

    private static String title(SaveRequest req) {
        String title = required(req.title(), "제목");
        if (title.length() > 200) throw badRequest("제목은 최대 200자입니다. 지금 " + title.length() + "자입니다");
        return title;
    }

    private String body(SaveRequest req) {
        String body = required(req.body(), "본문");
        int max = properties.getBriefMaxLength();
        if (body.length() > max) throw badRequest("본문은 최대 " + max + "자입니다. 지금 " + body.length() + "자입니다");
        return body;
    }

    // 유니코드 공백(전각 공백, 웹에서 복사한 줄바꿈 없는 공백 포함)과 파일 앞의 BOM. String.strip()은 뒤의 둘을 떼지 않는다
    private static final Pattern EDGE_BLANKS = Pattern.compile("^[\\p{IsWhite_Space}\\uFEFF]+|[\\p{IsWhite_Space}\\uFEFF]+$");

    /**
     * 저장할 모양으로 맞춘 값(K-31 2.5). 눈에 같은 글이 같은 해시가 되도록 유니코드 NFC(macOS에서 붙여 넣은 한글의 풀어쓴 자모를 모은다)와
     * 줄바꿈(\r\n, \r → \n)을 맞추고 앞뒤 공백을 뗀다. 줄 안의 글자는 원문 그대로 둔다
     */
    private static String required(String value, String label) {
        String text = EDGE_BLANKS.matcher(Normalizer.normalize(value, Normalizer.Form.NFC).replace("\r\n", "\n").replace('\r', '\n')).replaceAll("");
        if (text.isEmpty()) throw badRequest(label + "을 입력해 주세요");
        return text;
    }

    private static ResponseStatusException badRequest(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }

    private FileVault sourceFile(Project project, SaveRequest req) {
        return req.sourceFileId() == null ? null : files.findByIdAndProject(req.sourceFileId(), project)
                .orElseThrow(() -> new NotFoundException("파일을 찾을 수 없습니다"));
    }

    private Response response(Project project, ProjectBrief brief) {
        return responses(project, List.of(brief)).getFirst();
    }

    /** 안내문이 몇 개든 조회는 프로젝트 단위로 여섯 번이다(연결, 제출물 상태 네 번, 검토 대기 카드) */
    private List<Response> responses(Project project, List<ProjectBrief> list) {
        List<DeliverableBrief> all = links.findByProject(project);
        Map<UUID, DeliverableStatuses.Status> status = statuses.of(project, all.stream().map(DeliverableBrief::getDeliverable).distinct().toList());
        Map<UUID, List<UsedBy>> used = all.stream().collect(Collectors.groupingBy(l -> l.getBrief().getId(), Collectors.mapping(l ->
                new UsedBy(l.getDeliverable().getId(), l.getDeliverable().getTitle(), status.get(l.getDeliverable().getId()).status()),
                Collectors.toList())));
        Map<UUID, Long> pending = new HashMap<>();
        for (Object[] row : briefs.countPendingProposals(project.getId())) pending.put((UUID) row[0], ((Number) row[1]).longValue());
        return list.stream().map(b -> Response.from(b,
                new Usage(used.getOrDefault(b.getId(), List.of()), pending.getOrDefault(b.getId(), 0L)))).toList();
    }
}
