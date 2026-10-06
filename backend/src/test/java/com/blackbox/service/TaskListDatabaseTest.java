package com.blackbox.service;

import com.blackbox.entity.*;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 실제 연관을 로딩한 응답까지 검사한다. 저장소 mock은 N+1의 실행 증거가 될 수 없다. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TaskService.class, ProjectAccessChecker.class})
class TaskListDatabaseTest {
    @Autowired EntityManager em;
    @Autowired TaskService service;
    @MockBean ActivityLogService activity;
    @MockBean NotionService notion;
    @MockBean AlertService alerts;
    @MockBean DiscordNotificationService discord;
    @MockBean ScoreService scores;
    @MockBean DeliverableService deliverables;

    @Test void listLoadsDistinctDeliverablesRequirementsAndAssigneesWithFourQueries() {
        User leader = user("leader");
        Project project = new Project(); project.setName("B13 query regression"); project.setCreatedBy(leader);
        em.persist(project);
        ProjectMember member = new ProjectMember(); member.setProject(project); member.setUser(leader);
        member.setRole("LEADER"); em.persist(member);
        for (int i = 0; i < 8; i++) {
            Deliverable delivery = new Deliverable(); delivery.setProject(project);
            delivery.setTitle("deliverable-" + i); delivery.setDueDate(LocalDate.of(2026, 10, 12)); em.persist(delivery);
            DeliverableRequirement requirement = new DeliverableRequirement(); requirement.setDeliverable(delivery);
            requirement.setContent("requirement-" + i); requirement.setRequired(true); em.persist(requirement);
            Task task = new Task(); task.setProject(project); task.setCreatedBy(leader); task.setTitle("task-" + i);
            task.setStatus(i % 2 == 0 ? "DONE" : "TODO"); task.setPriority("MEDIUM");
            task.setDeliverable(delivery); task.setRequirement(requirement); em.persist(task);
            if (i != 7) {
                assign(task, leader);
                assign(task, user("candidate-" + i));
            }
        }
        em.flush();
        // 준비 데이터의 영속성 캐시가 지연 조회 문제를 가리지 않도록 비운 뒤 측정한다.
        em.clear();
        var statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var result = service.listTasks(project.getId(), null, leader);
        assertEquals(8, result.size());
        assertEquals(8, result.stream().map(r -> r.id()).distinct().count());
        assertEquals(1, result.stream().filter(r -> r.assignees().isEmpty()).count());
        assertTrue(result.stream().allMatch(r -> r.deliverableTitle().startsWith("deliverable-")
                && r.requirementContent().startsWith("requirement-")));
        assertTrue(result.stream().filter(r -> !r.assignees().isEmpty()).allMatch(r -> r.assignees().size() == 2));
        assertEquals(4, statistics.getPrepareStatementCount(), "프로젝트·멤버·업무·담당자 조회 각 1회");

        em.clear(); statistics.clear();
        var completed = service.listTasks(project.getId(), "DONE", leader);
        assertEquals(4, completed.size());
        assertTrue(completed.stream().allMatch(r -> "DONE".equals(r.status())));
        assertEquals(4, statistics.getPrepareStatementCount());
    }

    private User user(String label) {
        User user = new User(); user.setName(label);
        user.setEmail(UUID.randomUUID() + "@b13.example.invalid");
        user.setPasswordHash("test-only-not-used-for-authentication"); user.setRole("STUDENT");
        em.persist(user); return user;
    }

    private void assign(Task task, User user) {
        TaskAssignee assignment = new TaskAssignee(); assignment.setTask(task); assignment.setUser(user);
        em.persist(assignment);
    }
}
