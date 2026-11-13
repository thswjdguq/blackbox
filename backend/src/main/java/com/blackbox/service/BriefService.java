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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
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
    private static final int FIRST_LINE_MAX = 60;   // 제목이 없을 때 본문 첫 줄에서 쓰는 글자 수

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

    /**
     * 계획 만들기(K-31 4장)가 실행 전에 입력을 확인한다. 안내문 공간과 같은 규칙으로 맞춘 본문을 돌려주고, 맞지 않으면 400이다.
     * 저장은 하지 않는다. 막힌 실행(409·429)으로 안내문이 쌓이지 않게, 저장은 실행이 자리를 얻은 뒤 useForPlan이 한다
     */
    public PlanInput checkForPlan(String title, String body) {
        return new PlanInput(title == null || ProjectBrief.normalize(title).isEmpty() ? null : title(title), body(body, "과제 안내문"));
    }

    /** 계획 만들기의 입력. title이 null이면 저장할 때 날짜로 정한다 */
    public record PlanInput(String title, String body) {}

    /** 계획 만들기가 쓴 안내문. created는 이번에 새로 저장했는가다 */
    public record Used(UUID id, String title, String body, boolean created) {
        static Used of(ProjectBrief b, boolean created) { return new Used(b.getId(), b.getTitle(), b.getBody(), created); }
    }

    /** 저장된 안내문으로 계획을 만든다. 다른 프로젝트의 안내문이면 404다 */
    @Transactional(readOnly = true)
    public Used findForPlan(UUID projectId, UUID briefId, User user) {
        Project project = access.getProject(projectId);
        access.requireContributor(project, user);
        return Used.of(find(project, briefId), false);
    }

    /** 본문이 같은 안내문이 있으면 그것을 쓰고(가장 먼저 만든 것), 없으면 저장한다. 제목이 없으면 본문의 첫 줄이다(K-31 0.4 4장) */
    public Used useForPlan(UUID projectId, PlanInput input, User user) {
        Project project = access.getProject(projectId);
        access.requireContributor(project, user);
        Optional<ProjectBrief> same = briefs.findFirstByProjectAndBodyHashOrderByCreatedAtAsc(project, ProjectBrief.hash(input.body()));
        if (same.isPresent()) return Used.of(same.get(), false);
        String title = input.title() != null ? input.title() : firstLine(input.body());
        return Used.of(briefs.save(ProjectBrief.create(project, title, input.body(), null, user, now())), true);
    }

    /**
     * 안내문의 첫 줄은 보통 제목이다("[캡스톤디자인] 중간 보고서 제출 안내"). 날짜로 정하면 같은 날 붙여 넣은 안내문끼리 제목이 같아진다.
     * 정규화한 본문은 공백으로 시작하지 않으므로 첫 줄은 비지 않는다. 길면 앞 60자(코드 포인트)에 "…"를 붙인다.
     * 표시가 없으면 "…10월 18"처럼 잘린 글이 다른 뜻으로 읽힌다
     */
    private static String firstLine(String body) {
        String line = body.lines().findFirst().orElseThrow();
        if (line.codePointCount(0, line.length()) <= FIRST_LINE_MAX) return ProjectBrief.normalize(line);
        return ProjectBrief.normalize(line.substring(0, line.offsetByCodePoints(0, FIRST_LINE_MAX))) + "…";
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

    private static String title(SaveRequest req) { return title(req.title()); }

    private static String title(String value) {
        String title = required(value, "제목");
        if (title.length() > 200) throw badRequest("제목은 최대 200자입니다. 지금 " + title.length() + "자입니다");
        return title;
    }

    private String body(SaveRequest req) { return body(req.body(), "본문"); }

    /** label은 화면에서 그 칸을 부르는 말이다. 안내문 공간은 "본문", 계획 만들기는 "과제 안내문" */
    private String body(String value, String label) {
        String body = required(value, label);
        int max = properties.getBriefMaxLength();
        if (body.length() > max) throw badRequest(label + "은 최대 " + max + "자입니다. 지금 " + body.length() + "자입니다");
        return body;
    }

    /** 저장할 모양으로 맞춘 값(ProjectBrief.normalize). 비었으면 400 */
    private static String required(String value, String label) {
        String text = value == null ? "" : ProjectBrief.normalize(value);
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
