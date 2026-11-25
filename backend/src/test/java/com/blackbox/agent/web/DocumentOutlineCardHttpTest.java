package com.blackbox.agent.web;

import com.blackbox.agent.AgentProposal;
import com.blackbox.agent.AgentRun;
import com.blackbox.agent.proposal.DocumentOutlineKind;
import com.blackbox.agent.run.AgentRunner;
import com.blackbox.agent.skill.DraftOutlineSkill;
import com.blackbox.entity.*;
import com.blackbox.security.JwtService;
import com.blackbox.service.DeliverableStatusReader;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * 문서 목차 카드(K-59 3장)의 수정과 채택. 카드는 목차 만들기가 저장한 모양 그대로 넣는다(모델 응답 처리는 AgentRunnerTest).
 * 테스트마다 트랜잭션 하나로 묶여 끝나면 되돌아간다
 */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class DocumentOutlineCardHttpTest {
    static final String PROPOSAL = "/api/projects/{p}/agent/proposals/{id}";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EntityManager em;
    @Autowired JwtService jwt;
    @Autowired AgentRunner runner;

    User leader, member;
    Project project;
    Deliverable report, other;
    DeliverableRequirement problem, design, cover, elsewhere;

    @BeforeEach void setup() {
        leader = user(em, "송팀장"); member = user(em, "손팀원");
        project = project(em, leader);
        member(em, project, leader, "LEADER"); member(em, project, member, "MEMBER");
        report = deliverable(em, project, "중간 보고서");
        problem = requirement(em, report, true, null); design = requirement(em, report, true, null); cover = requirement(em, report, false, null);
        other = deliverable(em, project, "최종 발표");
        elsewhere = requirement(em, other, true, null);
    }

    @Test void editingKeepsTheDeliverableAndRecountsUncoveredRequirements() throws Exception {
        AgentProposal outline = outline(section("문제 정의", problem));
        ObjectNode edited = (ObjectNode) outline.getContent().deepCopy();
        edited.put("deliverableId", other.getId().toString());
        edited.putArray("uncoveredRequirementIds");   // 서버가 다시 센다
        ((ObjectNode) edited.withArray("sections").get(0)).put("note", "회의 결정 사항을 덧붙인다");
        edited.withArray("sections").add(json.readTree(section("설계", design)));

        JsonNode kept = edit(outline, edited, 200).path("content");
        assertEquals(report.getId().toString(), kept.path("deliverableId").asString(), "제출물은 바꿀 수 없다");
        assertEquals("회의 결정 사항을 덧붙인다", kept.at("/sections/0/note").asString());
        assertEquals(0, kept.path("uncoveredRequirementIds").size(), "두 필수 요구사항이 모두 장에 걸렸다");

        ((ObjectNode) edited.withArray("sections").get(1)).putArray("requirementIds");
        assertEquals(List.of(design.getId().toString()),
                edit(outline, edited, 200).at("/content/uncoveredRequirementIds").valueStream().map(JsonNode::asString).toList());
    }

    @Test void anOutlineThatBreaksTheShapeIsRefused() throws Exception {
        AgentProposal outline = outline(section("문제 정의", problem));
        ObjectNode foreign = (ObjectNode) outline.getContent().deepCopy();
        ((ObjectNode) foreign.withArray("sections").get(0)).putArray("requirementIds").add(elsewhere.getId().toString());
        assertTrue(edit(outline, foreign, 400).path("detail").asString().contains("그 제출물의 요구사항이 아니다"));

        ObjectNode seven = (ObjectNode) outline.getContent().deepCopy();
        for (int i = 0; i < 6; i++) seven.withArray("sections").add(json.readTree(section("장 " + i)));
        assertTrue(edit(outline, seven, 400).path("detail").asString().contains("sections: 1-6개"));

        ObjectNode untitled = (ObjectNode) outline.getContent().deepCopy();
        ((ObjectNode) untitled.withArray("sections").get(0)).put("title", " ");
        untitled.put("title", "가".repeat(256));
        String detail = edit(outline, untitled, 400).path("detail").asString();
        assertTrue(detail.contains("sections[0].title: 비어 있다") && detail.contains("title: 255자까지다"), detail);
    }

    /** 채택은 기록을 만들지 않는다. 채택 상태가 초안 실행의 입력이 된다(K-59 3장) */
    @Test void acceptingCreatesNothing() throws Exception {
        long tasks = em.createQuery("select count(t) from Task t", Long.class).getSingleResult();
        JsonNode accepted = call(post(PROPOSAL + "/accept", project.getId(), outline(section("문제 정의", problem)).getId()), member, 200);
        assertEquals("ACCEPTED", accepted.path("status").asString());
        assertTrue(accepted.at("/results/requirementIds").isEmpty() && accepted.at("/results/taskIds").isEmpty()
                && accepted.at("/results/deliverableId").isNull());
        assertEquals(tasks, em.createQuery("select count(t) from Task t", Long.class).getSingleResult());
    }

    @Test void aConfirmedDeliverableGetsNoOutline() throws Exception {
        problem.assess(leader); design.assess(leader);   // 확정하려면 필수 요구사항의 충족 확인이 먼저다
        ReviewRound approved = round(em, report, 1, file(em, project, member, "보고서.pdf", 1, 'a'), member, "APPROVED");
        call(put("/api/projects/{p}/deliverables/{d}/confirmation", project.getId(), report.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reviewId\":\"" + approved.getId() + "\"}"), leader, 200);
        ResponseStatusException refused = assertThrows(ResponseStatusException.class, () -> runner.prepare(project.getId(), member,
                DraftOutlineSkill.NAME, json.readTree("{\"deliverableId\":\"" + report.getId() + "\"}")));
        assertEquals(409, refused.getStatusCode().value());
        assertEquals(DeliverableStatusReader.CONFIRMED_DETAIL, refused.getReason());
    }

    private String section(String title, DeliverableRequirement... covers) {
        ObjectNode s = json.createObjectNode().put("title", title).put("note", "기록 있음");
        for (DeliverableRequirement r : covers) s.withArray("requirementIds").add(r.getId().toString());
        if (covers.length == 0) s.putArray("requirementIds");
        return s.toString();
    }

    private AgentProposal outline(String... sections) {
        AgentRun run = AgentRun.start(project, member, DraftOutlineSkill.NAME, OffsetDateTime.now());
        run.finish(OffsetDateTime.now());
        em.persist(run);
        String content = """
                {"deliverableId": "%s", "title": "중간 보고서", "sections": [%s], "uncoveredRequirementIds": []}"""
                .formatted(report.getId(), String.join(",", sections));
        AgentProposal card = AgentProposal.propose(run, DocumentOutlineKind.NAME, "중간 보고서 목차", "테스트 근거", json.readTree(content), OffsetDateTime.now());
        em.persist(card);
        return card;
    }

    private JsonNode edit(AgentProposal card, JsonNode content, int expected) throws Exception {
        return call(put(PROPOSAL, project.getId(), card.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(json.createObjectNode().set("content", content).toString()), member, expected);
    }

    /** 요청마다 서버가 DB에서 새로 읽도록 영속성 컨텍스트를 비운다 */
    private JsonNode call(MockHttpServletRequestBuilder request, User as, int expectedStatus) throws Exception {
        em.flush(); em.clear();
        request.header("Authorization", "Bearer " + jwt.generateAccessToken(as.getEmail()));
        var response = mvc.perform(request).andReturn().getResponse();
        response.setCharacterEncoding("UTF-8");
        assertEquals(expectedStatus, response.getStatus(), response.getContentAsString());
        return response.getContentAsString().isEmpty() ? json.createObjectNode() : json.readTree(response.getContentAsString());
    }
}
