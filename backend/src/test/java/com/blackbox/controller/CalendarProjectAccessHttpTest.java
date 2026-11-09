package com.blackbox.controller;

import com.blackbox.entity.*;
import com.blackbox.security.JwtService;
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
import java.util.UUID;
import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** 실제 PostgreSQL 멤버/프로젝트와 인증 필터를 사용하는 MVC HTTP 검사. 모든 합성 자료는 롤백한다. */
@SpringBootTest(properties = "spring.datasource.hikari.minimum-idle=0")
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class CalendarProjectAccessHttpTest {
    @Autowired EntityManager em;
    @Autowired JwtService jwt;
    @Autowired MockMvc mvc;

    @Test void outsidersCannotReadTeamIdentityOrSchedulesThroughAnyAlias() throws Exception {
        var leader = user(em, "합성 팀장"); var outsider = user(em, "합성 외부 사용자");
        var p = project(em, leader); member(em, p, leader, "LEADER"); em.flush();
        for (String suffix : new String[]{"/calendar/status", "/schedule/team-status", "/calendar/availability?date=2026-11-10"}) {
            assertEquals(403, call(get("/api/projects/" + p.getId() + suffix), outsider));
        }
        assertEquals(403, call(post("/api/calendar/recommend").content(recommend(p.getId(), leader.getId())), outsider));
        assertEquals(403, call(post("/api/projects/" + p.getId() + "/calendar/events").content(event(leader.getId())), outsider));
    }
    @Test void memberReadsWorkButObserverWritesAndForeignAttendeesDoNot() throws Exception {
        var leader = user(em, "합성 팀장"); var observer = user(em, "합성 관찰자"); var other = user(em, "합성 다른 팀원");
        var p = project(em, leader); member(em, p, leader, "LEADER"); member(em, p, observer, "OBSERVER");
        var otherProject = project(em, other); member(em, otherProject, other, "LEADER"); em.flush();
        String base = "/api/projects/" + p.getId();
        assertEquals(200, call(get(base + "/calendar/status"), observer));
        assertEquals(200, call(get(base + "/calendar/availability?date=2026-11-10"), leader));
        assertEquals(200, call(post("/api/calendar/recommend").content(recommend(p.getId(), leader.getId())), leader));
        assertEquals(200, call(post(base + "/calendar/events").content(event(leader.getId())), leader));
        assertEquals(403, call(post(base + "/calendar/events").content(event(leader.getId())), observer));
        for (UUID invalid : new UUID[]{other.getId(), UUID.randomUUID()}) {
            assertEquals(403, call(post(base + "/calendar/events").content(event(invalid)), leader));
            assertEquals(403, call(post("/api/calendar/recommend").content(recommend(p.getId(), invalid)), leader));
        }
        assertEquals(404, call(get("/api/projects/" + UUID.randomUUID() + "/calendar/status"), leader));
    }
    @Test void uuidStateAndMissingBrowserCookieAreRejectedWithoutGoogleConfiguration() throws Exception {
        var leader = user(em, "합성 사용자"); em.flush();
        assertEquals(403, call(get("/api/auth/google/callback").param("code", "synthetic-code")
                .param("state", leader.getId().toString()), leader));
        assertEquals(401, mvc.perform(get("/api/calendar/authorize")).andReturn().getResponse().getStatus());
    }
    private int call(MockHttpServletRequestBuilder request, User actor) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + jwt.generateAccessToken(actor.getEmail())))
                .andReturn().getResponse().getStatus();
    }
    private String event(UUID attendee) {
        return "{\"title\":\"합성 회의\",\"startTime\":\"2026-11-10T10:00:00+09:00\",\"endTime\":\"2026-11-10T11:00:00+09:00\",\"attendeeIds\":[\""+attendee+"\"]}";
    }
    private String recommend(UUID project, UUID attendee) {
        return "{\"projectId\":\""+project+"\",\"targetDate\":\"2026-11-10\",\"attendeeIds\":[\""+attendee+"\"]}";
    }
}
