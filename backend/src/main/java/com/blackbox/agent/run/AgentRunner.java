package com.blackbox.agent.run;

import com.blackbox.agent.AgentProperties;
import com.blackbox.agent.AgentProposal;
import com.blackbox.agent.AgentProposalRepository;
import com.blackbox.agent.AgentRun;
import com.blackbox.agent.AgentRunRepository;
import com.blackbox.agent.model.AgentModel;
import com.blackbox.agent.model.AgentModelException;
import com.blackbox.agent.proposal.ProposalKind;
import com.blackbox.agent.proposal.ProposalKinds;
import com.blackbox.agent.skill.AgentSkill;
import com.blackbox.agent.tool.AgentToolbox;
import com.blackbox.entity.Project;
import com.blackbox.entity.ProjectMember;
import com.blackbox.entity.User;
import com.blackbox.exception.ForbiddenException;
import com.blackbox.service.ProjectAccessChecker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 실행 하나를 처음부터 끝까지 맡는다(K-30 2장·4장).
 * prepare는 스트림이 시작되기 전에 확인할 것을 보고 실행 기록을 만든다. 여기서 난 예외는 HTTP 오류가 된다.
 * execute는 모델을 부르고 결과를 알린다. 예외를 던지지 않고 done이나 error로 끝낸다.
 */
@Component
public class AgentRunner {
    private static final Logger log = LoggerFactory.getLogger(AgentRunner.class);
    public static final String FAILED = "AI 응답을 받지 못했습니다. 다시 시도해주세요";
    public static final String UNAVAILABLE = "AI 기능이 설정되지 않았습니다";

    private final Map<String, AgentSkill> skills;
    private final ProposalKinds kinds;
    private final AgentInstructions instructions;
    private final AgentToolbox toolbox;
    private final AgentModel model;
    private final AgentRunRepository runs;
    private final AgentProposalRepository proposals;
    private final ProjectAccessChecker access;
    private final AgentProperties properties;
    private final ObjectMapper json;

    public AgentRunner(List<AgentSkill> skills, ProposalKinds kinds, AgentInstructions instructions, AgentToolbox toolbox,
                       AgentModel model, AgentRunRepository runs, AgentProposalRepository proposals,
                       ProjectAccessChecker access, AgentProperties properties, ObjectMapper json) {
        // 이름이 겹치면 서버가 뜰 때 실패한다. 순서(@Order)는 화면에 보이는 순서다
        this.skills = skills.stream().collect(Collectors.toMap(AgentSkill::name, Function.identity(),
                (a, b) -> { throw new IllegalStateException("실행 종류의 이름이 겹칩니다: " + a.name()); }, LinkedHashMap::new));
        this.kinds = kinds; this.instructions = instructions; this.toolbox = toolbox; this.model = model;
        this.runs = runs; this.proposals = proposals; this.access = access; this.properties = properties; this.json = json;
    }

    public Collection<AgentSkill> skills() { return skills.values(); }

    /** contributor는 요청한 사람이 기록을 만들 수 있는 역할(팀장·팀원)인가다 */
    public record Prepared(AgentRun run, AgentSkill skill, boolean contributor, JsonNode input, String material) {}

    @Transactional
    public Prepared prepare(UUID projectId, User user, String skillName, JsonNode input) {
        Project project = access.getProject(projectId);
        ProjectMember member = access.requireMember(project, user);
        boolean contributor = ProjectAccessChecker.canContribute(member);
        AgentSkill skill = skills.get(skillName);
        if (skill == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "모르는 실행 종류입니다");
        if (!skill.allows(contributor)) throw new ForbiddenException("관찰자는 제안을 만들거나 채택할 수 없습니다");
        JsonNode given = input == null ? json.createObjectNode() : input;
        String material = skill.material(projectId, user, given);
        if (!model.available()) {
            throw new AgentModelException(AgentModelException.Reason.UNAVAILABLE, UNAVAILABLE, null);
        }

        OffsetDateTime now = OffsetDateTime.now();
        // ponytail: 확인과 기록 사이에 잠금이 없어 동시에 들어온 두 요청은 둘 다 지나간다. 문제가 되면 실행 중인 줄에 부분 유일 인덱스를 건다
        if (runs.hasRunning(project, user, now.minus(properties.getRunTimeLimit()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "앞의 요청이 끝난 뒤 다시 시도해주세요");
        }
        if (runs.countByProjectAndStartedAtAfter(project, now.minusHours(1)) >= properties.getRunsPerHour()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "잠시 뒤 다시 시도해주세요");
        }
        return new Prepared(runs.save(AgentRun.start(project, user, skill.name(), now)), skill, contributor, given, material);
    }

    public void execute(Prepared prepared, AgentEvents events) {
        AgentRun run = prepared.run();
        List<UUID> proposalIds;
        try {
            proposalIds = produce(prepared, events);
            run.finish(OffsetDateTime.now());
            runs.save(run);
        } catch (RuntimeException e) {
            // 이유는 실행 기록과 로그에만 남기고 화면에는 정해진 문장만 보낸다
            log.warn("에이전트 실행 실패 run={} skill={}", run.getId(), run.getSkill(), e);
            fail(prepared, e.toString());
            events.error(FAILED);
            return;
        }
        events.done(run.getId(), proposalIds);
    }

    /** 실행을 실패로 끝낸다. 시작하지 못한 실행을 정리할 때도 쓴다 */
    public void fail(Prepared prepared, String reason) {
        prepared.run().fail(reason, OffsetDateTime.now());
        runs.save(prepared.run());
    }

    /** 시간 상한을 넘긴 실행은 더 저장하지 않고 실패로 끝낸다. 모델이 답을 돌려줄 때마다 본다 */
    private void requireInTime(AgentRun run) {
        if (OffsetDateTime.now().isAfter(run.getStartedAt().plus(properties.getRunTimeLimit()))) {
            throw new IllegalStateException("시간 상한을 넘겼습니다");
        }
    }

    private List<UUID> produce(Prepared p, AgentEvents events) {
        UUID projectId = p.run().getProject().getId();
        User user = p.run().getRequestedBy();
        // 관찰자의 실행은 카드를 만들지 않는다(K-30 1장·2장)
        List<String> allowedKinds = p.contributor() ? p.skill().proposalKinds() : List.of();
        String guide = instructions.forRun(p.skill(), allowedKinds, projectId);
        // 안내문과 질문은 자료로만 넘긴다. 그 안의 지시는 따르지 않게 공통 지시문이 막는다
        String material = instructions.material(p.material());

        String answer = model.generate(guide, material, toolbox.bind(projectId, user, p.skill().tools(), events::step));
        requireInTime(p.run());
        if (allowedKinds.isEmpty()) {
            events.text(answer);
            return List.of();
        }

        Answer read = read(answer, p, allowedKinds);
        if (!read.problems().isEmpty()) {
            // 한 번만 다시 요청한다(K-30 6장). 기록은 다시 조회하지 않고 형식만 고치게 한다
            read = read(model.generate(guide, instructions.repair(material, answer, read.problems()), List.of()), p, allowedKinds);
            requireInTime(p.run());
            if (!read.problems().isEmpty()) throw new IllegalStateException("형식에 맞지 않는 응답: " + read.problems());
        }
        if (!read.text().isBlank()) events.text(read.text());
        List<UUID> ids = new ArrayList<>();
        for (Draft draft : read.drafts()) {
            AgentProposal saved = proposals.save(AgentProposal.propose(
                    p.run(), draft.kind(), draft.title(), draft.rationale(), draft.content(), OffsetDateTime.now()));
            events.proposal(saved);
            ids.add(saved.getId());
        }
        return ids;
    }

    private record Draft(String kind, String title, String rationale, JsonNode content) {}
    private record Answer(String text, List<Draft> drafts, List<String> problems) {}

    /** 모델의 답을 읽는다. 형식에 맞지 않는 곳이 있으면 problems에 담고, 그때는 아무것도 저장하지 않는다 */
    private Answer read(String answer, Prepared p, List<String> allowedKinds) {
        JsonNode root;
        try {
            // 모델이 코드 블록 표시나 앞뒤 말을 붙여도 객체만 읽는다
            root = json.readTree(answer.substring(answer.indexOf('{'), answer.lastIndexOf('}') + 1));
        } catch (JsonProcessingException | IndexOutOfBoundsException e) {
            return new Answer("", List.of(), List.of("답이 JSON 객체 하나가 아니다"));
        }
        List<String> problems = new ArrayList<>();
        List<Draft> drafts = new ArrayList<>();
        JsonNode list = root.path("proposals");
        if (!list.isArray() && !list.isMissingNode() && !list.isNull()) problems.add("proposals: 배열이어야 한다");
        for (int i = 0; list.isArray() && i < list.size(); i++) {
            JsonNode item = list.get(i);
            String at = "proposals[" + i + "]";
            String kind = item.path("kind").asText("");
            String title = item.path("title").asText("").strip();
            String rationale = item.path("rationale").asText("").strip();
            if (!allowedKinds.contains(kind)) { problems.add(at + ".kind: " + allowedKinds + " 중 하나여야 한다"); continue; }
            if (title.isEmpty()) problems.add(at + ".title: 비어 있다");
            if (rationale.isEmpty()) problems.add(at + ".rationale: 비어 있다");
            if (!(item.path("content") instanceof ObjectNode content)) { problems.add(at + ".content: 객체여야 한다"); continue; }
            p.skill().complete(content, p.input());
            ProposalKind.Checked checked = kinds.get(kind).check(p.run().getProject().getId(), p.run().getRequestedBy(), content);
            checked.problems().forEach(problem -> problems.add(at + ".content." + problem));
            // 저장하는 것은 그 종류가 아는 칸만 남긴 내용이다
            drafts.add(new Draft(kind, title, rationale, checked.content()));
        }
        String text = root.path("text").asText("").strip();
        if (problems.isEmpty() && text.isEmpty() && drafts.isEmpty()) problems.add("text와 proposals가 모두 비어 있다");
        return new Answer(text, drafts, problems);
    }
}
