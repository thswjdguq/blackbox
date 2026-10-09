package com.blackbox.agent;

import com.blackbox.entity.*;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 트랜잭션을 스스로 나누는 코드(실행기, HTTP)를 실제 DB로 시험할 때 쓰는 준비 자료.
 * 테스트 트랜잭션으로 되돌릴 수 없으므로 만든 것을 기억했다가 cleanup에서 지운다.
 */
public class AgentTestData {
    private static final String EMAIL_DOMAIN = "@agent-run.example.invalid";

    private final EntityManager em;
    private final TransactionTemplate tx;
    private final JdbcTemplate jdbc;
    private final List<UUID> projectIds = new ArrayList<>();

    public AgentTestData(EntityManager em, PlatformTransactionManager transactions, JdbcTemplate jdbc) {
        this.em = em; this.tx = new TransactionTemplate(transactions); this.jdbc = jdbc;
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
        projectIds.add(project.getId());
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

    public void cleanup() {
        for (UUID id : projectIds) {
            jdbc.update("DELETE FROM agent_proposal_results WHERE proposal_id IN (SELECT id FROM agent_proposals WHERE project_id = ?)", id);
            jdbc.update("DELETE FROM agent_proposals WHERE project_id = ?", id);
            jdbc.update("DELETE FROM agent_runs WHERE project_id = ?", id);
            jdbc.update("DELETE FROM activity_logs WHERE project_id = ?", id);
            jdbc.update("DELETE FROM projects WHERE id = ?", id);
        }
        jdbc.update("DELETE FROM users WHERE email LIKE ?", "%" + EMAIL_DOMAIN);
        projectIds.clear();
    }
}
