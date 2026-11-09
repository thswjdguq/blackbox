package com.blackbox.controller;

import com.blackbox.entity.*;
import com.blackbox.security.JwtService;
import com.blackbox.service.AiService;
import com.blackbox.service.NotionService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties = "spring.datasource.hikari.minimum-idle=0")
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class MeetingExternalWriteHttpTest {
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean AiService ai;
    @MockitoBean NotionService notion;

    @Test void observerAndOutsiderAreDeniedBeforeProvidersWhileReadRemainsAllowed() throws Exception {
        var leader = user(em, "합성 팀장"); var observer = user(em, "합성 관찰자"); var outsider = user(em, "합성 외부인");
        var p = project(em, leader); member(em, p, leader, "LEADER"); member(em, p, observer, "OBSERVER");
        var meeting = meeting(p, leader); String base = "/api/projects/" + p.getId() + "/meetings/" + meeting.getId();
        assertEquals(200, call(get(base), observer));
        for (User actor : new User[]{observer, outsider}) {
            for (String suffix : new String[]{"/ai/summarize", "/notion/export", "/notion/calendar"}) {
                assertEquals(403, call(post(base + suffix), actor));
            }
        }
        verifyNoInteractions(ai, notion);
        em.refresh(meeting); assertNull(meeting.getAiSummary()); assertNull(meeting.getNotionPageId());
    }
    @Test void contributorCanSaveAiSummaryThroughRealHttpAndDatabase() throws Exception {
        var leader = user(em, "합성 팀장"); var contributor = user(em, "합성 팀원"); var p = project(em, leader);
        member(em, p, leader, "LEADER"); member(em, p, contributor, "MEMBER"); var meeting = meeting(p, leader);
        when(ai.summarizeMeeting(any(), any(), any(), any())).thenReturn("합성 요약");
        assertEquals(200, call(post("/api/projects/" + p.getId() + "/meetings/" + meeting.getId() + "/ai/summarize"), contributor));
        em.flush(); em.refresh(meeting); assertEquals("합성 요약", meeting.getAiSummary());
    }
    private Meeting meeting(Project p, User leader) {
        var meeting = new Meeting(); meeting.setProject(p); meeting.setCreatedBy(leader); meeting.setTitle("합성 회의");
        meeting.setMeetingDate(OffsetDateTime.now().plusDays(1)); em.persist(meeting); em.flush(); return meeting;
    }
    private int call(MockHttpServletRequestBuilder request, User actor) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + jwt.generateAccessToken(actor.getEmail())))
                .andReturn().getResponse().getStatus();
    }
}
