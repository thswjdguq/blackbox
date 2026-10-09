package com.blackbox.agent;

import com.blackbox.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** V23의 테이블과 엔티티가 실제 DB에서 맞물리는지, 제약이 잘못된 줄을 거절하는지 본다. */
// 목록 조회가 묶음(채택 결과)을 함께 끌어오면 Hibernate가 건수 제한을 DB가 아니라 메모리에서 건다. 그런 조회는 실패하게 해서 막는다
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AgentProposalRepositoryTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-10T14:00:00+09:00");

    @Autowired EntityManager em;
    @Autowired AgentRunRepository runs;
    @Autowired AgentProposalRepository proposals;
    private final ObjectMapper json = new ObjectMapper();

    @Test void contentRoundTripsAndOriginalStaysWhenContentIsEdited() throws Exception {
        User leader = user("leader"); Project project = project(leader);
        AgentProposal saved = proposal(run(project, leader, NOW), "{\"tasks\":[{\"title\":\"초안 작성\"}]}", NOW);
        em.flush(); em.clear();

        AgentProposal loaded = proposals.findByIdAndProject(saved.getId(), project).orElseThrow();
        assertEquals("초안 작성", loaded.getContent().at("/tasks/0/title").asText());
        assertFalse(loaded.isEdited());
        loaded.edit(json.readTree("{\"tasks\":[{\"title\":\"고친 제목\"}]}"));
        em.flush(); em.clear();

        AgentProposal again = proposals.findByIdAndProject(saved.getId(), project).orElseThrow();
        assertEquals("고친 제목", again.getContent().at("/tasks/0/title").asText());
        assertEquals("초안 작성", again.getOriginal().at("/tasks/0/title").asText());
        assertTrue(again.isEdited());
    }

    @Test void resultsAreStoredAndFoundByTarget() throws Exception {
        User leader = user("leader"); Project project = project(leader); Project other = project(leader);
        AgentProposal p = proposal(run(project, leader, NOW), "{}", NOW);
        UUID taskId = UUID.randomUUID();
        p.accept(leader, List.of(new AgentProposal.Result("TASK", taskId)), NOW);
        em.flush(); em.clear();

        assertEquals(1, proposals.findByIdAndProject(p.getId(), project).orElseThrow().getResults().size());
        assertTrue(proposals.existsResult(project.getId(), "TASK", taskId));
        assertFalse(proposals.existsResult(project.getId(), "DELIVERABLE", taskId));
        assertFalse(proposals.existsResult(other.getId(), "TASK", taskId));
        assertEquals(1, proposals.countByProjectAndStatus(project, AgentProposal.Status.ACCEPTED));
        assertEquals(0, proposals.countByProjectAndStatus(project, AgentProposal.Status.PENDING));
        assertTrue(proposals.findByIdAndProject(p.getId(), other).isEmpty());   // 다른 프로젝트 경로로는 찾지 못한다
    }

    @Test void acceptsATargetTypeAddedLater() throws Exception {
        // 대상 종류는 카드 종류와 함께 늘어난다. 새 종류를 넣을 때 DB를 고치지 않아도 되어야 한다
        User leader = user("leader"); Project project = project(leader);
        AgentProposal p = proposal(run(project, leader, NOW), "{}", NOW);
        UUID meetingId = UUID.randomUUID();
        String longName = "MEETING_AGENDA_ITEM_FROM_REVIEW_FEEDBACK";   // 40자. 이름이 길어져도 들어가야 한다
        p.accept(leader, List.of(new AgentProposal.Result("MEETING", meetingId), new AgentProposal.Result(longName, meetingId)), NOW);
        em.flush(); em.clear();

        assertTrue(proposals.existsResult(project.getId(), "MEETING", meetingId));
        assertTrue(proposals.existsResult(project.getId(), longName, meetingId));
    }

    @Test void listsNewestFirstWithinLimitAndFiltersByStatus() throws Exception {
        User leader = user("leader"); Project project = project(leader); AgentRun run = run(project, leader, NOW);
        AgentProposal oldest = proposal(run, "{}", NOW.minusHours(2));
        AgentProposal old = proposal(run, "{}", NOW.minusHours(1));
        AgentProposal recent = proposal(run, "{}", NOW);
        recent.reject(leader, NOW);
        em.flush(); em.clear();

        assertEquals(List.of(recent.getId(), old.getId(), oldest.getId()), ids(proposals.findByProjectOrderByCreatedAtDesc(project, Limit.of(50))));
        assertEquals(List.of(recent.getId(), old.getId()), ids(proposals.findByProjectOrderByCreatedAtDesc(project, Limit.of(2))));
        assertEquals(List.of(old.getId(), oldest.getId()),
                ids(proposals.findByProjectAndStatusOrderByCreatedAtDesc(project, AgentProposal.Status.PENDING, Limit.of(50))));
        assertTrue(proposals.lockByIdAndProject(old.getId(), project).isPresent());
    }

    @Test void listReadsResultsOfAllCardsInOneExtraQuery() throws Exception {
        User leader = user("leader"); Project project = project(leader); AgentRun run = run(project, leader, NOW);
        for (int i = 0; i < 5; i++) {
            proposal(run, "{}", NOW).accept(leader, List.of(new AgentProposal.Result("TASK", UUID.randomUUID())), NOW);
        }
        em.flush(); em.clear();
        var statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        List<AgentProposal> list = proposals.findByProjectOrderByCreatedAtDesc(project, Limit.of(50));
        assertEquals(5, list.stream().mapToInt(p -> p.getResults().size()).sum());
        // 카드 목록 1번 + 결과 묶음 1번. 카드마다 따로 읽으면 6번이 된다
        assertEquals(2, statistics.getPrepareStatementCount());
    }

    @Test void runningCheckIgnoresOldRunsFinishedRunsAndOtherUsers() {
        User leader = user("leader"); User member = user("member"); User third = user("third"); Project project = project(leader);
        run(project, leader, NOW.minusMinutes(30));          // 시간 상한을 넘겨 버려진 실행
        run(project, member, NOW.minusMinutes(1));           // 도는 중
        run(project, third, NOW.minusMinutes(1)).finish(NOW); // 끝난 실행
        em.flush();

        OffsetDateTime since = NOW.minusMinutes(5);
        assertFalse(runs.hasRunning(project, leader, since));
        assertTrue(runs.hasRunning(project, member, since));
        assertFalse(runs.hasRunning(project, third, since));
        assertEquals(3, runs.countByProjectAndStartedAtAfter(project, NOW.minusHours(1)));
        assertEquals(2, runs.countByProjectAndStartedAtAfter(project, since));
    }

    @Test void databaseRejectsInconsistentRows() throws Exception {
        User leader = user("leader"); Project project = project(leader); Project other = project(leader);
        AgentRun run = run(project, leader, NOW); AgentProposal p = proposal(run, "{}", NOW);
        em.flush();

        rejected("끝난 실행에 끝난 시각이 없음", "update agent_runs set status = 'DONE' where id = ?", run.getId());
        rejected("모르는 실행 상태", "update agent_runs set status = 'PAUSED', finished_at = now() where id = ?", run.getId());
        rejected("결정한 사람 없이 채택", "update agent_proposals set status = 'ACCEPTED', decided_at = now() where id = ?", p.getId());
        rejected("대기 중인데 결정 시각이 있음", "update agent_proposals set decided_at = now(), decided_by = '" + leader.getId() + "' where id = ?", p.getId());
        rejected("카드를 다른 프로젝트로 옮김", "update agent_proposals set project_id = '" + other.getId() + "' where id = ?", p.getId());
    }

    /** 제약 위반은 트랜잭션을 못 쓰게 만들므로 저장점으로 감싸 한 건씩 확인한다. */
    private void rejected(String what, String sql, UUID id) {
        em.createNativeQuery("savepoint before_check").executeUpdate();
        assertThrows(PersistenceException.class, () -> em.createNativeQuery(sql).setParameter(1, id).executeUpdate(), what);
        em.createNativeQuery("rollback to savepoint before_check").executeUpdate();
    }

    private static List<UUID> ids(List<AgentProposal> list) { return list.stream().map(AgentProposal::getId).toList(); }

    private AgentRun run(Project project, User user, OffsetDateTime startedAt) {
        AgentRun r = AgentRun.start(project, user, "PLAN_FROM_BRIEF", startedAt);
        em.persist(r); return r;
    }

    private AgentProposal proposal(AgentRun run, String content, OffsetDateTime createdAt) throws Exception {
        AgentProposal p = AgentProposal.propose(run, "TASKS", "업무 제안", "요구사항에서 뽑았습니다", json.readTree(content), createdAt);
        em.persist(p); return p;
    }

    private Project project(User leader) {
        Project p = new Project(); p.setName("agent repository test"); p.setCreatedBy(leader); em.persist(p); return p;
    }

    private User user(String name) {
        User u = new User(); u.setEmail(name + "-" + UUID.randomUUID() + "@example.com"); u.setName(name);
        u.setPasswordHash("x"); u.setRole("STUDENT"); em.persist(u); return u;
    }
}
