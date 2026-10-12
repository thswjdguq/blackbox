package com.blackbox.controller;

import com.blackbox.entity.Project;
import com.blackbox.entity.User;
import com.blackbox.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

/**
 * 프로젝트 삭제를 실제 DB와 HTTP 요청으로 확인한다. 테스트마다 트랜잭션 하나로 묶여 끝나면 되돌아간다.
 * 저장소를 가짜로 두면 Hibernate가 삭제를 거부하는 것(모든 삭제가 500)을 잡지 못한다.
 * 쉬는 연결을 쥐지 않게 한다. 서버를 띄우는 테스트마다 연결을 10개씩 쥐면 검사용 DB의 한도(100)를 넘는다.
 */
@SpringBootTest(properties = "spring.datasource.hikari.minimum-idle=0") @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class ProjectDeleteHttpTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EntityManager em;
    @Autowired JwtService jwt;

    User leader, member, outsider;
    Project project;

    @BeforeEach void setup() {
        leader = user(em, "송팀장"); member = user(em, "손팀원"); outsider = user(em, "외부인");
        project = project(em, leader);
        member(em, project, leader, "LEADER"); member(em, project, member, "MEMBER");
    }

    @Test void leaderDeletesProjectWithItsMembersAndDeliverables() throws Exception {
        requirement(em, deliverable(em, project, "중간 보고서"), true, null);

        assertEquals("", send(leader, project.getId(), 204));

        assertNull(em.find(Project.class, project.getId()));
        for (String table : new String[]{"project_members", "deliverables"}) {
            assertEquals(0L, em.createNativeQuery("select count(*) from " + table + " where project_id = :id")
                    .setParameter("id", project.getId()).getSingleResult(), table);
        }
    }

    /** 무결성 위반 뒤에는 이 트랜잭션으로 더 조회할 수 없어 응답만 본다. 실제 요청에서는 트랜잭션이 되돌아가 프로젝트가 남는다. */
    @Test void projectWithFileIsRejectedWithGuidance() throws Exception {
        file(em, project, member, "중간보고서.pdf", 1, 'a');

        String body = send(leader, project.getId(), 409);

        assertEquals("기록 보존을 위해 이 프로젝트는 삭제할 수 없습니다", json.readTree(body).get("detail").asText());
    }

    @Test void onlyLeaderCanDelete() throws Exception {
        send(member, project.getId(), 403);
        send(outsider, project.getId(), 403);
        send(null, project.getId(), 401);

        assertNotNull(em.find(Project.class, project.getId()));
    }

    @Test void unknownProjectIsNotFound() throws Exception {
        send(leader, UUID.randomUUID(), 404);
    }

    /** 서버가 DB에서 새로 읽도록 영속성 컨텍스트를 비우고 보낸다. as가 null이면 로그인하지 않은 요청이다. */
    private String send(User as, UUID projectId, int expectedStatus) throws Exception {
        em.flush(); em.clear();
        var request = delete("/api/projects/{projectId}", projectId);
        if (as != null) request.header("Authorization", "Bearer " + jwt.generateAccessToken(as.getEmail()));
        var response = mvc.perform(request).andReturn().getResponse();
        response.setCharacterEncoding("UTF-8");
        assertEquals(expectedStatus, response.getStatus(), response.getContentAsString());
        em.clear();
        return response.getContentAsString();
    }
}
