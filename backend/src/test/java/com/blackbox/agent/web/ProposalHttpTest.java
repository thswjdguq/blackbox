package com.blackbox.agent.web;

import com.blackbox.agent.AgentOriginReader;
import com.blackbox.agent.AgentProposal;
import com.blackbox.agent.AgentTestData;
import com.blackbox.dto.CreateTaskRequest;
import com.blackbox.entity.*;
import com.blackbox.security.JwtService;
import com.blackbox.service.TaskService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

/** 제안 카드의 조회·수정·채택·거절을 실제 서버와 실제 DB로 본다(K-30 2장·3장·6장·12장). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ProposalHttpTest {
    static final String PLAN = """
            {"deliverable": {"title": "중간 보고서", "description": "문제 정의와 설계", "dueDate": "2026-10-18", "submissionMethod": "LMS 과제함"},
             "requirements": [{"key": "r1", "content": "문제 정의를 적는다", "required": true}, {"key": "r2", "content": "분량은 10쪽 안팎", "required": false}],
             "tasks": [{"title": "문제 정의 초안", "completionCriteria": "한 쪽으로 정리", "dueDate": "2026-10-13", "requirementKey": "r1"},
                       {"title": "표지 만들기", "completionCriteria": null, "dueDate": null, "requirementKey": null}]}""";
    static final String DECIDED = "이미 결정된 제안입니다";

    @LocalServerPort int port;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper json;
    @Autowired AgentOriginReader origin;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean TaskService taskService;

    final HttpClient http = HttpClient.newHttpClient();
    AgentTestData data;
    User leader, member, observer, outsider;
    Project project, otherProject;

    @BeforeEach void setUp() {
        data = new AgentTestData(em, transactions, jdbc);
        leader = data.user("김팀장"); member = data.user("이팀원"); observer = data.user("최교수"); outsider = data.user("박외부");
        project = data.project("캡스톤", leader);
        data.join(project, member, "MEMBER"); data.join(project, observer, "OBSERVER");
        otherProject = data.project("다른 프로젝트", outsider);
    }

    @AfterEach void cleanUp() { data.cleanup(); }

    @Test void cardsAreListedNewestFirstAndReadByEveryMember() throws Exception {
        AgentProposal first = data.card(project, member, "DELIVERABLE_PLAN", "첫 카드", PLAN);
        AgentProposal second = data.card(project, member, "DELIVERABLE_PLAN", "둘째 카드", PLAN);
        AgentProposal third = data.card(project, leader, "DELIVERABLE_PLAN", "셋째 카드", PLAN);
        assertEquals(200, post(third, "/reject", leader).statusCode());
        AgentProposal secret = data.card(otherProject, outsider, "DELIVERABLE_PLAN", "남의 카드", PLAN);

        assertEquals(List.of("셋째 카드", "둘째 카드", "첫 카드"), titles(body(send("GET", "", observer, null))));
        assertEquals(List.of("둘째 카드", "첫 카드"), titles(body(send("GET", "?status=PENDING", member, null))));
        assertEquals(List.of("셋째 카드"), titles(body(send("GET", "?status=REJECTED", member, null))));
        assertEquals(400, send("GET", "?status=WRONG", member, null).statusCode());

        JsonNode card = body(send("GET", "/" + first.getId(), observer, null));
        assertEquals("PENDING", card.path("status").asText());
        assertEquals("이팀원", card.path("requestedBy").path("name").asText());
        assertFalse(card.path("edited").asBoolean());
        assertTrue(card.has("decision") && card.path("decision").isNull() && card.has("results") && card.path("results").isNull());
        assertEquals("문제 정의 초안", card.path("content").path("tasks").get(0).path("title").asText());

        // 다른 프로젝트의 카드는 없는 카드와 똑같이 보인다
        HttpResponse<String> foreign = send("GET", "/" + secret.getId(), member, null);
        assertEquals(404, foreign.statusCode());
        assertFalse(foreign.body().contains("남의 카드"));
        assertEquals(404, post(secret, "/accept", member).statusCode());
        assertEquals(404, send("GET", "/" + UUID.randomUUID(), member, null).statusCode());
        assertEquals(400, send("GET", "/not-a-uuid", member, null).statusCode());
        assertEquals(403, send("GET", "", outsider, null).statusCode());
        assertEquals(401, send("GET", "", null, null).statusCode());
    }

    @Test void anEditedCardKeepsTheOriginalAndOnlyTheFieldsItsKindKnows() throws Exception {
        AgentProposal card = data.card(project, member, "DELIVERABLE_PLAN", "계획", PLAN);
        String edited = PLAN.replace("\"문제 정의 초안\"", "\"문제 정의 최종본\", \"assignee\": \"이팀원\"");

        JsonNode saved = body(send("PUT", "/" + card.getId(), leader, "{\"content\":" + edited + "}"));

        assertTrue(saved.path("edited").asBoolean());
        JsonNode task = body(send("GET", "/" + card.getId(), observer, null)).path("content").path("tasks").get(0);
        assertEquals("문제 정의 최종본", task.path("title").asText());
        assertFalse(task.has("assignee"), "담당자는 카드에 없다");
        assertTrue(jdbc.queryForObject("SELECT original::text FROM agent_proposals WHERE id = ?", String.class, card.getId()).contains("문제 정의 초안"),
                "모델이 만든 원본은 그대로다");

        HttpResponse<String> tooLong = send("PUT", "/" + card.getId(), member, "{\"content\":" + PLAN.replace("중간 보고서", "가".repeat(300)) + "}");
        assertEquals(400, tooLong.statusCode());
        assertTrue(json.readTree(tooLong.body()).path("detail").asText().contains("deliverable.title"), "어느 칸인지 알려 준다: " + tooLong.body());
        assertEquals(400, send("PUT", "/" + card.getId(), member, "{}").statusCode());
        HttpResponse<String> refused = send("PUT", "/" + card.getId(), observer, "{\"content\":" + PLAN + "}");
        assertEquals(403, refused.statusCode());
        assertEquals("관찰자는 제안을 만들거나 채택할 수 없습니다", json.readTree(refused.body()).path("detail").asText());
    }

    @Test void acceptingAPlanCreatesTheDeliverableItsRequirementsAndTasksOnce() throws Exception {
        AgentProposal card = data.card(project, member, "DELIVERABLE_PLAN", "계획", PLAN);

        JsonNode accepted = body(post(card, "/accept", leader));

        assertEquals("ACCEPTED", accepted.path("status").asText());
        assertEquals("ACCEPTED", accepted.path("decision").path("result").asText());
        assertEquals("김팀장", accepted.path("decision").path("decidedBy").path("name").asText());
        JsonNode results = accepted.path("results");
        String deliverableId = results.path("deliverableId").asText();
        assertEquals(2, results.path("requirementIds").size());
        assertEquals(2, results.path("taskIds").size());

        // 만들어진 것은 기존 API로 조회된다. 채택한 사람이 만든 것으로 남고, 업무는 요구사항에 연결돼 있다
        JsonNode deliverable = json.readTree(api("GET", "/api/projects/" + project.getId() + "/deliverables/" + deliverableId, observer, null).body());
        assertEquals("중간 보고서", deliverable.path("title").asText());
        assertEquals("2026-10-18", deliverable.path("dueDate").asText());
        assertEquals("LMS 과제함", deliverable.path("submissionMethod").asText());
        assertEquals(2, deliverable.path("requirements").size());
        JsonNode tasks = json.readTree(api("GET", "/api/projects/" + project.getId() + "/tasks", observer, null).body());
        assertEquals(2, tasks.size());
        JsonNode draft = find(tasks, "문제 정의 초안");
        assertEquals(deliverableId, draft.path("deliverableId").asText());
        assertEquals("문제 정의를 적는다", draft.path("requirementContent").asText());
        assertEquals("한 쪽으로 정리", draft.path("completionCriteria").asText());
        assertEquals("2026-10-13", draft.path("dueDate").asText());
        assertEquals(leader.getId().toString(), draft.path("createdBy").asText());
        assertEquals(0, draft.path("assignees").size(), "에이전트는 사람을 배정하지 않는다");
        assertEquals(deliverableId, find(tasks, "표지 만들기").path("deliverableId").asText());
        assertTrue(find(tasks, "표지 만들기").path("requirementId").isNull());

        // 다시 눌러도 지금 카드를 돌려줄 뿐 새로 만들지 않는다
        assertEquals(results, body(post(card, "/accept", member)).path("results"));
        assertEquals(1, count("deliverables"));
        assertEquals(2, count("tasks"));

        assertTrue(origin.createdFromProposal(project.getId(), "TASK", UUID.fromString(draft.path("id").asText())));
        assertTrue(origin.createdFromProposal(project.getId(), "DELIVERABLE", UUID.fromString(deliverableId)));
        assertFalse(origin.createdFromProposal(project.getId(), "TASK", UUID.randomUUID()));
        assertFalse(origin.createdFromProposal(otherProject.getId(), "TASK", UUID.fromString(draft.path("id").asText())));
    }

    @Test void twoAcceptsAtTheSameTimeCreateTheRecordsOnce() throws Exception {
        AgentProposal card = data.card(project, member, "DELIVERABLE_PLAN", "계획", PLAN);
        HttpRequest accept = request("POST", path("/" + card.getId() + "/accept"), leader, "");

        List<CompletableFuture<HttpResponse<String>>> both = List.of(
                http.sendAsync(accept, HttpResponse.BodyHandlers.ofString()), http.sendAsync(accept, HttpResponse.BodyHandlers.ofString()));

        for (CompletableFuture<HttpResponse<String>> one : both) assertEquals(200, one.get().statusCode());
        assertEquals(both.get(0).get().body().replaceAll("\"decidedAt\":\"[^\"]+\"", ""), both.get(1).get().body().replaceAll("\"decidedAt\":\"[^\"]+\"", ""));
        assertEquals(1, count("deliverables"));
        assertEquals(2, count("tasks"));
    }

    @Test void aCardThatCannotBeAcceptedCreatesNothingAndStaysPending() throws Exception {
        // 기한을 비워 둔 초안: 사람이 채워야 채택된다
        AgentProposal noDate = data.card(project, member, "DELIVERABLE_PLAN", "기한 없음", PLAN.replace("\"2026-10-18\"", "null"));
        HttpResponse<String> refused = post(noDate, "/accept", member);
        assertEquals(400, refused.statusCode());
        assertTrue(json.readTree(refused.body()).path("detail").asText().contains("deliverable.dueDate"), refused.body());
        assertEquals(0, count("deliverables"));
        assertEquals("PENDING", body(send("GET", "/" + noDate.getId(), member, null)).path("status").asText());
        body(send("PUT", "/" + noDate.getId(), member, "{\"content\":" + PLAN + "}"));
        assertEquals(200, post(noDate, "/accept", member).statusCode());
        assertEquals(1, count("deliverables"));

        // 형식에 맞지 않는 내용이 들어 있는 카드: 만들기 전에 막힌다
        AgentProposal tooLong = data.card(project, member, "DELIVERABLE_PLAN", "긴 제목", PLAN.replace("표지 만들기", "가".repeat(300)));
        assertEquals(400, post(tooLong, "/accept", member).statusCode());

        // 만드는 도중의 실패: 제출물과 앞의 업무까지 모두 되돌아간다
        AgentProposal midway = data.card(project, member, "DELIVERABLE_PLAN", "도중 실패", PLAN.replace("중간 보고서", "기말 보고서"));
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "두 번째 업무에서 실패"))
                .when(taskService).createTask(any(), argThat((CreateTaskRequest r) -> r != null && r.title().equals("표지 만들기")), any());
        assertEquals(400, post(midway, "/accept", member).statusCode());
        assertEquals(1, count("deliverables"), "기말 보고서는 만들어지지 않았다");
        assertEquals(2, count("tasks"));
        assertEquals("PENDING", body(send("GET", "/" + midway.getId(), member, null)).path("status").asText());
        assertTrue(body(send("GET", "/" + midway.getId(), member, null)).path("results").isNull());
    }

    @Test void taskDraftsAreAttachedToAnExistingDeliverable() throws Exception {
        Deliverable report = data.deliverable(project, "중간 보고서", LocalDate.now().plusDays(7));
        DeliverableRequirement requirement = data.requirement(report, "문제 정의를 적는다", true);
        String content = "{\"deliverableId\":\"" + report.getId() + "\",\"tasks\":[{\"title\":\"초안 쓰기\",\"completionCriteria\":\"한 쪽\",\"dueDate\":null,\"requirementId\":\""
                + requirement.getId() + "\"}]}";
        AgentProposal card = data.card(project, member, "TASKS", "업무 초안", content);

        JsonNode results = body(post(card, "/accept", member)).path("results");

        assertTrue(results.path("deliverableId").isNull(), "새로 만든 제출물은 없다");
        assertEquals(1, results.path("taskIds").size());
        JsonNode task = json.readTree(api("GET", "/api/projects/" + project.getId() + "/tasks", member, null).body()).get(0);
        assertEquals(report.getId().toString(), task.path("deliverableId").asText());
        assertEquals(requirement.getId().toString(), task.path("requirementId").asText());

        // 카드를 만든 뒤 요구사항이 지워졌다면 채택되지 않는다
        AgentProposal stale = data.card(project, member, "TASKS", "낡은 카드", content.replace(requirement.getId().toString(), UUID.randomUUID().toString()));
        assertEquals(400, post(stale, "/accept", member).statusCode());
        assertEquals(1, count("tasks"));
    }

    @Test void aDecisionIsFinal() throws Exception {
        AgentProposal rejected = data.card(project, member, "DELIVERABLE_PLAN", "거절할 카드", PLAN);
        assertEquals(403, post(rejected, "/reject", observer).statusCode());
        assertEquals(403, post(rejected, "/accept", observer).statusCode());

        JsonNode decision = body(post(rejected, "/reject", leader));
        assertEquals("REJECTED", decision.path("status").asText());
        assertEquals("REJECTED", decision.path("decision").path("result").asText());
        assertTrue(decision.path("results").isNull());
        assertEquals(200, post(rejected, "/reject", member).statusCode(), "거절을 다시 눌러도 그대로다");

        HttpResponse<String> late = post(rejected, "/accept", member);
        assertEquals(409, late.statusCode());
        assertEquals(DECIDED, json.readTree(late.body()).path("detail").asText());
        assertEquals(409, send("PUT", "/" + rejected.getId(), member, "{\"content\":" + PLAN + "}").statusCode());
        assertEquals(0, count("deliverables"));

        AgentProposal accepted = data.card(project, member, "DELIVERABLE_PLAN", "채택할 카드", PLAN);
        assertEquals(200, post(accepted, "/accept", member).statusCode());
        assertEquals(409, post(accepted, "/reject", member).statusCode());
        assertEquals(409, send("PUT", "/" + accepted.getId(), member, "{\"content\":" + PLAN + "}").statusCode());
        assertEquals("ACCEPTED", body(send("GET", "/" + accepted.getId(), member, null)).path("status").asText());
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE project_id = ?", Integer.class, project.getId());
    }

    private static JsonNode find(JsonNode tasks, String title) {
        for (JsonNode task : tasks) if (task.path("title").asText().equals(title)) return task;
        throw new AssertionError("업무가 없다: " + title);
    }

    private static List<String> titles(JsonNode cards) {
        List<String> titles = new ArrayList<>();
        cards.forEach(c -> titles.add(c.path("title").asText()));
        return titles;
    }

    private JsonNode body(HttpResponse<String> response) throws Exception {
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private String path(String tail) { return "/api/projects/" + project.getId() + "/agent/proposals" + tail; }
    private HttpResponse<String> send(String method, String tail, User user, String body) throws Exception { return api(method, path(tail), user, body); }
    /** 카드가 어느 프로젝트의 것이든 이 프로젝트의 경로로 부른다 */
    private HttpResponse<String> post(AgentProposal card, String action, User user) throws Exception {
        return send("POST", "/" + card.getId() + action, user, "");
    }

    private HttpResponse<String> api(String method, String path, User user, String body) throws Exception {
        return http.send(request(method, path, user, body), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest request(String method, String path, User user, String body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null && !body.isEmpty()) request.header("Content-Type", "application/json");
        if (user != null) request.header("Authorization", "Bearer " + jwt.generateAccessToken(user.getEmail()));
        return request.build();
    }
}
