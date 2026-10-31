package com.blackbox.service;

import com.blackbox.entity.*;
import com.blackbox.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** 동명이인 요청자/검토자를 실제 권한·HTTP JSON 응답으로 구분한다. 자료는 롤백한다. */
@SpringBootTest(properties = "spring.datasource.hikari.minimum-idle=0")
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class TaskRequesterIdentityHttpTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper json;

    @Test void requesterIdSurvivesReadsAndClearsWhenRequestEnds() throws Exception {
        User requester = user(em, "김민지"), reviewer = user(em, "김민지");
        Project project = project(em, requester);
        member(em, project, requester, "LEADER"); member(em, project, reviewer, "MEMBER");
        Task task = new Task(); task.setProject(project); task.setCreatedBy(requester);
        task.setTitle("동명이인 결과 확인"); task.setStatus("TODO"); task.setPriority("MEDIUM"); em.persist(task);
        em.flush();
        String base = "/api/projects/" + project.getId() + "/tasks";
        String path = base + "/" + task.getId();
        assertTrue(call(get(path), requester).path("completionRequestedBy").isNull());
        JsonNode requested = call(post(path + "/completion-request"), requester);
        assertEquals(requester.getId().toString(), requested.path("completionRequestedBy").asText());
        assertEquals("김민지", requested.path("completionRequestedByName").asText());
        assertEquals(requester.getId().toString(), call(get(path), reviewer).path("completionRequestedBy").asText());
        assertEquals(requester.getId().toString(), call(get(base), reviewer).get(0).path("completionRequestedBy").asText());
        JsonNode completion = call(get(path + "/completion"), reviewer);
        assertTrue(completion.path("canReview").asBoolean(), "동명이인인 다른 팀원도 확인할 수 있다");
        assertEquals(requester.getId().toString(), completion.path("task").path("completionRequestedBy").asText());
        assertTrue(call(post(path + "/completion-cancel").content("{\"reason\":\"수정 후 재요청\"}"), requester)
                .path("completionRequestedBy").isNull());
        call(post(path + "/completion-request"), requester);
        assertTrue(call(put(path + "/completion-decision").content("{\"approved\":false,\"reason\":\"자료 보완\"}"), reviewer)
                .path("completionRequestedBy").isNull());
        call(post(path + "/completion-request"), requester);
        JsonNode done = call(put(path + "/completion-decision").content("{\"approved\":true,\"criteriaConfirmed\":true}"), reviewer);
        assertEquals("DONE", done.path("status").asText());
        assertTrue(done.path("completionRequestedBy").isNull());
    }

    private JsonNode call(MockHttpServletRequestBuilder request, User actor) throws Exception {
        em.flush(); em.clear();
        var response = mvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + jwt.generateAccessToken(actor.getEmail())))
                .andReturn().getResponse();
        assertEquals(200, response.getStatus(), response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        return json.readTree(response.getContentAsByteArray());
    }
}
