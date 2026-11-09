package com.blackbox.controller;

import com.blackbox.entity.*;
import com.blackbox.scheduler.ScoreScheduler;
import com.blackbox.security.JwtService;
import com.blackbox.service.AlertService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;
import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** 실제 DB 행의 생성/보존과 인증된 재계산 API를 함께 검사한다. 합성 자료는 롤백한다. */
@SpringBootTest(properties = "spring.datasource.hikari.minimum-idle=0")
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class ScoreContributorHttpTest {
    @Autowired EntityManager em;
    @Autowired JwtService jwt;
    @Autowired MockMvc mvc;
    @MockitoBean ScoreScheduler scheduler;
    @MockitoBean AlertService alerts;

    @Test void newObserversHaveNoStoredScoreAndCannotRequestRecalculation() throws Exception {
        var leader = user(em, "합성 팀장"); var contributor = user(em, "합성 팀원"); var observer = user(em, "합성 관찰자");
        var p = project(em, leader); member(em, p, leader, "LEADER"); member(em, p, contributor, "MEMBER"); member(em, p, observer, "OBSERVER");
        var ids = recalculate(p, leader); assertEquals(Set.of(leader.getId(), contributor.getId()), ids);
        assertEquals(0L, count(p, observer)); assertEquals(1L, count(p, leader)); assertEquals(1L, count(p, contributor));
        assertEquals(403, mvc.perform(post(path(p)).header("Authorization", token(observer))).andReturn().getResponse().getStatus());
        assertEquals(0L, count(p, observer));
    }
    @Test void legacyObserverRowIsPreservedAndReusedOnlyAfterRoleChange() throws Exception {
        var leader = user(em, "합성 팀장"); var observer = user(em, "합성 관찰자"); var p = project(em, leader);
        member(em, p, leader, "LEADER"); member(em, p, observer, "OBSERVER");
        var membership = em.createQuery("select m from ProjectMember m where m.project=:p and m.user=:u", ProjectMember.class)
                .setParameter("p", p).setParameter("u", observer).getSingleResult();
        var old = new ContributionScore(); old.setProject(p); old.setUser(observer); old.setTaskParticipated(true);
        var oldTime = OffsetDateTime.parse("2026-10-01T00:00:00Z"); old.setCalculatedAt(oldTime); em.persist(old); em.flush();
        UUID originalId = old.getId();
        assertFalse(recalculate(p, leader).contains(observer.getId())); em.refresh(old);
        assertEquals(originalId, old.getId()); assertEquals(oldTime.toInstant(), old.getCalculatedAt().toInstant()); assertTrue(old.isTaskParticipated());
        membership.setRole("MEMBER"); em.flush(); assertTrue(recalculate(p, leader).contains(observer.getId())); em.refresh(old);
        assertEquals(originalId, old.getId()); assertEquals(1L, count(p, observer)); assertFalse(old.isTaskParticipated());
        var latest = old.getCalculatedAt(); membership.setRole("OBSERVER"); em.flush();
        assertFalse(recalculate(p, leader).contains(observer.getId())); em.refresh(old);
        assertEquals(latest.toInstant(), old.getCalculatedAt().toInstant()); assertEquals(1L, count(p, observer));
    }
    private Set<UUID> recalculate(Project project, User actor) throws Exception {
        em.flush();
        var response = mvc.perform(post(path(project)).header("Authorization", token(actor))).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        // MVC도 테스트의 바깥 트랜잭션에 참여한다. refresh 전에 실제 요청 커밋에 해당하는 flush를 해야 갱신을 버리지 않는다.
        em.flush();
        var json = new tools.jackson.databind.ObjectMapper().readTree(response.getContentAsByteArray());
        Set<UUID> ids = new HashSet<>(); json.forEach(r -> ids.add(UUID.fromString(r.path("userId").asString()))); return ids;
    }
    private long count(Project p, User user) {
        return em.createQuery("select count(s) from ContributionScore s where s.project=:p and s.user=:u", Long.class)
                .setParameter("p", p).setParameter("u", user).getSingleResult();
    }
    private String token(User user) { return "Bearer " + jwt.generateAccessToken(user.getEmail()); }
    private String path(Project p) { return "/api/projects/" + p.getId() + "/scores/recalculate"; }
}
