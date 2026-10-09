package com.blackbox.agent.tool;

import com.blackbox.agent.AgentProperties;
import com.blackbox.entity.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

/** 읽기 도구가 실제 DB의 기록을 기존 조회로 읽어, 모델로 나가도 되는 것만 돌려주는지 본다(K-30 5장). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AgentToolboxTest {
    static final List<String> ALL = List.of(GetProjectTool.NAME, ListDeliverablesTool.NAME, GetProgressTool.NAME,
            ListTasksTool.NAME, ListAlertsTool.NAME, GetScoresTool.NAME);

    @Autowired EntityManager em;
    @Autowired AgentToolbox toolbox;
    @Autowired AgentProperties properties;
    @Autowired ObjectMapper json;

    User leader, member, outsider;
    Project project;
    Deliverable report;
    DeliverableRequirement metRequirement, openRequirement;
    LocalDate today;

    @BeforeEach void setUp() {
        today = LocalDate.now(properties.getZone());
        leader = user("김팀장"); member = user("이팀원"); outsider = user("박외부");
        project = project("캡스톤", leader);
        join(project, leader, "LEADER"); join(project, member, "MEMBER");

        report = deliverable(project, "중간 보고서", today.plusDays(7));
        metRequirement = requirement(report, "문제 정의를 적는다", true);
        metRequirement.assess(leader);
        openRequirement = requirement(report, "분량은 10쪽 안팎", false);

        Task late = task("문제 정의 초안", "TODO", today.minusDays(1));
        late.setDeliverable(report); late.setRequirement(metRequirement); late.setCompletionCriteria("한 쪽으로 정리");
        assign(late, member);
        task("오늘 마감 업무", "IN_PROGRESS", today);
        Task done = task("끝난 업무", "DONE", today.minusDays(3));
        done.setDeliverable(report); done.setCompletedAt(OffsetDateTime.now());

        Alert alert = new Alert(); alert.setProject(project); alert.setUser(member); alert.setAlertType("DROPOUT");
        alert.setSeverity("HIGH"); alert.setMessage("이팀원님이 최근 2주 동안 활동 기록이 없습니다"); em.persist(alert);
        ContributionScore score = new ContributionScore(); score.setProject(project); score.setUser(member);
        score.setTaskParticipated(true); score.setParticipationLevel("PARTIAL"); score.setCalculatedAt(OffsetDateTime.now());
        em.persist(score);
        em.flush(); em.clear();
    }

    @Test void everyToolReadsTheRecordsOfTheProject() throws Exception {
        Map<String, AgentTool> tools = bind(leader);

        JsonNode p = call(tools, GetProjectTool.NAME, "{}");
        assertEquals("캡스톤", p.path("name").asText());
        assertEquals(today.toString(), p.path("today").asText());
        assertEquals(Map.of("김팀장", "LEADER", "이팀원", "MEMBER"), stream(p.path("members"))
                .collect(Collectors.toMap(m -> m.path("name").asText(), m -> m.path("role").asText())));

        JsonNode deliverables = call(tools, ListDeliverablesTool.NAME, "");
        assertEquals(1, deliverables.path("total").asInt());
        assertFalse(deliverables.path("truncated").asBoolean());
        JsonNode d = deliverables.path("items").get(0);
        assertEquals(report.getId().toString(), d.path("id").asText());
        assertEquals(today.plusDays(7).toString(), d.path("dueDate").asText());
        assertEquals("TASKS_IN_PROGRESS", d.path("stage").asText(), "필수 요구사항은 확인됐고 연결 업무 하나가 안 끝났다");
        assertEquals(Map.of("문제 정의를 적는다", true, "분량은 10쪽 안팎", false), stream(d.path("requirements"))
                .collect(Collectors.toMap(r -> r.path("content").asText(), r -> r.path("met").asBoolean())));

        JsonNode progress = call(tools, GetProgressTool.NAME, "{\"deliverableId\":\"" + report.getId() + "\"}");
        assertEquals(2, progress.path("tasks").path("total").asInt());
        assertEquals(1, progress.path("tasks").path("completed").asInt());
        assertEquals(1, progress.path("requiredRequirements").path("met").asInt());

        JsonNode tasks = call(tools, ListTasksTool.NAME, "{}");
        assertEquals(3, tasks.path("total").asInt());
        Map<String, JsonNode> byTitle = stream(tasks.path("items"))
                .collect(Collectors.toMap(t -> t.path("title").asText(), Function.identity()));
        JsonNode late = byTitle.get("문제 정의 초안");
        assertTrue(late.path("overdue").asBoolean(), "끝나지 않았고 기한이 어제");
        assertEquals("이팀원", late.path("assignees").get(0).asText());
        assertEquals("중간 보고서", late.path("deliverableTitle").asText());
        assertEquals(metRequirement.getId().toString(), late.path("requirementId").asText());
        assertEquals("한 쪽으로 정리", late.path("completionCriteria").asText());
        assertFalse(byTitle.get("오늘 마감 업무").path("overdue").asBoolean(), "기한이 오늘이면 늦지 않았다");
        assertFalse(byTitle.get("끝난 업무").path("overdue").asBoolean(), "끝난 업무는 늦지 않았다");

        JsonNode alerts = call(tools, ListAlertsTool.NAME, "{}");
        assertEquals("DROPOUT", alerts.path("items").get(0).path("type").asText());
        assertEquals("HIGH", alerts.path("items").get(0).path("severity").asText());

        JsonNode scores = call(tools, GetScoresTool.NAME, "{}");
        assertEquals("이팀원", scores.path("items").get(0).path("name").asText());
        assertEquals("PARTIAL", scores.path("items").get(0).path("participationLevel").asText());
    }

    @Test void noToolSendsAnEmailToTheModel() {
        Map<String, AgentTool> tools = bind(leader);
        for (String name : ALL) {
            String out = tools.get(name).call("{\"deliverableId\":\"" + report.getId() + "\"}");
            assertFalse(out.contains("error"), name + ": " + out);
            assertFalse(out.contains("@") || out.contains("email"), name + ": " + out);
        }
    }

    @Test void toolsRunWithThePermissionOfTheRequester() throws Exception {
        // 참여자가 아닌 사람으로 묶인 도구는 기록을 돌려주지 않는다
        for (AgentTool tool : bind(outsider).values()) {
            JsonNode out = json.readTree(tool.call("{\"deliverableId\":\"" + report.getId() + "\"}"));
            assertTrue(out.has("error"), tool.name() + ": " + out);
            assertEquals(1, out.size(), "이유 말고는 아무것도 없다");
        }
    }

    @Test void aWrongCallComesBackAsAReasonTheModelCanRead() throws Exception {
        Map<String, AgentTool> tools = bind(leader);
        Project other = project("다른 프로젝트", outsider);
        join(other, outsider, "LEADER");
        Deliverable secret = deliverable(other, "남의 제출물", today);
        em.flush();

        JsonNode foreign = call(tools, GetProgressTool.NAME, "{\"deliverableId\":\"" + secret.getId() + "\"}");
        assertTrue(foreign.has("error"));
        assertFalse(foreign.toString().contains("남의 제출물"), "다른 프로젝트의 제목을 드러내지 않는다");
        assertTrue(call(tools, GetProgressTool.NAME, "{}").has("error"));
        assertTrue(call(tools, GetProgressTool.NAME, "{\"deliverableId\":\"abc\"}").has("error"));
        assertTrue(call(tools, GetProgressTool.NAME, "{잘못된 JSON").has("error"));
    }

    @Test void toolsAreFoundByNameAndNamesMustBeUnique() {
        assertEquals(ALL, toolbox.bind(project.getId(), leader, ALL).stream().map(AgentTool::name).toList());
        assertThrows(IllegalArgumentException.class, () -> toolbox.bind(project.getId(), leader, List.of("delete_project")));

        ProjectTool one = named("same", List.of()), two = named("same", List.of());
        assertThrows(IllegalStateException.class, () -> new AgentToolbox(List.of(one, two), json, properties));
    }

    @Test void aLongListIsCutAndSaysSo() throws Exception {
        // 상한은 도구가 아니라 도구함이 건다. 목록을 돌려주는 도구는 모두 같은 모양으로 나간다
        AgentProperties two = new AgentProperties(); two.setToolListLimit(2);
        ProjectTool numbers = named("numbers", List.of(1, 2, 3));
        AgentTool tool = new AgentToolbox(List.of(numbers), json, two).bind(project.getId(), leader, List.of("numbers")).get(0);

        JsonNode out = json.readTree(tool.call("{}"));
        assertEquals(List.of(1, 2), stream(out.path("items")).map(JsonNode::asInt).toList());
        assertEquals(3, out.path("total").asInt());
        assertTrue(out.path("truncated").asBoolean());
    }

    @Test void anUnexpectedFailureIsNotHandedToTheModel() {
        // 인자나 권한의 문제가 아닌 실패는 이유를 모델에 넘기지 않고 실행을 끝낸다
        ProjectTool broken = new ProjectTool() {
            @Override public String name() { return "broken"; }
            @Override public String label() { return "broken"; }
            @Override public String description() { return "broken"; }
            @Override public Object read(UUID projectId, User user, JsonNode arguments) { throw new IllegalStateException("내부 사정"); }
        };
        AgentTool tool = new AgentToolbox(List.of(broken), json, properties).bind(project.getId(), leader, List.of("broken")).get(0);

        assertThrows(IllegalStateException.class, () -> tool.call("{}"));
    }

    private Map<String, AgentTool> bind(User user) {
        return toolbox.bind(project.getId(), user, ALL).stream().collect(Collectors.toMap(AgentTool::name, Function.identity()));
    }

    private JsonNode call(Map<String, AgentTool> tools, String name, String arguments) throws Exception {
        return json.readTree(tools.get(name).call(arguments));
    }

    private static java.util.stream.Stream<JsonNode> stream(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false);
    }

    private static ProjectTool named(String name, Object result) {
        return new ProjectTool() {
            @Override public String name() { return name; }
            @Override public String label() { return name; }
            @Override public String description() { return name; }
            @Override public Object read(UUID projectId, User user, JsonNode arguments) { return result; }
        };
    }

    private User user(String name) {
        User user = new User(); user.setName(name); user.setEmail(UUID.randomUUID() + "@agent.example.invalid");
        user.setPasswordHash("test-only-not-used-for-authentication"); user.setRole("STUDENT");
        em.persist(user); return user;
    }

    private Project project(String name, User creator) {
        Project p = new Project(); p.setName(name); p.setCreatedBy(creator); em.persist(p); return p;
    }

    private void join(Project p, User user, String role) {
        ProjectMember m = new ProjectMember(); m.setProject(p); m.setUser(user); m.setRole(role); em.persist(m);
    }

    private Deliverable deliverable(Project p, String title, LocalDate dueDate) {
        Deliverable d = new Deliverable(); d.setProject(p); d.setTitle(title); d.setDueDate(dueDate); em.persist(d); return d;
    }

    private DeliverableRequirement requirement(Deliverable d, String content, boolean required) {
        DeliverableRequirement r = new DeliverableRequirement(); r.setDeliverable(d); r.setContent(content);
        r.setRequired(required); em.persist(r); return r;
    }

    private Task task(String title, String status, LocalDate dueDate) {
        Task t = new Task(); t.setProject(project); t.setCreatedBy(leader); t.setTitle(title); t.setStatus(status);
        t.setPriority("MEDIUM"); t.setDueDate(dueDate); em.persist(t); return t;
    }

    private void assign(Task task, User user) {
        TaskAssignee a = new TaskAssignee(); a.setTask(task); a.setUser(user); em.persist(a);
    }
}
