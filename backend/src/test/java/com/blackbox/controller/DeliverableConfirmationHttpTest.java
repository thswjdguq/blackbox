package com.blackbox.controller;

import com.blackbox.entity.*;
import com.blackbox.repository.DeliverableConfirmationRepository;
import com.blackbox.repository.DeliverableSubmissionRepository;
import com.blackbox.security.JwtService;
import com.blackbox.service.UnresolvedCommentCounter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * K-20 11장의 수용 조건을 실제 DB와 HTTP 요청으로 확인한다.
 * 테스트마다 트랜잭션 하나로 묶여 끝나면 되돌아간다. 미해결 피드백 수는 피드백 구현(B-20) 전이라 가짜 집계를 쓴다.
 */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class DeliverableConfirmationHttpTest {
    static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-17T21:10:00+09:00");
    static final String FILE = "중간보고서.pdf";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EntityManager em;
    @Autowired JwtService jwt;
    @Autowired DeliverableConfirmationRepository confirmations;
    @Autowired DeliverableSubmissionRepository submissions;
    @MockBean UnresolvedCommentCounter unresolved;

    User leader, member, observer, outsider;
    Project project;
    Deliverable report;

    @BeforeEach void setup() {
        leader = user(em, "송팀장"); member = user(em, "손팀원"); observer = user(em, "관찰자"); outsider = user(em, "외부인");
        project = project(em, leader);
        member(em, project, leader, "LEADER"); member(em, project, member, "MEMBER"); member(em, project, observer, "OBSERVER");
        report = deliverable(em, project, "중간 보고서");
    }

    @Test void deliverableWithoutRoundsIsNotReady() throws Exception {
        JsonNode body = read(member);
        assertEquals("DRAFT", body.get("status").asText());
        assertFalse(body.get("canConfirm").asBoolean());
        assertEquals(List.of("REQUIRED_REQUIREMENTS", "APPROVED_FILE", "NO_UNRESOLVED_COMMENTS"), body.get("checks").findValuesAsText("code"));
        assertEquals(List.of("true", "false", "true"), body.get("checks").findValuesAsText("passed"));
        assertEquals(List.of("필수 요구사항이 없습니다", "검토 회차가 없습니다. 파일을 골라 검토를 요청해주세요", "미해결 피드백이 없습니다"),
                body.get("checks").findValuesAsText("detail"));
        assertFalse(body.get("soleContributor").asBoolean());
        // 값이 없어도 필드는 생략하지 않는다
        for (String field : List.of("candidate", "confirmation", "submission")) assertTrue(body.get(field).isNull(), field);
    }

    @Test void readyWhenAllThreeChecksPass() throws Exception {
        requirement(em, report, true, member); requirement(em, report, true, leader); requirement(em, report, false, null);
        round(em, report, 1, upload(1, 'a'), member, "CHANGES_REQUESTED");
        FileVault revised = upload(2, 'b');
        ReviewRound approved = round(em, report, 2, revised, member, "APPROVED");

        JsonNode body = read(observer);
        assertEquals("IN_REVIEW", body.get("status").asText());
        assertTrue(body.get("canConfirm").asBoolean());
        assertEquals(List.of("필수 요구사항 2개를 모두 확인했습니다", "2회차에서 중간보고서.pdf 2버전이 승인됐습니다", "미해결 피드백이 없습니다"),
                body.get("checks").findValuesAsText("detail"));
        JsonNode candidate = body.get("candidate");
        assertEquals(approved.getId().toString(), candidate.get("reviewId").asText());
        assertEquals(2, candidate.get("roundNo").asInt());
        assertEquals(json.readTree("{\"fileId\":\"" + revised.getId() + "\",\"fileName\":\"" + FILE + "\",\"version\":2,\"shortHash\":\"bbbbbbb\","
                + "\"uploaderId\":\"" + member.getId() + "\",\"uploaderName\":\"손팀원\"}"), candidate.get("file"));
    }

    @Test void unmetRequirementOrUnresolvedCommentBlocksButKeepsTheCandidate() throws Exception {
        requirement(em, report, true, member); requirement(em, report, true, member);
        DeliverableRequirement last = requirement(em, report, true, null);
        round(em, report, 1, upload(1, 'a'), member, "APPROVED");

        JsonNode body = read(leader);
        assertFalse(body.get("canConfirm").asBoolean());
        assertEquals("필수 요구사항 3개 중 1개의 충족을 확인해주세요", detail(body, "REQUIRED_REQUIREMENTS"));
        assertFalse(body.get("candidate").isNull(), "확정 대상은 승인된 파일 검사만 통과하면 보인다");

        em.find(DeliverableRequirement.class, last.getId()).assess(leader);
        assertTrue(read(leader).get("canConfirm").asBoolean());

        when(unresolved.countUnresolvedComments(project.getId(), report.getId())).thenReturn(2L);
        body = read(leader);
        assertFalse(body.get("canConfirm").asBoolean());
        assertEquals("미해결 피드백 2건을 해결해야 확정할 수 있습니다", detail(body, "NO_UNRESOLVED_COMMENTS"));
        assertFalse(body.get("candidate").isNull());
    }

    /** 승인된 파일 검사는 최신 회차만 보고, 통과하지 못한 사유를 K-10 7장과 같은 말로 적는다. */
    @Test void approvedFileCheckFollowsTheLatestRoundAndTheNewestContent() throws Exception {
        FileVault first = upload(1, 'a');
        round(em, report, 1, first, member, null);
        assertNotApproved("1회차가 결정 전입니다. 승인을 받아주세요");
        round(em, report, 2, first, member, "CHANGES_REQUESTED");
        assertNotApproved("2회차에서 수정 요청을 받았습니다. 보완한 뒤 새 회차를 열어주세요");
        round(em, report, 3, null, member, "APPROVED");
        assertNotApproved("3회차는 파일 없는 회차입니다. 파일을 골라 새 회차를 열어주세요");

        round(em, report, 4, first, member, "APPROVED");
        assertEquals("4회차에서 중간보고서.pdf 1버전이 승인됐습니다", detail(read(member), "APPROVED_FILE"));
        upload(2, 'a');   // 같은 내용을 다시 올려 버전만 올라간 경우
        JsonNode body = read(member);
        assertTrue(body.get("canConfirm").asBoolean());
        assertEquals(1, body.at("/candidate/file/version").asInt(), "확정 대상은 회차가 가리키는 버전이다");

        upload(3, 'c');
        assertNotApproved("4회차에서 승인된 파일이 같은 이름의 최신 버전과 내용이 다릅니다. 최신 버전으로 새 회차를 열어주세요");
        round(em, report, 5, null, member, null);
        assertNotApproved("5회차가 결정 전입니다. 승인을 받아주세요");
    }

    @Test void soleContributorCountsLeaderAndMembersButNotObservers() throws Exception {
        Project small = project(em, leader);
        member(em, small, leader, "LEADER"); member(em, small, observer, "OBSERVER");
        Deliverable alone = deliverable(em, small, "혼자 만든 제출물");
        assertTrue(read(leader, small.getId(), alone.getId(), 200).get("soleContributor").asBoolean());
        member(em, small, member, "MEMBER");
        assertFalse(read(leader, small.getId(), alone.getId(), 200).get("soleContributor").asBoolean());
    }

    @Test void membersAndObserversReadButOthersAreRefused() throws Exception {
        Project other = project(em, leader);
        member(em, other, leader, "LEADER");
        Deliverable foreign = deliverable(em, other, "다른 프로젝트의 제출물");

        for (User reader : List.of(leader, member, observer)) read(reader);
        read(outsider, project.getId(), report.getId(), 403);
        read(null, project.getId(), report.getId(), 401);
        JsonNode notFound = read(leader, project.getId(), foreign.getId(), 404);
        assertFalse(notFound.toString().contains("다른 프로젝트의 제출물"), "제목을 드러내지 않는다");
        read(leader, project.getId(), UUID.randomUUID(), 404);
        read(leader, UUID.randomUUID(), report.getId(), 404);
        read(leader, project.getId(), "not-a-uuid", 400);
    }

    @Test void confirmedAndSubmittedDeliverablesShowTheRecordsInsteadOfChecks() throws Exception {
        FileVault approvedFile = upload(2, 'b');
        ReviewRound approved = round(em, report, 1, approvedFile, member, "APPROVED");
        confirmations.save(DeliverableConfirmation.of(approved, leader, NOW));

        JsonNode body = read(member);
        assertEquals("CONFIRMED", body.get("status").asText());
        assertFalse(body.get("canConfirm").asBoolean());
        assertTrue(body.get("checks").isArray() && body.get("checks").isEmpty());
        assertTrue(body.get("candidate").isNull());
        assertTrue(body.get("submission").isNull());
        JsonNode confirmation = body.get("confirmation");
        assertEquals(approved.getId().toString(), confirmation.get("reviewId").asText());
        assertEquals(1, confirmation.get("roundNo").asInt());
        assertEquals(approvedFile.getId().toString(), confirmation.at("/file/fileId").asText());
        assertEquals(json.readTree("{\"userId\":\"" + leader.getId() + "\",\"name\":\"송팀장\"}"), confirmation.get("confirmedBy"));
        assertEquals(NOW.toInstant(), OffsetDateTime.parse(confirmation.get("confirmedAt").asText()).toInstant());
        assertTrue(confirmation.get("fileStillLatest").asBoolean());

        upload(3, 'c');   // 확정 뒤에 내용이 다른 새 버전이 올라와도 확정본은 그대로다
        confirmation = read(member).get("confirmation");
        assertFalse(confirmation.get("fileStillLatest").asBoolean());
        assertEquals(2, confirmation.at("/file/version").asInt());

        submissions.save(DeliverableSubmission.of(confirmations.getReferenceById(report.getId()),
                "학교 LMS 과제함", NOW.plusHours(12), "접수 번호 2026-1234", member, NOW.plusHours(13)));
        body = read(observer);
        assertEquals("SUBMITTED", body.get("status").asText());
        JsonNode submission = body.get("submission");
        assertEquals("학교 LMS 과제함", submission.get("channel").asText());
        assertEquals("접수 번호 2026-1234", submission.get("note").asText());
        assertEquals(NOW.plusHours(12).toInstant(), OffsetDateTime.parse(submission.get("submittedAt").asText()).toInstant());
        assertEquals(NOW.plusHours(13).toInstant(), OffsetDateTime.parse(submission.get("recordedAt").asText()).toInstant());
        assertEquals(json.readTree("{\"userId\":\"" + member.getId() + "\",\"name\":\"손팀원\"}"), submission.get("recordedBy"));
        assertFalse(body.get("confirmation").isNull());
    }

    private void assertNotApproved(String detail) throws Exception {
        JsonNode body = read(member);
        assertEquals(detail, detail(body, "APPROVED_FILE"));
        assertFalse(body.get("canConfirm").asBoolean());
        assertTrue(body.get("candidate").isNull(), "승인된 파일 검사를 통과하지 못하면 확정 대상이 없다");
    }

    private static String detail(JsonNode body, String code) {
        for (JsonNode check : body.get("checks")) if (code.equals(check.get("code").asText())) return check.get("detail").asText();
        return fail(code + " 검사가 응답에 없다");
    }

    private FileVault upload(int version, char content) { return file(em, project, member, FILE, version, content); }

    private JsonNode read(User as) throws Exception { return read(as, project.getId(), report.getId(), 200); }

    /** 요청마다 서버가 DB에서 새로 읽도록 영속성 컨텍스트를 비운다. as가 null이면 로그인하지 않은 요청이다. */
    private JsonNode read(User as, Object projectId, Object deliverableId, int expectedStatus) throws Exception {
        em.flush(); em.clear();
        var request = get("/api/projects/{projectId}/deliverables/{deliverableId}/confirmation", projectId, deliverableId);
        if (as != null) request.header("Authorization", "Bearer " + jwt.generateAccessToken(as.getEmail()));
        var response = mvc.perform(request).andReturn().getResponse();
        response.setCharacterEncoding("UTF-8");
        assertEquals(expectedStatus, response.getStatus(), response.getContentAsString());
        return response.getContentAsString().isEmpty() ? json.createObjectNode() : json.readTree(response.getContentAsString());
    }
}
