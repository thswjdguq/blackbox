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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * K-20 11장의 수용 조건을 실제 DB와 HTTP 요청으로 확인한다.
 * 테스트마다 트랜잭션 하나로 묶여 끝나면 되돌아간다. 미해결 피드백 수는 피드백 구현(B-20) 전이라 가짜 집계를 쓴다.
 */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class DeliverableConfirmationHttpTest {
    static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-17T21:10:00+09:00");
    static final String FILE = "중간보고서.pdf";
    // 제출 기록의 낸 시각. 서버 시각보다 앞이어야 받는다
    static final OffsetDateTime PAST = OffsetDateTime.parse("2026-10-01T09:30:00+09:00");
    static final String PATH = "/api/projects/{projectId}/deliverables/{deliverableId}/confirmation";
    static final String SUBMISSION = "/api/projects/{projectId}/deliverables/{deliverableId}/submission";

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

    @Test void leaderConfirmsTheCandidateAndRepeatingKeepsTheFirstRecord() throws Exception {
        requirement(em, report, true, member);
        FileVault file = upload(1, 'a');
        ReviewRound approved = round(em, report, 1, file, member, "APPROVED");
        String candidate = read(leader).at("/candidate/reviewId").asText();
        OffsetDateTime before = OffsetDateTime.now();

        JsonNode body = confirm(leader, candidate, 200);
        assertEquals("CONFIRMED", body.get("status").asText());
        assertFalse(body.get("canConfirm").asBoolean());
        assertTrue(body.get("checks").isEmpty() && body.get("candidate").isNull() && body.get("submission").isNull());
        JsonNode confirmation = body.get("confirmation");
        assertEquals(approved.getId().toString(), confirmation.get("reviewId").asText());
        assertEquals(file.getId().toString(), confirmation.at("/file/fileId").asText());
        assertEquals("송팀장", confirmation.at("/confirmedBy/name").asText());
        assertTrue(confirmation.get("fileStillLatest").asBoolean());
        OffsetDateTime confirmedAt = OffsetDateTime.parse(confirmation.get("confirmedAt").asText());
        assertTrue(Duration.between(before, confirmedAt).abs().toSeconds() < 5, "확정 시각은 서버의 지금이다");

        // 같은 회차로 다시 보낸 것은 중복 클릭이다. 처음의 응답과 글자까지 같은 객체를 돌려준다
        assertEquals(body, confirm(leader, candidate, 200));
        assertEquals(body, read(observer));
    }

    @Test void onlyTheLeaderConfirms() throws Exception {
        requirement(em, report, true, member);
        ReviewRound approved = round(em, report, 1, upload(1, 'a'), member, "APPROVED");

        assertEquals("최종본 확정은 팀장만 할 수 있습니다", confirm(member, approved.getId(), 403).get("detail").asText());
        confirm(observer, approved.getId(), 403);
        confirm(outsider, approved.getId(), 403);
        confirm(null, approved.getId(), 401);
        assertNotConfirmed();
    }

    /** 검사를 하나라도 통과하지 못하면 409이고, 세 검사의 순서에서 처음 막힌 사유를 준다. */
    @Test void confirmationIsRefusedWithTheFirstBlockingReason() throws Exception {
        DeliverableRequirement unmet = requirement(em, report, true, null);
        FileVault first = upload(1, 'a');
        ReviewRound undecided = round(em, report, 1, first, member, null);
        assertRefused(undecided, "필수 요구사항 1개 중 1개의 충족을 확인해주세요");
        em.find(DeliverableRequirement.class, unmet.getId()).assess(leader);
        assertRefused(undecided, "1회차가 결정 전입니다. 승인을 받아주세요");
        assertRefused(round(em, report, 2, first, member, "CHANGES_REQUESTED"), "2회차에서 수정 요청을 받았습니다. 보완한 뒤 새 회차를 열어주세요");
        assertRefused(round(em, report, 3, null, member, "APPROVED"), "3회차는 파일 없는 회차입니다. 파일을 골라 새 회차를 열어주세요");

        ReviewRound approved = round(em, report, 4, first, member, "APPROVED");
        when(unresolved.countUnresolvedComments(project.getId(), report.getId())).thenReturn(2L);
        assertRefused(approved, "미해결 피드백 2건을 해결해야 확정할 수 있습니다");
        when(unresolved.countUnresolvedComments(project.getId(), report.getId())).thenReturn(0L);
        upload(2, 'c');   // 승인 뒤에 내용이 다른 새 버전이 올라왔다
        assertRefused(approved, "4회차에서 승인된 파일이 같은 이름의 최신 버전과 내용이 다릅니다. 최신 버전으로 새 회차를 열어주세요");
        // 화면을 본 뒤 새 회차가 열렸으면 대상 대조까지 가지 않고 승인된 파일 검사의 사유가 나온다
        round(em, report, 5, null, member, null);
        assertRefused(approved, "5회차가 결정 전입니다. 승인을 받아주세요");
    }

    @Test void targetMustBeTheCandidateTheLeaderSaw() throws Exception {
        Project other = project(em, leader);
        member(em, other, leader, "LEADER");
        Deliverable foreign = deliverable(em, other, "다른 프로젝트의 제출물");
        ReviewRound foreignRound = round(em, foreign, 1, file(em, other, member, FILE, 1, 'a'), member, "APPROVED");
        FileVault file = upload(1, 'a');
        ReviewRound earlier = round(em, report, 1, file, member, "CHANGES_REQUESTED");
        ReviewRound approved = round(em, report, 2, file, member, "APPROVED");

        assertRefused(earlier, "확정하려는 대상이 바뀌었습니다. 새로고침해 다시 확인해주세요");
        // 이 제출물의 회차가 아니면 다른 프로젝트의 것이든 없는 것이든 404다
        assertEquals("검토 회차를 찾을 수 없습니다", confirm(leader, foreignRound.getId(), 404).get("detail").asText());
        confirm(leader, UUID.randomUUID(), 404);
        call(put(PATH, project.getId(), foreign.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reviewId\":\"" + foreignRound.getId() + "\"}"), leader, 404);
        for (String invalid : List.of("{}", "{\"reviewId\":null}", "{\"reviewId\":\"not-a-uuid\"}"))
            call(put(PATH, project.getId(), report.getId()).contentType(MediaType.APPLICATION_JSON).content(invalid), leader, 400);
        assertNotConfirmed();

        confirm(leader, approved.getId(), 200);
        assertEquals("이미 확정된 제출물입니다", confirm(leader, earlier.getId(), 409).get("detail").asText());
        confirm(leader, UUID.randomUUID(), 404);
        assertEquals(approved.getId().toString(), read(leader).at("/confirmation/reviewId").asText());
    }

    @Test void submissionIsRefusedBeforeConfirmation() throws Exception {
        assertEquals("최종본을 확정한 뒤에 제출을 기록할 수 있습니다", submit(member, submission(null, PAST, null), 409).get("detail").asText());
        assertNotSubmitted();
    }

    @Test void memberRecordsTheSubmission() throws Exception {
        em.find(Deliverable.class, report.getId()).setSubmissionMethod("학교 LMS 과제함");
        confirmReport();
        OffsetDateTime before = OffsetDateTime.now();

        JsonNode body = submit(member, submission(null, PAST, "접수 번호 2026-1234"), 200);
        assertEquals("SUBMITTED", body.get("status").asText());
        assertFalse(body.get("confirmation").isNull());
        JsonNode submission = body.get("submission");
        assertEquals("학교 LMS 과제함", submission.get("channel").asText(), "낸 곳을 비우면 제출물의 제출 경로가 들어간다");
        assertEquals("접수 번호 2026-1234", submission.get("note").asText());
        assertEquals(PAST.toInstant(), OffsetDateTime.parse(submission.get("submittedAt").asText()).toInstant());
        assertEquals("손팀원", submission.at("/recordedBy/name").asText());
        assertTrue(Duration.between(before, OffsetDateTime.parse(submission.get("recordedAt").asText())).abs().toSeconds() < 5);
        assertEquals(body, read(observer));
    }

    /** 기록은 한 번이다. 같은 내용은 누가 다시 보내도 처음의 기록 그대로이고, 다른 내용은 받지 않는다. */
    @Test void repeatedSubmissionKeepsTheFirstRecord() throws Exception {
        confirmReport();
        JsonNode body = submit(member, submission("학교 LMS 과제함", PAST, "접수 번호 2026-1234"), 200);

        assertEquals(body, submit(leader, submission("학교 LMS 과제함", PAST, "접수 번호 2026-1234"), 200));
        // 표기만 다른 같은 시각과 앞뒤 공백도 같은 내용이다
        assertEquals(body, submit(leader, submission(" 학교 LMS 과제함 ", PAST.withOffsetSameInstant(ZoneOffset.UTC), "접수 번호 2026-1234"), 200));
        for (String different : List.of(submission("이메일", PAST, "접수 번호 2026-1234"),
                submission("학교 LMS 과제함", PAST.plusMinutes(1), "접수 번호 2026-1234"), submission("학교 LMS 과제함", PAST, null)))
            assertEquals("이미 제출이 기록됐습니다", submit(leader, different, 409).get("detail").asText());
        assertEquals(body, read(observer));
    }

    @Test void onlyLeaderAndMembersRecordTheSubmission() throws Exception {
        confirmReport();
        submit(observer, submission(null, PAST, null), 403);
        submit(outsider, submission(null, PAST, null), 403);
        submit(null, submission(null, PAST, null), 401);
        assertNotSubmitted();
        submit(leader, submission(null, PAST, null), 200);
    }

    @Test void invalidSubmissionIsRefusedAndLeavesNothing() throws Exception {
        confirmReport();
        OffsetDateTime now = OffsetDateTime.now();
        assertEquals("제출 시각은 지금보다 뒤일 수 없습니다", submit(member, submission(null, now.plusMinutes(10), null), 400).get("detail").asText());
        for (String invalid : List.of("{}", "{\"submittedAt\":null}", "{\"submittedAt\":\"어제\"}",
                submission("가".repeat(501), PAST, null), submission(null, PAST, "가".repeat(1001))))
            submit(member, invalid, 400);
        call(put(SUBMISSION, project.getId(), UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content(submission(null, PAST, null)), member, 404);
        assertNotSubmitted();
    }

    @Test void blankChannelAndNoteAreStoredAsNull() throws Exception {
        confirmReport();
        JsonNode submission = submit(member, submission("  ", PAST, ""), 200).get("submission");
        assertTrue(submission.get("channel").isNull(), "낸 곳도 제출물의 제출 경로도 비어 있으면 없는 값이다");
        assertTrue(submission.get("note").isNull());
    }

    /** 기록하는 사람의 시계가 조금 빠른 것과, 확정보다 먼저 낸 것은 받는다. */
    @Test void submissionTimeMayBeSlightlyAheadOrBeforeTheConfirmation() throws Exception {
        confirmReport();
        JsonNode body = submit(member, submission(null, OffsetDateTime.now().plusMinutes(4), null), 200);
        assertEquals("SUBMITTED", body.get("status").asText());

        Deliverable poster = deliverable(em, project, "포스터");
        ReviewRound approved = round(em, poster, 1, file(em, project, member, "포스터.pdf", 1, 'p'), member, "APPROVED");
        call(put(PATH, project.getId(), poster.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reviewId\":\"" + approved.getId() + "\"}"), leader, 200);
        body = call(put(SUBMISSION, project.getId(), poster.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(submission(null, PAST, null)), member, 200);
        assertTrue(OffsetDateTime.parse(body.at("/submission/submittedAt").asText())
                .isBefore(OffsetDateTime.parse(body.at("/confirmation/confirmedAt").asText())));
    }

    private void assertNotSubmitted() {
        em.flush(); em.clear();
        assertFalse(submissions.existsById(report.getId()), "거절된 기록은 아무것도 남기지 않는다");
    }

    /** 조건을 채워 팀장이 확정한 상태로 만든다. */
    private void confirmReport() throws Exception {
        ReviewRound approved = round(em, report, 1, upload(1, 'a'), member, "APPROVED");
        confirm(leader, approved.getId(), 200);
    }

    private String submission(String channel, OffsetDateTime submittedAt, String note) {
        var body = json.createObjectNode().put("submittedAt", submittedAt.toString());
        if (channel != null) body.put("channel", channel);
        if (note != null) body.put("note", note);
        return body.toString();
    }

    private JsonNode submit(User as, String body, int expectedStatus) throws Exception {
        return call(put(SUBMISSION, project.getId(), report.getId()).contentType(MediaType.APPLICATION_JSON).content(body), as, expectedStatus);
    }

    private void assertRefused(ReviewRound target, String detail) throws Exception {
        assertEquals(detail, confirm(leader, target.getId(), 409).get("detail").asText());
        assertNotConfirmed();
    }

    private void assertNotConfirmed() {
        em.flush(); em.clear();
        assertFalse(confirmations.existsById(report.getId()), "거절된 확정은 아무것도 남기지 않는다");
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

    private JsonNode read(User as, Object projectId, Object deliverableId, int expectedStatus) throws Exception {
        return call(get(PATH, projectId, deliverableId), as, expectedStatus);
    }

    private JsonNode confirm(User as, Object reviewId, int expectedStatus) throws Exception {
        return call(put(PATH, project.getId(), report.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reviewId\":\"" + reviewId + "\"}"), as, expectedStatus);
    }

    /** 요청마다 서버가 DB에서 새로 읽도록 영속성 컨텍스트를 비운다. as가 null이면 로그인하지 않은 요청이다. */
    private JsonNode call(MockHttpServletRequestBuilder request, User as, int expectedStatus) throws Exception {
        em.flush(); em.clear();
        if (as != null) request.header("Authorization", "Bearer " + jwt.generateAccessToken(as.getEmail()));
        var response = mvc.perform(request).andReturn().getResponse();
        response.setCharacterEncoding("UTF-8");
        assertEquals(expectedStatus, response.getStatus(), response.getContentAsString());
        return response.getContentAsString().isEmpty() ? json.createObjectNode() : json.readTree(response.getContentAsString());
    }
}
