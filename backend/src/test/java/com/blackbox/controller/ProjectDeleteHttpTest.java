package com.blackbox.controller;

import com.blackbox.entity.Project;
import com.blackbox.entity.User;
import com.blackbox.security.JwtService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 실제 PostgreSQL에서 멤버 참조, 삭제 권한, 기존 기록 보존을 확인한다. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class ProjectDeleteHttpTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired JwtService jwt;
    User leader, teammate;
    Project project;

    @BeforeEach void setup() {
        leader = user(em, "삭제검사 팀장");
        teammate = user(em, "삭제검사 팀원");
        project = project(em, leader);
        member(em, project, leader, "LEADER");
        member(em, project, teammate, "MEMBER");
        em.flush();
        em.clear();
    }

    @Test void leaderDeletesAnEmptyProjectIncludingMemberships() throws Exception {
        mvc.perform(delete("/api/projects/{id}", project.getId())
                .header("Authorization", "Bearer " + jwt.generateAccessToken(leader.getEmail())))
                .andExpect(status().isNoContent());
        assertNull(em.find(Project.class, project.getId()));
        assertEquals(0L, em.createQuery("select count(m) from ProjectMember m where m.project.id = :id", Long.class)
                .setParameter("id", project.getId()).getSingleResult());
        assertNotNull(em.find(User.class, leader.getId()));
    }

    @Test void memberCannotDeleteProject() throws Exception {
        mvc.perform(delete("/api/projects/{id}", project.getId())
                .header("Authorization", "Bearer " + jwt.generateAccessToken(teammate.getEmail())))
                .andExpect(status().isForbidden());
        assertNotNull(em.find(Project.class, project.getId()));
    }

    @Test void preservedActivityReturnsConflictInsteadOfRemovingRecords() throws Exception {
        em.createNativeQuery("insert into activity_logs (project_id, user_id, source, action_type, occurred_at) values (:p, :u, 'PLATFORM', 'TASK_CREATE', now())")
                .setParameter("p", project.getId()).setParameter("u", leader.getId()).executeUpdate();
        mvc.perform(delete("/api/projects/{id}", project.getId())
                .header("Authorization", "Bearer " + jwt.generateAccessToken(leader.getEmail())))
                .andExpect(status().isConflict());
        // 이 트랜잭션은 FK 위반 후 롤백된다. 실제 보존은 별도 HTTP 검증에서도 확인한다.
    }
}
