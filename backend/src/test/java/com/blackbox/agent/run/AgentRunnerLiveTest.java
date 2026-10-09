package com.blackbox.agent.run;

import com.blackbox.agent.AgentProposal;
import com.blackbox.agent.AgentTestData;
import com.blackbox.agent.skill.BreakDownTasksSkill;
import com.blackbox.agent.skill.PlanFromBriefSkill;
import com.blackbox.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 지시문이 실제 모델에서 형식에 맞는 카드를 만드는지 본다. 키(SPRING_AI_GOOGLE_GENAI_API_KEY)가 있을 때만 돈다.
 * 지어낸 안내문과 지어낸 프로젝트만 보낸다. 모델이 만든 글과 카드는 테스트 출력에 찍힌다.
 */
@SpringBootTest(properties = "spring.ai.model.chat=google-genai")
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "SPRING_AI_GOOGLE_GENAI_API_KEY", matches = ".+")
class AgentRunnerLiveTest {
    @Autowired AgentRunner runner;
    @Autowired ObjectMapper json;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;

    AgentTestData data;
    User leader;
    Project project;

    @BeforeEach void setUp() {
        data = new AgentTestData(em, transactions, jdbc);
        leader = data.user("김팀장");
        project = data.project("캡스톤 디자인", leader);
    }

    @AfterEach void cleanUp() { data.cleanup(); }

    @Test void aBriefBecomesCardsInTheAgreedFormat() throws Exception {
        String brief = "캡스톤 디자인 중간 평가 안내. 중간 보고서를 " + LocalDate.now().plusDays(14) + "까지 LMS 과제함에 PDF로 제출한다. "
                + "보고서에는 문제 정의, 대상 사용자, 시스템 설계가 반드시 들어가야 하고 분량은 10쪽 안팎을 권장한다. "
                + "최종 발표는 학기 말에 하며 날짜는 추후 공지한다.";

        Recorder events = run(PlanFromBriefSkill.NAME, json.createObjectNode().put("briefText", brief).toString());

        assertEquals("done", events.last(), events.seen.toString());
        assertFalse(events.proposals.isEmpty(), "카드가 하나 이상 만들어진다");
        assertTrue(events.proposals.stream().allMatch(p -> p.getKind().equals("DELIVERABLE_PLAN") && p.isPending()));
    }

    @Test void requirementsBecomeTaskDraftsTiedToTheDeliverable() throws Exception {
        Deliverable report = data.deliverable(project, "중간 보고서", LocalDate.now().plusDays(14));
        DeliverableRequirement first = data.requirement(report, "문제 정의와 대상 사용자를 적는다", true);
        DeliverableRequirement second = data.requirement(report, "시스템 설계를 그림과 함께 설명한다", true);

        Recorder events = run(BreakDownTasksSkill.NAME, "{\"deliverableId\":\"" + report.getId() + "\"}");

        assertEquals("done", events.last(), events.seen.toString());
        assertEquals(1, events.proposals.size());
        AgentProposal card = events.proposals.get(0);
        assertEquals(report.getId().toString(), card.getContent().path("deliverableId").asText());
        List<String> linked = new ArrayList<>();
        card.getContent().path("tasks").forEach(t -> linked.add(t.path("requirementId").asText()));
        assertTrue(linked.contains(first.getId().toString()) && linked.contains(second.getId().toString()),
                "요구사항마다 업무가 붙는다: " + card.getContent());
    }

    private Recorder run(String skill, String input) throws Exception {
        Recorder events = new Recorder();
        runner.execute(runner.prepare(project.getId(), leader, skill, json.readTree(input)), events);
        System.out.println("[" + skill + "] " + events.seen);
        events.proposals.forEach(p -> System.out.println("[" + skill + "] " + p.getTitle() + " / " + p.getRationale() + " / " + p.getContent()));
        return events;
    }

    static class Recorder implements AgentEvents {
        final List<String> seen = new ArrayList<>();
        final List<AgentProposal> proposals = new ArrayList<>();
        String last() { return seen.get(seen.size() - 1).split(" ")[0]; }
        @Override public void step(String tool, String label) { seen.add("step " + tool); }
        @Override public void text(String text) { seen.add("text " + text); }
        @Override public void proposal(AgentProposal proposal) { seen.add("proposal " + proposal.getKind()); proposals.add(proposal); }
        @Override public void done(UUID runId, List<UUID> proposalIds) { seen.add("done"); }
        @Override public void error(String detail) { seen.add("error " + detail); }
    }
}
