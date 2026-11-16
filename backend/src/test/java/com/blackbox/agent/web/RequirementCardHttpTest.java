package com.blackbox.agent.web;

import com.blackbox.agent.AgentProposal;
import com.blackbox.agent.AgentRun;
import com.blackbox.agent.run.AgentRunner;
import com.blackbox.agent.skill.FindRequirementGapsSkill;
import com.blackbox.entity.*;
import com.blackbox.security.JwtService;
import com.blackbox.service.UnresolvedCommentCounter;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * 요구사항 카드의 채택(K-31 5장, 13장)을 실제 DB와 HTTP 요청으로 본다. 진척(K-13)도 HTTP 응답으로 확인한다.
 * 카드는 요구사항 찾기가 만든 모양 그대로 넣는다(근거 확인은 AgentRunnerTest). 테스트마다 트랜잭션 하나로 묶여 끝나면 되돌아간다
 */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class RequirementCardHttpTest {
    static final String PROPOSAL = "/api/projects/{p}/agent/proposals/{id}";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EntityManager em;
    @Autowired JwtService jwt;
    @Autowired AgentRunner runner;
    @MockitoBean UnresolvedCommentCounter unresolved;

    User leader, member;
    Project project;
    Deliverable report;
    DeliverableRequirement met;

    @BeforeEach void setup() {
        leader = user(em, "송팀장"); member = user(em, "손팀원");
        project = project(em, leader);
        member(em, project, leader, "LEADER"); member(em, project, member, "MEMBER");
        report = deliverable(em, project, "중간 보고서");
        met = requirement(em, report, true, leader);
    }

    @Test void acceptedRequirementCardsMoveProgressAsTheContractSays() throws Exception {
        JsonNode before = progress();
        assertEquals(1, before.at("/requiredRequirements/total").asInt());
        assertEquals(1, before.at("/requiredRequirements/met").asInt());

        accept(add("표지에 팀 이름을 적는다", false), 200);
        assertEquals(1, progress().at("/requiredRequirements/total").asInt(), "선택 요구사항은 필수 전체 수에 들지 않는다");

        JsonNode added = accept(add("보고서는 A4 10쪽 이내로 작성한다", true), 200);
        assertEquals(1, added.at("/results/requirementIds").size());
        assertFalse(added.at("/results/assessmentCleared").asBoolean());
        JsonNode afterAdd = progress();
        assertEquals(2, afterAdd.at("/requiredRequirements/total").asInt());
        assertEquals(1, afterAdd.at("/requiredRequirements/met").asInt());

        JsonNode revised = accept(revise(met, "보고서는 PDF로, A4 10쪽 이내로 제출한다", null), 200);
        assertTrue(revised.at("/results/assessmentCleared").asBoolean(), "문구가 달라져 충족 확인이 풀렸다");
        assertEquals(0, revised.at("/results/requirementIds").size(), "수정은 새 기록을 만들지 않는다");
        JsonNode afterRevise = progress();
        assertEquals(2, afterRevise.at("/requiredRequirements/total").asInt());
        assertEquals(0, afterRevise.at("/requiredRequirements/met").asInt());
        assertEquals(before.path("tasks"), afterRevise.path("tasks"), "요구사항 카드는 업무를 바꾸지 않는다");
        JsonNode deliverable = call(get("/api/projects/{p}/deliverables/{d}", project.getId(), report.getId()), member, 200);
        assertTrue(deliverable.path("requirements").findValuesAsString("content").contains("보고서는 PDF로, A4 10쪽 이내로 제출한다"));
    }

    @Test void questionsAndStaleCardsAreNotAccepted() throws Exception {
        AgentProposal question = revise(met, null, "표지도 쪽수에 드는지 확인이 필요합니다");
        assertEquals("QUESTION_ONLY", accept(question, 409).path("code").asString());
        assertEquals("REJECTED", call(post(PROPOSAL + "/reject", project.getId(), question.getId()), member, 200).path("status").asString(),
                "질문만 있는 카드는 거절로 닫는다");

        AgentProposal changed = revise(met, "문제 정의를 한 쪽으로 적는다", null);
        call(put("/api/projects/{p}/deliverables/{d}/requirements/{r}", project.getId(), report.getId(), met.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"다른 사람이 고친 문구\",\"required\":true}"), leader, 200);
        JsonNode refused = accept(changed, 409);
        assertEquals("REQUIREMENT_CHANGED", refused.path("code").asString());
        assertEquals("제안을 만든 뒤 요구사항이 바뀌었거나 지워졌습니다. 요구사항 찾기를 다시 실행해 주세요", refused.path("detail").asString());

        DeliverableRequirement gone = requirement(em, report, false, null);
        AgentProposal orphan = revise(gone, "고친 문구", null);
        em.remove(gone);
        assertEquals("REQUIREMENT_CHANGED", accept(orphan, 409).path("code").asString());
    }

    /**
     * 빠진 요구사항 카드가 대기하는 사이 같은 문구(앞뒤 공백·대소문자 무시)의 요구사항이 생기면 채택은 409 REQUIREMENT_EXISTS다(K-31 5.1).
     * 같은 지적의 다른 카드를 먼저 채택한 경우와 사람이 직접 등록한 경우다
     */
    @Test void aRequirementThatAppearedMeanwhileIsNotAddedTwice() throws Exception {
        AgentProposal first = add("보고서는 PDF로 낸다", true), second = add("보고서는 pdf로 낸다", false);
        accept(first, 200);
        JsonNode refused = accept(second, 409);
        assertEquals("REQUIREMENT_EXISTS", refused.path("code").asString());
        assertEquals("같은 요구사항이 이미 등록되어 있습니다. 이 제안은 거절로 닫아 주세요", refused.path("detail").asString());

        AgentProposal typedMeanwhile = add("발표는 10분이다", true);
        call(post("/api/projects/{p}/deliverables/{d}/requirements", project.getId(), report.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\" 발표는 10분이다 \",\"required\":true}"), leader, 201);
        assertEquals("REQUIREMENT_EXISTS", accept(typedMeanwhile, 409).path("code").asString());
        assertEquals(3, call(get("/api/projects/{p}/deliverables/{d}", project.getId(), report.getId()), member, 200).path("requirements").size(),
                "처음 요구사항, 첫 카드, 직접 등록한 것뿐이다");
    }

    @Test void editingChangesOnlyTheWordsNotTheEvidence() throws Exception {
        AgentProposal revision = revise(met, "보고서는 PDF로 낸다", "표지도 드는지 확인이 필요합니다");
        ObjectNode edited = (ObjectNode) revision.getContent().deepCopy();
        ((ObjectNode) edited.get("revision")).put("content", "보고서는 PDF 한 파일로 낸다").putArray("evidence").add("B1-S9");
        edited.put("currentContent", "다른 문구").put("question", "참고문헌도 드는지 확인이 필요합니다");
        ((ObjectNode) edited.get("evidenceText")).putObject("B1-S9").put("text", "넣은 문장");
        JsonNode kept = edit(revision, edited).path("content");
        assertEquals("보고서는 PDF 한 파일로 낸다", kept.at("/revision/content").asString());
        assertEquals("참고문헌도 드는지 확인이 필요합니다", kept.path("question").asString());
        assertEquals(revision.getContent().at("/revision/evidence"), kept.at("/revision/evidence"));
        assertEquals(met.getContent(), kept.path("currentContent").asString());
        assertEquals(revision.getContent().path("evidenceText"), kept.path("evidenceText"));

        AgentProposal question = revise(met, null, "확인이 필요합니다");
        ObjectNode withRevision = (ObjectNode) question.getContent().deepCopy();
        withRevision.putObject("revision").put("content", "근거 없이 더한 수정안");
        assertTrue(edit(question, withRevision).at("/content/revision").isNull(), "질문만 있는 카드에 수정안을 더할 수 없다");

        AgentProposal addition = add("표지를 붙인다", false);
        ObjectNode addEdited = (ObjectNode) addition.getContent().deepCopy();
        ((ObjectNode) addEdited.get("requirement")).put("content", "표지를 맨 앞에 붙인다").put("required", true).putArray("evidence");
        JsonNode addKept = edit(addition, addEdited).path("content");
        assertEquals("표지를 맨 앞에 붙인다", addKept.at("/requirement/content").asString());
        assertTrue(addKept.at("/requirement/required").asBoolean());
        assertEquals(addition.getContent().at("/requirement/evidence"), addKept.at("/requirement/evidence"));
    }

    @Test void aConfirmedDeliverableIsNeitherSearchedNorChanged() throws Exception {
        String brief = call(post("/api/projects/{p}/briefs", project.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"과제\",\"body\":\"보고서를 낸다\"}"), member, 201).path("id").asString();
        call(post("/api/projects/{p}/deliverables/{d}/briefs/{b}", project.getId(), report.getId(), brief), member, 200);
        AgentProposal addition = add("보고서를 낸다", true);
        ReviewRound approved = round(em, report, 1, file(em, project, member, "보고서.pdf", 1, 'a'), member, "APPROVED");
        call(put("/api/projects/{p}/deliverables/{d}/confirmation", project.getId(), report.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reviewId\":\"" + approved.getId() + "\"}"), leader, 200);

        ResponseStatusException search = assertThrows(ResponseStatusException.class, () -> runner.prepare(project.getId(), member,
                FindRequirementGapsSkill.NAME, json.readTree("{\"deliverableId\":\"" + report.getId() + "\"}")));
        assertEquals(409, search.getStatusCode().value());
        accept(addition, 409);
    }

    private AgentProposal add(String content, boolean required) {
        return card("REQUIREMENT_ADD", """
                {"kind": "REQUIREMENT_ADD", "deliverableId": "%s", "requirement": {"content": "%s", "required": %s, "evidence": ["B1-S1"]},
                 "reason": {"text": "안내문에 있다", "evidence": ["B1-S1"]}, "evidenceText": {}}""".formatted(report.getId(), content, required));
    }

    /** revision이 null이면 질문만 있는 카드다 */
    private AgentProposal revise(DeliverableRequirement target, String revision, String question) {
        return card("REQUIREMENT_REVISE", """
                {"kind": "REQUIREMENT_REVISE", "deliverableId": "%s", "requirementId": "%s", "currentContent": "%s",
                 "issue": {"text": "모호하다", "evidence": ["R1"]}, "revision": %s, "question": %s, "evidenceText": {},
                 "evidenceRequirements": {"R1": {"requirementId": "%2$s", "content": "%3$s"}}}""".formatted(report.getId(), target.getId(),
                target.getContent(), revision == null ? "null" : "{\"content\": \"" + revision + "\", \"evidence\": [\"B1-S1\"]}",
                question == null ? "null" : "\"" + question + "\""));
    }

    private AgentProposal card(String kind, String content) {
        AgentRun run = AgentRun.start(project, member, FindRequirementGapsSkill.NAME, OffsetDateTime.now());
        run.finish(OffsetDateTime.now());
        em.persist(run);
        AgentProposal card = AgentProposal.propose(run, kind, kind, "테스트 근거", json.readTree(content), OffsetDateTime.now());
        em.persist(card);
        return card;
    }

    private JsonNode accept(AgentProposal card, int expected) throws Exception {
        return call(post(PROPOSAL + "/accept", project.getId(), card.getId()), member, expected);
    }

    private JsonNode edit(AgentProposal card, JsonNode content) throws Exception {
        return call(put(PROPOSAL, project.getId(), card.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(json.createObjectNode().set("content", content).toString()), member, 200);
    }

    private JsonNode progress() throws Exception {
        return call(get("/api/projects/{p}/deliverables/{d}/progress", project.getId(), report.getId()), member, 200);
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
