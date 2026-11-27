package com.blackbox.agent.web;

import com.blackbox.agent.AgentProposal;
import com.blackbox.agent.AgentRun;
import com.blackbox.agent.proposal.DocumentDraftKind;
import com.blackbox.agent.proposal.DocumentOutlineKind;
import com.blackbox.agent.skill.DraftDocumentSkill;
import com.blackbox.entity.*;
import com.blackbox.scheduler.ScoreScheduler;
import com.blackbox.security.JwtService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipInputStream;

import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * 문서 초안 카드(K-59 3장)의 수정과 채택, 초안 실행의 시작 조건(2장). 파일은 임시 폴더에 쓰고,
 * 테스트마다 트랜잭션 하나로 묶여 끝나면 되돌아간다(디스크의 파일도 파일 금고가 지운다)
 */
@SpringBootTest(properties = "app.file.upload-dir=${java.io.tmpdir}/bb-draft-test")
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class DocumentDraftCardHttpTest {
    static final String PROPOSAL = "/api/projects/{p}/agent/proposals/{id}";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EntityManager em;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    // 서버가 뜨자마자 도는 점수 재계산이 다른 테스트가 남긴 프로젝트의 점수를 써 넣지 않게 뗀다. FileVaultRollbackTest와 같은 설정이라 서버를 함께 쓴다
    @MockitoBean ScoreScheduler scoreScheduler;

    User leader, member;
    Project project;
    Deliverable report;
    DeliverableRequirement problem;
    Task task;
    Meeting meeting;
    AgentProposal outline;

    @BeforeEach void setup() {
        leader = user(em, "송팀장"); member = user(em, "손팀원");
        project = project(em, leader);
        member(em, project, leader, "LEADER"); member(em, project, member, "MEMBER");
        report = deliverable(em, project, "중간 보고서");
        problem = requirement(em, report, true, null);
        task = new Task(); task.setProject(project); task.setCreatedBy(leader); task.setTitle("문제 정의 초안");
        task.setStatus("TODO"); task.setPriority("MEDIUM"); task.setDeliverable(report); em.persist(task);
        meeting = new Meeting(); meeting.setProject(project); meeting.setCreatedBy(leader); meeting.setTitle("설계 회의");
        meeting.setMeetingDate(OffsetDateTime.parse("2026-11-20T01:00:00Z")); em.persist(meeting);
        outline = card(DocumentOutlineKind.NAME, """
                {"deliverableId": "%s", "title": "중간 보고서", "sections": [{"title": "문제 정의", "requirementIds": ["%s"], "note": null}],
                 "uncoveredRequirementIds": []}""".formatted(report.getId(), problem.getId()));
    }

    @Test void editingKeepsTheFixedIdsFiltersSourcesAndRecountsMissing() throws Exception {
        AgentProposal draft = draft("문제는 기록이 흩어진다는 것이다.\n\n[작성 필요: 설문 결과] [작성 필요: 비교 표]");
        ObjectNode edited = (ObjectNode) draft.getContent().deepCopy();
        edited.put("deliverableId", UUID.randomUUID().toString()).put("outlineProposalId", UUID.randomUUID().toString());
        ObjectNode sources = (ObjectNode) edited.at("/sections/0/sources");
        sources.withArray("taskIds").add(UUID.randomUUID().toString()).add("abc");
        sources.withArray("meetingIds").add(meeting.getId().toString()).add(meeting.getId().toString());

        JsonNode kept = edit(draft, edited).path("content");
        assertEquals(report.getId().toString(), kept.path("deliverableId").asString());
        assertEquals(outline.getId().toString(), kept.path("outlineProposalId").asString());
        assertEquals(List.of(task.getId().toString()), strings(kept.at("/sections/0/sources/taskIds")), "모르는 id와 형식이 틀린 id는 빠진다");
        assertEquals(List.of(meeting.getId().toString()), strings(kept.at("/sections/0/sources/meetingIds")), "같은 id는 한 번만");
        assertEquals(2, kept.path("missingCount").asInt());

        ((ObjectNode) edited.withArray("sections").get(0)).put("body", "설문 결과는 60%였다. [작성 필요: 비교 표]");
        assertEquals(1, edit(draft, edited).at("/content/missingCount").asInt());
    }

    /** 채택하면 .docx를 파일 금고에 올리고 그 파일로 검토 회차를 연다. 채택한 사람이 올린 사람이다(K-59 3장) */
    @Test void acceptingSavesADocxAndOpensAReviewRound() throws Exception {
        JsonNode accepted = call(post(PROPOSAL + "/accept", project.getId(), draft("문제는 [작성 필요: 설문 결과]이다.").getId()), member, 200);
        UUID fileId = UUID.fromString(accepted.at("/results/fileId").asString());
        UUID roundId = UUID.fromString(accepted.at("/results/reviewRoundId").asString());

        var file = jdbc.queryForMap("SELECT file_name, uploader_id, version, storage_path FROM file_vault WHERE id = ?", fileId);
        assertEquals("중간 보고서 초안.docx", file.get("file_name"));
        assertEquals(member.getId(), file.get("uploader_id"));
        assertEquals(1, file.get("version"));
        assertEquals(fileId, jdbc.queryForObject("SELECT file_id FROM review_rounds WHERE id = ? AND deliverable_id = ?", UUID.class, roundId, report.getId()));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM activity_logs WHERE project_id = ? AND action_type = 'FILE_CREATE'", Integer.class, project.getId()),
                "화면에서 올릴 때처럼 파일 참여로 기록된다");

        String xml = documentXml(Files.readAllBytes(Path.of((String) file.get("storage_path"))));
        assertTrue(xml.contains("문제 정의") && xml.contains("[작성 필요: 설문 결과]"), xml);
        assertTrue(xml.contains("근거: 업무 「문제 정의 초안」, 회의 「설계 회의」(2026-11-20)"), "회의 날짜는 한국 시간");
        assertFalse(xml.contains(problem.getId().toString()), "요구사항은 근거 줄에 넣지 않는다");

        // 채택한 사람이 올린 사람이라 그 회차는 다른 팀원이 결정한다(K-10, K-59 8장 4)
        String decision = "{\"decision\":\"APPROVED\"}";
        call(put("/api/projects/{p}/deliverables/{d}/reviews/{r}/decision", project.getId(), report.getId(), roundId)
                .contentType(MediaType.APPLICATION_JSON).content(decision), member, 403);
        call(put("/api/projects/{p}/deliverables/{d}/reviews/{r}/decision", project.getId(), report.getId(), roundId)
                .contentType(MediaType.APPLICATION_JSON).content(decision), leader, 200);
    }

    /** 같은 제출물의 초안을 다시 채택하면 같은 이름의 다음 버전이 되고, 화면에서 다시 올릴 때처럼 FILE_EDIT가 남는다 */
    @Test void aSecondDraftBecomesTheNextVersionOfTheSameFile() throws Exception {
        UUID first = UUID.fromString(call(post(PROPOSAL + "/accept", project.getId(), draft("첫 초안").getId()), member, 200).at("/results/fileId").asString());
        UUID second = UUID.fromString(call(post(PROPOSAL + "/accept", project.getId(), draft("고친 초안").getId()), leader, 200).at("/results/fileId").asString());
        assertEquals(List.of("중간 보고서 초안.docx:1", "중간 보고서 초안.docx:2"), jdbc.queryForList(
                "SELECT file_name || ':' || version FROM file_vault WHERE id IN (?, ?) ORDER BY version", String.class, first, second));
        assertEquals(List.of("FILE_CREATE", "FILE_EDIT"), jdbc.queryForList(
                "SELECT action_type FROM activity_logs WHERE project_id = ? AND action_type LIKE 'FILE_%' ORDER BY (metadata->>'version')::int", String.class, project.getId()));
    }

    @Test void aConfirmedDeliverableRefusesBeforeAnyFileIsSaved() throws Exception {
        AgentProposal draft = draft("본문");
        problem.assess(leader);
        ReviewRound approved = round(em, report, 1, file(em, project, member, "보고서.pdf", 1, 'a'), member, "APPROVED");
        call(put("/api/projects/{p}/deliverables/{d}/confirmation", project.getId(), report.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reviewId\":\"" + approved.getId() + "\"}"), leader, 200);
        int files = jdbc.queryForObject("SELECT count(*) FROM file_vault WHERE project_id = ?", Integer.class, project.getId());

        call(post(PROPOSAL + "/accept", project.getId(), draft.getId()), member, 409);
        assertEquals(files, jdbc.queryForObject("SELECT count(*) FROM file_vault WHERE project_id = ?", Integer.class, project.getId()));
    }

    /** 초안 실행은 같은 프로젝트의 채택된 목차로만 시작한다(K-59 2장). 모델을 부르기 전에 HTTP 오류로 끝난다 */
    @Test void aDraftNeedsAnAcceptedOutlineOfThisProject() throws Exception {
        JsonNode pending = run(outline.getId().toString(), 409);
        assertEquals("OUTLINE_NOT_ACCEPTED", pending.path("code").asString());
        assertEquals(DraftDocumentSkill.OUTLINE_NOT_ACCEPTED, pending.path("detail").asString());

        call(post(PROPOSAL + "/reject", project.getId(), outline.getId()), member, 200);
        assertEquals("OUTLINE_NOT_ACCEPTED", run(outline.getId().toString(), 409).path("code").asString(), "거절된 목차도 쓸 수 없다");

        run(UUID.randomUUID().toString(), 404);
        run(draft("본문").getId().toString(), 404);   // 목차가 아닌 카드
        run("abc", 400);
    }

    private JsonNode run(String outlineId, int expected) throws Exception {
        return call(post("/api/projects/{p}/agent/runs", project.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"skill\":\"" + DraftDocumentSkill.NAME + "\",\"input\":{\"outlineProposalId\":\"" + outlineId + "\"}}"), member, expected);
    }

    private AgentProposal draft(String body) {
        ObjectNode section = json.createObjectNode().put("title", "문제 정의").put("body", body);
        ObjectNode sources = section.putObject("sources");
        sources.putArray("taskIds").add(task.getId().toString());
        sources.putArray("meetingIds").add(meeting.getId().toString());
        sources.putArray("requirementIds").add(problem.getId().toString());
        ObjectNode content = json.createObjectNode().put("deliverableId", report.getId().toString())
                .put("outlineProposalId", outline.getId().toString()).put("title", "중간 보고서").put("missingCount", 0);
        content.putArray("sections").add(section);
        return card(DocumentDraftKind.NAME, content.toString());
    }

    private AgentProposal card(String kind, String content) {
        AgentRun run = AgentRun.start(project, member, DraftDocumentSkill.NAME, OffsetDateTime.now());
        run.finish(OffsetDateTime.now());
        em.persist(run);
        AgentProposal card = AgentProposal.propose(run, kind, kind, "테스트 근거", json.readTree(content), OffsetDateTime.now());
        em.persist(card);
        return card;
    }

    private static String documentXml(byte[] docx) throws Exception {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            for (var e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                if (e.getName().equals("word/document.xml")) return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return fail("document.xml이 없다");
    }

    private static List<String> strings(JsonNode array) { return array.valueStream().map(JsonNode::asString).toList(); }

    private JsonNode edit(AgentProposal card, JsonNode content) throws Exception {
        return call(put(PROPOSAL, project.getId(), card.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(json.createObjectNode().set("content", content).toString()), member, 200);
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
