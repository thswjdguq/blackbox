package com.blackbox.agent;

import com.blackbox.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 트랜잭션을 스스로 나누는 코드(실행기, HTTP)를 실제 DB로 시험할 때 쓰는 준비 자료.
 * 테스트 트랜잭션으로 되돌릴 수 없으므로 만든 것을 cleanup에서 지운다.
 */
public class AgentTestData {
    private static final String EMAIL_DOMAIN = "@agent-run.example.invalid";

    private final EntityManager em;
    private final TransactionTemplate tx;
    private final JdbcTemplate jdbc;

    public AgentTestData(EntityManager em, PlatformTransactionManager transactions, JdbcTemplate jdbc) {
        this.em = em; this.tx = new TransactionTemplate(transactions); this.jdbc = jdbc;
        cleanup();
    }

    public <T> T inTransaction(Supplier<T> work) { return tx.execute(status -> work.get()); }

    public User user(String name) {
        return inTransaction(() -> {
            User user = new User(); user.setName(name); user.setEmail(UUID.randomUUID() + EMAIL_DOMAIN);
            user.setPasswordHash("test-only-not-used-for-authentication"); user.setRole("STUDENT");
            em.persist(user); return user;
        });
    }

    public Project project(String name, User leader) {
        Project project = inTransaction(() -> {
            Project p = new Project(); p.setName(name); p.setCreatedBy(leader); em.persist(p); return p;
        });
        join(project, leader, "LEADER");
        return project;
    }

    public void join(Project project, User user, String role) {
        inTransaction(() -> {
            ProjectMember m = new ProjectMember(); m.setProject(project); m.setUser(user); m.setRole(role);
            em.persist(m); return m;
        });
    }

    public Deliverable deliverable(Project project, String title, LocalDate dueDate) {
        return inTransaction(() -> {
            Deliverable d = new Deliverable(); d.setProject(project); d.setTitle(title); d.setDueDate(dueDate);
            em.persist(d); return d;
        });
    }

    public DeliverableRequirement requirement(Deliverable deliverable, String content, boolean required) {
        return inTransaction(() -> {
            DeliverableRequirement r = new DeliverableRequirement(); r.setDeliverable(deliverable); r.setContent(content);
            r.setRequired(required); em.persist(r); return r;
        });
    }

    /** 끝난 실행 하나와 거기서 나온 대기 중인 카드. 검증을 거치지 않고 넣으므로 형식에 맞지 않는 내용도 넣을 수 있다 */
    public AgentProposal card(Project project, User requestedBy, String kind, String title, String contentJson) {
        return inTransaction(() -> {
            try {
                AgentRun run = AgentRun.start(project, requestedBy, "TEST", OffsetDateTime.now());
                run.finish(OffsetDateTime.now());
                em.persist(run);
                AgentProposal card = AgentProposal.propose(run, kind, title, "테스트 근거", new ObjectMapper().readTree(contentJson), OffsetDateTime.now());
                em.persist(card);
                return card;
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalArgumentException(e);
            }
        });
    }

    /** 이 도우미가 만든 사용자의 프로젝트와 거기 딸린 것을 모두 지운다. 중간에 죽은 실행이 남긴 것도 함께 지워진다 */
    public void cleanup() {
        String like = "%" + EMAIL_DOMAIN;
        String projects = "(SELECT p.id FROM projects p JOIN users u ON u.id = p.created_by WHERE u.email LIKE ?)";
        jdbc.update("DELETE FROM agent_proposal_results WHERE proposal_id IN (SELECT id FROM agent_proposals WHERE project_id IN " + projects + ")", like);
        // 점수 재계산 일정(ScoreScheduler)이 테스트 중에 돌면 점수와 경보가 생긴다. 이 표들은 프로젝트를 지워도 따라 지워지지 않는다
        for (String table : List.of("agent_proposals", "agent_runs", "activity_logs", "contribution_scores", "alerts")) {
            jdbc.update("DELETE FROM " + table + " WHERE project_id IN " + projects, like);
        }
        jdbc.update("DELETE FROM projects WHERE id IN " + projects, like);
        jdbc.update("DELETE FROM users WHERE email LIKE ?", like);
    }
}
