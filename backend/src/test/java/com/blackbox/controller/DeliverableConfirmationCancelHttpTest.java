package com.blackbox.controller;

import com.blackbox.entity.*;
import com.blackbox.repository.DeliverableConfirmationCancellationRepository;
import com.blackbox.repository.DeliverableConfirmationRepository;
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

import java.util.List;
import java.util.UUID;

import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * K-20 0.3의 확정 취소를 실제 DB와 HTTP 요청으로 확인한다(11장의 확정 취소 줄).
 * 테스트마다 트랜잭션 하나로 묶여 끝나면 되돌아간다.
 */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class DeliverableConfirmationCancelHttpTest {
    static final String BASE = "/api/projects/{projectId}/deliverables/{deliverableId}";
    static final String FILE = "중간보고서.pdf";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EntityManager em;
    @Autowired JwtService jwt;
    @Autowired DeliverableConfirmationRepository confirmations;
    @Autowired DeliverableConfirmationCancellationRepository cancellations;
    @MockBean UnresolvedCommentCounter unresolved;

    User leader, member, observer;
    Project project;
    Deliverable report;

    @BeforeEach void setup() {
        leader = user(em, "송팀장"); member = user(em, "손팀원"); observer = user(em, "관찰자");
        project = project(em, leader);
        member(em, project, leader, "LEADER"); member(em, project, member, "MEMBER"); member(em, project, observer, "OBSERVER");
        report = deliverable(em, project, "중간 보고서");
    }

    /** 취소하면 확정이 취소 기록으로 옮겨지고, 그 회차로는 다시 확정할 수 없다 */
    @Test void leaderCancelsAndTheConfirmationMovesToTheRecord() throws Exception {
        ReviewRound first = confirmed(1, 'a');

        JsonNode body = cancel(leader, first.getId(), "  표지의 과목명 오타  ", 200);
        assertEquals("DRAFT", body.get("status").asText());
        assertTrue(body.get("confirmation").isNull());
        assertFalse(body.get("canConfirm").asBoolean());
        assertEquals("1회차의 확정을 취소했습니다. 고친 파일로 새 회차를 열어주세요", detail(body, "APPROVED_FILE"));
        JsonNode record = single(body.get("cancellations"));
        assertEquals(first.getId().toString(), record.get("reviewId").asText());
        assertEquals(1, record.get("roundNo").asInt());
        assertEquals("송팀장", record.at("/confirmedBy/name").asText());
        assertEquals("송팀장", record.at("/cancelledBy/name").asText());
        assertEquals("표지의 과목명 오타", record.get("reason").asText(), "앞뒤 공백은 떼고 저장한다");
        assertFalse(confirmations.existsById(report.getId()));

        // 같은 회차로 다시 보낸 것은 중복 클릭이다. 기록이 늘지 않고 처음 사유가 남는다
        assertEquals(body, cancel(leader, first.getId(), "다른 사유", 200));
        assertEquals(body, read(observer));
        assertEquals(1, cancellations.count());

        assertEquals("1회차의 확정을 취소했습니다. 고친 파일로 새 회차를 열어주세요", confirm(first.getId(), 409).get("detail").asText());
    }

    /** 새 회차의 승인으로 다시 확정할 수 있고, 취소 기록은 쌓인다 */
    @Test void newRoundCanBeConfirmedAgainAndRecordsAccumulate() throws Exception {
        ReviewRound first = confirmed(1, 'a');
        cancel(leader, first.getId(), "표지 오타", 200);
        ReviewRound second = round(em, report, 2, upload(2, 'b'), member, "APPROVED");

        JsonNode body = confirm(second.getId(), 200);
        assertEquals("CONFIRMED", body.get("status").asText());
        assertEquals(second.getId().toString(), body.at("/confirmation/reviewId").asText());
        assertEquals(1, body.get("cancellations").size());

        // 지난 회차로 다시 보낸 취소는 중복 클릭이다. 새 확정을 취소하지 않는다
        assertFalse(cancel(leader, first.getId(), "표지 오타", 200).get("confirmation").isNull());
        assertTrue(cancel(leader, second.getId(), "목차 번호", 200).get("confirmation").isNull());
        assertEquals(List.of("표지 오타", "목차 번호"), read(member).get("cancellations").findValuesAsText("reason"));
    }

    @Test void cancelIsRefusedWithAReason() throws Exception {
        ReviewRound changes = round(em, report, 1, upload(1, 'a'), member, "CHANGES_REQUESTED");
        assertEquals("확정되지 않은 제출물입니다", cancel(leader, changes.getId(), "사유", 409).get("detail").asText());

        ReviewRound approved = round(em, report, 2, upload(2, 'b'), member, "APPROVED");
        confirm(approved.getId(), 200);
        assertEquals("확정 취소는 팀장만 할 수 있습니다", cancel(member, approved.getId(), "사유", 403).get("detail").asText());
        cancel(observer, approved.getId(), "사유", 403);
        cancel(leader, approved.getId(), "   ", 400);
        cancel(leader, approved.getId(), "가".repeat(1001), 400);
        call(post(BASE + "/confirmation/cancellation", project.getId(), report.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"사유\"}"), leader, 400);
        cancel(leader, UUID.randomUUID(), "사유", 404);
        // 화면이 보여 준 확정과 다른 회차다
        assertEquals("취소하려는 확정이 바뀌었습니다. 새로고침해 다시 확인해주세요",
                cancel(leader, changes.getId(), "사유", 409).get("detail").asText());

        call(put(BASE + "/submission", project.getId(), report.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"submittedAt\":\"2026-10-01T09:30:00+09:00\"}"), member, 200);
        assertEquals("제출을 기록한 제출물은 확정을 취소할 수 없습니다", cancel(leader, approved.getId(), "사유", 409).get("detail").asText());
        assertTrue(confirmations.existsById(report.getId()));
        assertEquals(0, cancellations.count());
    }

    /** 취소한 회차가 최신이면 상태는 DRAFT, 결정은 APPROVED로 남고 회차에 취소가 표시된다. 막혔던 쓰기가 풀린다 */
    @Test void cancelledLatestRoundShowsAsDraftAndUnlocksChanges() throws Exception {
        ReviewRound first = confirmed(1, 'a');
        String requirements = "/api/projects/" + project.getId() + "/deliverables/" + report.getId() + "/requirements";
        call(post(requirements).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"분량 10쪽\",\"required\":true}"), member, 409);

        cancel(leader, first.getId(), "표지 오타", 200);
        JsonNode one = call(get("/api/projects/{p}/deliverables/{d}", project.getId(), report.getId()), observer, 200);
        assertEquals("DRAFT", one.get("status").asText());
        assertEquals("APPROVED", one.get("latestDecision").asText());
        for (JsonNode d : call(get("/api/projects/{p}/deliverables", project.getId()), observer, 200))
            assertEquals("DRAFT", d.get("status").asText());

        JsonNode round = single(call(get(BASE + "/reviews", project.getId(), report.getId()), observer, 200).get("rounds"));
        assertTrue(round.get("confirmationCancelled").asBoolean());
        assertFalse(round.get("currentBasis").asBoolean());

        call(post(requirements).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"분량 10쪽\",\"required\":true}"), member, 201);
    }

    /** 1회차를 승인받아 팀장이 확정한 상태로 만든다. 파일은 팀원이 올린다 */
    private ReviewRound confirmed(int version, char content) throws Exception {
        ReviewRound approved = round(em, report, 1, upload(version, content), member, "APPROVED");
        confirm(approved.getId(), 200);
        return approved;
    }

    private FileVault upload(int version, char content) { return file(em, project, member, FILE, version, content); }

    private static JsonNode single(JsonNode array) {
        assertEquals(1, array.size(), array.toString());
        return array.get(0);
    }

    private static String detail(JsonNode body, String code) {
        for (JsonNode check : body.get("checks")) if (code.equals(check.get("code").asText())) return check.get("detail").asText();
        return fail(code + " 검사가 응답에 없다");
    }

    private JsonNode read(User as) throws Exception {
        return call(get(BASE + "/confirmation", project.getId(), report.getId()), as, 200);
    }

    private JsonNode confirm(UUID reviewId, int expectedStatus) throws Exception {
        return call(put(BASE + "/confirmation", project.getId(), report.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reviewId\":\"" + reviewId + "\"}"), leader, expectedStatus);
    }

    private JsonNode cancel(User as, UUID reviewId, String reason, int expectedStatus) throws Exception {
        String body = json.createObjectNode().put("reviewId", reviewId.toString()).put("reason", reason).toString();
        return call(post(BASE + "/confirmation/cancellation", project.getId(), report.getId())
                .contentType(MediaType.APPLICATION_JSON).content(body), as, expectedStatus);
    }

    /** 요청마다 서버가 DB에서 새로 읽도록 영속성 컨텍스트를 비운다. */
    private JsonNode call(MockHttpServletRequestBuilder request, User as, int expectedStatus) throws Exception {
        em.flush(); em.clear();
        request.header("Authorization", "Bearer " + jwt.generateAccessToken(as.getEmail()));
        var response = mvc.perform(request).andReturn().getResponse();
        response.setCharacterEncoding("UTF-8");
        assertEquals(expectedStatus, response.getStatus(), response.getContentAsString());
        return response.getContentAsString().isEmpty() ? json.createObjectNode() : json.readTree(response.getContentAsString());
    }
}
