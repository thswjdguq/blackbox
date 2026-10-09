package com.blackbox.agent.web;

import com.blackbox.agent.AgentProperties;
import com.blackbox.agent.AgentRun;
import com.blackbox.agent.AgentRunRepository;
import com.blackbox.agent.AgentTestData;
import com.blackbox.agent.model.AgentModel;
import com.blackbox.agent.tool.AgentTool;
import com.blackbox.agent.tool.ListDeliverablesTool;
import com.blackbox.entity.*;
import com.blackbox.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** 상태 조회와 실행을 실제 서버, 실제 DB, 가짜 모델로 본다(K-30 2장·3장·4장·6장). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.agent.max-concurrent-runs=2")
@ActiveProfiles("test")
class AgentHttpTest {
    static final String PLAN = """
            {"text": "제출물 하나를 찾았습니다.", "proposals": [
              {"kind": "DELIVERABLE_PLAN", "title": "기말 보고서 계획", "rationale": "안내문의 '기말 보고서'에서 뽑았습니다.",
               "content": {"deliverable": {"title": "기말 보고서", "dueDate": "2026-12-01"},
                 "requirements": [{"key": "r1", "content": "결과를 적는다", "required": true}], "tasks": []}}]}""";
    static final String BRIEF = "{\"skill\":\"PLAN_FROM_BRIEF\",\"input\":{\"briefText\":\"기말 보고서를 12월 1일까지 낸다\"}}";
    static final String ASK = "{\"skill\":\"ASK\",\"input\":{\"question\":\"지금 상태는?\"}}";

    @LocalServerPort int port;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper json;
    @Autowired AgentProperties properties;
    @Autowired AgentRunRepository runs;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean AgentModel model;

    final HttpClient http = HttpClient.newHttpClient();
    AgentTestData data;
    User leader, member, observer, outsider;
    Project project;
    Deliverable report;
    volatile String answer = "답";
    volatile CountDownLatch entered = new CountDownLatch(0), release = new CountDownLatch(0);

    @BeforeEach void setUp() {
        data = new AgentTestData(em, transactions, jdbc);
        leader = data.user("김팀장"); member = data.user("이팀원"); observer = data.user("최교수"); outsider = data.user("박외부");
        project = data.project("캡스톤", leader);
        data.join(project, member, "MEMBER"); data.join(project, observer, "OBSERVER");
        report = data.deliverable(project, "중간 보고서", LocalDate.now().plusDays(7));
        data.requirement(report, "문제 정의를 적는다", true);

        when(model.available()).thenReturn(true);
        // 가짜 모델: 제출물 조회 도구를 한 번 부르고, 테스트가 놓아줄 때까지 기다렸다가 정해진 답을 돌려준다
        when(model.generate(any(), any(), any())).thenAnswer(call -> {
            List<AgentTool> tools = call.getArgument(2);
            tools.stream().filter(t -> t.name().equals(ListDeliverablesTool.NAME)).findFirst().ifPresent(t -> t.call("{}"));
            entered.countDown();
            release.await(10, TimeUnit.SECONDS);
            return answer;
        });
    }

    @AfterEach void cleanUp() {
        release.countDown();
        data.cleanup();
    }

    @Test void statusTellsWhatThisPersonCanDoAndWhereEachDeliverableStands() throws Exception {
        JsonNode forMember = body(get("/status", member));
        assertTrue(forMember.path("available").asBoolean());
        assertTrue(forMember.path("unavailableReason").isNull());
        assertEquals(List.of("PLAN_FROM_BRIEF:true", "BREAK_DOWN_TASKS:true", "STATUS_BRIEF:true", "ASK:true"), skills(forMember));
        assertEquals("과제 안내문으로 계획 만들기", forMember.path("skills").get(0).path("label").asText());
        JsonNode stage = forMember.path("stages").get(0);
        assertEquals(report.getId().toString(), stage.path("deliverableId").asText());
        assertEquals("중간 보고서", stage.path("title").asText());
        assertEquals(LocalDate.now().plusDays(7).toString(), stage.path("dueDate").asText());
        assertEquals("NO_TASK", stage.path("stage").asText());
        assertEquals(0, forMember.path("pendingProposalCount").asInt());

        assertEquals(List.of("PLAN_FROM_BRIEF:false", "BREAK_DOWN_TASKS:false", "STATUS_BRIEF:true", "ASK:true"),
                skills(body(get("/status", observer))));

        assertEquals(401, get("/status", null).statusCode());
        assertEquals(403, get("/status", outsider).statusCode());
        assertEquals(404, send("GET", "/api/projects/" + UUID.randomUUID() + "/agent/status", member, null).statusCode());

        when(model.available()).thenReturn(false);
        JsonNode off = body(get("/status", member));
        assertFalse(off.path("available").asBoolean());
        assertEquals("AI 기능이 설정되지 않았습니다", off.path("unavailableReason").asText());
    }

    @Test void aRunStreamsStepTextProposalAndDone() throws Exception {
        answer = PLAN;

        HttpResponse<String> response = post("/runs", member, BRIEF);

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/event-stream"));
        assertEquals("no", response.headers().firstValue("X-Accel-Buffering").orElse(""), "프록시가 응답을 모아 두지 않게 알린다");
        List<Event> events = events(response.body());
        assertEquals(List.of("step", "text", "proposal", "done"), events.stream().map(Event::name).toList());
        assertEquals("list_deliverables", events.get(0).data().path("tool").asText());
        assertEquals("제출물과 요구사항 조회", events.get(0).data().path("label").asText());
        assertEquals("제출물 하나를 찾았습니다.", events.get(1).data().path("text").asText());

        JsonNode card = events.get(2).data();
        assertEquals("DELIVERABLE_PLAN", card.path("kind").asText());
        assertEquals("PENDING", card.path("status").asText());
        assertEquals("기말 보고서", card.path("content").path("deliverable").path("title").asText());
        assertEquals("2026-12-01", card.path("content").path("deliverable").path("dueDate").asText());
        assertFalse(card.path("edited").asBoolean());
        assertEquals("이팀원", card.path("requestedBy").path("name").asText());
        assertFalse(card.path("requestedBy").has("email"));
        assertTrue(card.has("decision") && card.path("decision").isNull(), "결정 전에도 필드는 있다");
        assertTrue(card.has("results") && card.path("results").isNull());
        assertTrue(card.path("createdAt").asText().matches(".*([+-]\\d\\d:\\d\\d|Z)$"), "시각에 시간대가 있다");

        JsonNode done = events.get(3).data();
        assertEquals(card.path("runId").asText(), done.path("runId").asText());
        assertEquals(card.path("id").asText(), done.path("proposalIds").get(0).asText());
        assertEquals(1, body(get("/status", member)).path("pendingProposalCount").asInt());
    }

    @Test void whatIsWrongBeforeTheStreamIsAnHttpErrorWithAReadableDetail() throws Exception {
        HttpResponse<String> forbidden = post("/runs", observer, BRIEF);
        assertEquals(403, forbidden.statusCode());
        assertEquals("관찰자는 제안을 만들거나 채택할 수 없습니다", json.readTree(forbidden.body()).path("detail").asText());
        assertEquals(200, post("/runs", observer, ASK).statusCode());

        assertEquals(400, post("/runs", member, "{\"skill\":\"NOPE\",\"input\":{}}").statusCode());
        assertEquals(400, post("/runs", member, "{\"skill\":\"PLAN_FROM_BRIEF\"}").statusCode());
        assertEquals(400, post("/runs", member, "{잘못된 JSON").statusCode());
        assertEquals(404, post("/runs", member, "{\"skill\":\"BREAK_DOWN_TASKS\",\"input\":{\"deliverableId\":\"" + UUID.randomUUID() + "\"}}").statusCode());
        assertEquals(401, post("/runs", null, ASK).statusCode());
        assertEquals(403, post("/runs", outsider, ASK).statusCode());

        when(model.available()).thenReturn(false);
        HttpResponse<String> off = post("/runs", member, ASK);
        assertEquals(503, off.statusCode());
        assertEquals("AI 기능이 설정되지 않았습니다", json.readTree(off.body()).path("detail").asText());
    }

    @Test void oneRunPerPersonAndNoMoreThanTheServerCanHold() throws Exception {
        entered = new CountDownLatch(2); release = new CountDownLatch(1);
        CompletableFuture<HttpResponse<String>> first = http.sendAsync(request("POST", path("/runs"), member, ASK), HttpResponse.BodyHandlers.ofString());
        CompletableFuture<HttpResponse<String>> second = http.sendAsync(request("POST", path("/runs"), leader, ASK), HttpResponse.BodyHandlers.ofString());
        assertTrue(entered.await(10, TimeUnit.SECONDS), "두 실행이 모델을 기다리는 중이다");

        HttpResponse<String> again = post("/runs", member, ASK);
        assertEquals(409, again.statusCode());
        assertEquals("앞의 요청이 끝난 뒤 다시 시도해주세요", json.readTree(again.body()).path("detail").asText());

        // 서버가 동시에 돌리는 수(이 테스트에서는 2)가 찼다. 받지 못한 실행은 실패로 끝나 다음 요청을 막지 않는다
        assertEquals(429, post("/runs", observer, ASK).statusCode());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM agent_runs WHERE requested_by = ? AND status = 'RUNNING'",
                Integer.class, observer.getId()));

        release.countDown();
        assertEquals(List.of("step", "text", "done"), events(first.get(10, TimeUnit.SECONDS).body()).stream().map(Event::name).toList());
        assertEquals(200, second.get(10, TimeUnit.SECONDS).statusCode());
        assertEquals(200, post("/runs", member, ASK).statusCode(), "끝난 뒤에는 다시 실행할 수 있다");
    }

    @Test void eventsArriveOneByOneWhileTheRunIsStillGoing() throws Exception {
        answer = PLAN;
        entered = new CountDownLatch(1); release = new CountDownLatch(1);
        HttpResponse<java.io.InputStream> response = http.send(request("POST", path("/runs"), member, BRIEF), HttpResponse.BodyHandlers.ofInputStream());
        java.io.BufferedReader stream = new java.io.BufferedReader(new java.io.InputStreamReader(response.body(), java.nio.charset.StandardCharsets.UTF_8));

        // 모델이 아직 답하지 않았는데 조회 한 줄이 먼저 도착한다. 끝에 한꺼번에 오지 않는다
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        assertEquals("event:step", CompletableFuture.supplyAsync(() -> firstLine(stream)).get(5, TimeUnit.SECONDS));
        assertEquals(AgentRun.Status.RUNNING, onlyRun().getStatus());

        release.countDown();
        String rest = stream.lines().collect(java.util.stream.Collectors.joining("\n"));
        assertTrue(rest.contains("event:proposal") && rest.contains("event:done"), rest);
    }

    @Test void aRunGoesOnWhenTheScreenHangsUp() throws Exception {
        answer = PLAN;
        entered = new CountDownLatch(1); release = new CountDownLatch(1);
        HttpResponse<java.io.InputStream> response = http.send(request("POST", path("/runs"), member, BRIEF), HttpResponse.BodyHandlers.ofInputStream());
        assertTrue(entered.await(10, TimeUnit.SECONDS));

        response.body().close();   // 화면이 창을 닫았다
        release.countDown();

        // 실행은 끝까지 가고 카드는 남는다. 다시 열면 목록에서 받을 수 있다(K-30 4장)
        assertEquals(AgentRun.Status.DONE, awaitEnd().getStatus());
        assertEquals(1, body(get("/status", member)).path("pendingProposalCount").asInt());
        assertEquals(200, post("/runs", member, ASK).statusCode());
    }

    @Test void aRunOverTheTimeLimitEndsWithAnErrorAndSavesNothing() throws Exception {
        Duration before = properties.getRunTimeLimit();
        properties.setRunTimeLimit(Duration.ofMillis(500));
        try {
            answer = PLAN;
            entered = new CountDownLatch(1); release = new CountDownLatch(1);

            HttpResponse<String> response = post("/runs", member, BRIEF);   // 모델이 답하지 않는 동안 상한이 지난다

            List<Event> events = events(response.body());
            assertEquals("error", events.get(events.size() - 1).name());
            assertEquals("AI 응답을 받지 못했습니다. 다시 시도해주세요", events.get(events.size() - 1).data().path("detail").asText());

            release.countDown();   // 늦게 온 답은 저장하지 않는다
            AgentRun run = awaitEnd();
            assertEquals(AgentRun.Status.FAILED, run.getStatus());
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM agent_proposals WHERE project_id = ?", Integer.class, project.getId()));
            assertEquals(200, post("/runs", member, ASK).statusCode(), "상한을 넘긴 실행은 다음 요청을 막지 않는다");
        } finally {
            properties.setRunTimeLimit(before);
        }
    }

    private static String firstLine(java.io.BufferedReader stream) {
        try { return stream.readLine(); } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }

    private AgentRun onlyRun() {
        return runs.findAll().stream().filter(r -> r.getProject().getId().equals(project.getId())).findFirst().orElseThrow();
    }

    private AgentRun awaitEnd() throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            AgentRun run = onlyRun();
            if (run.getStatus() != AgentRun.Status.RUNNING) return run;
            Thread.sleep(50);
        }
        throw new AssertionError("실행이 끝나지 않았다");
    }

    record Event(String name, JsonNode data) {}

    private List<Event> events(String body) throws Exception {
        List<Event> events = new ArrayList<>();
        String name = null;
        for (String line : body.split("\n")) {
            if (line.startsWith("event:")) name = line.substring(6).strip();
            if (line.startsWith("data:")) events.add(new Event(name, json.readTree(line.substring(5))));
        }
        return events;
    }

    private static List<String> skills(JsonNode status) {
        List<String> out = new ArrayList<>();
        status.path("skills").forEach(s -> out.add(s.path("skill").asText() + ":" + s.path("allowed").asBoolean()));
        return out;
    }

    private JsonNode body(HttpResponse<String> response) throws Exception {
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private String path(String tail) { return "/api/projects/" + project.getId() + "/agent" + tail; }
    private HttpResponse<String> get(String tail, User user) throws Exception { return send("GET", path(tail), user, null); }
    private HttpResponse<String> post(String tail, User user, String body) throws Exception { return send("POST", path(tail), user, body); }

    private HttpResponse<String> send(String method, String path, User user, String body) throws Exception {
        return http.send(request(method, path, user, body), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest request(String method, String path, User user, String body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) request.header("Content-Type", "application/json");
        if (user != null) request.header("Authorization", "Bearer " + jwt.generateAccessToken(user.getEmail()));
        return request.build();
    }
}
