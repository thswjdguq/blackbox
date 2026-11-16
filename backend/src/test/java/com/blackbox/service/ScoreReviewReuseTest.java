package com.blackbox.service;

import com.blackbox.entity.*;
import com.blackbox.repository.AlertRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.OffsetDateTime;
import java.util.List;

import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 실제 도메인 자료를 읽는 spy로 동일 계산의 중복 판독과 경보 의미를 검증한다. 자료는 롤백한다. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({ScoreService.class, ScoreSnapshotService.class, AlertService.class, ScoreRecentActivityReader.class,
        ScoreReviewActivityReader.class, ProjectAccessChecker.class})
class ScoreReviewReuseTest {
    @Autowired EntityManager em;
    @Autowired ScoreService scores;
    @Autowired AlertService alerts;
    @Autowired AlertRepository alertRows;
    @MockitoSpyBean ScoreReviewActivityReader reader;
    @MockitoBean DiscordNotificationService discord;

    record Team(Project project, User leader, User reviewer) {}
    private Team team() {
        User leader = user(em, "합성 팀장"), reviewer = user(em, "합성 검토자");
        Project p = project(em, leader);
        member(em, p, leader, "LEADER"); member(em, p, reviewer, "MEMBER");
        Task task = new Task(); task.setProject(p); task.setCreatedBy(leader);
        task.setTitle("합성 업무"); task.setStatus("IN_PROGRESS"); task.setPriority("MEDIUM"); em.persist(task);
        TaskMessage message = new TaskMessage(); message.setProject(p); message.setTask(task); message.setAuthor(reviewer);
        message.setKind("COMMENT"); message.setBody("합성 피드백"); message.setCreatedAt(OffsetDateTime.now()); em.persist(message);
        em.flush(); em.clear();
        return new Team(em.find(Project.class, p.getId()), leader, reviewer);
    }

    @Test void wholeCalculationReadsReviewsOnceAndSharesTheCountWithAlerts() {
        Team t = team(); clearInvocations(reader);
        var result = scores.recalculate(t.project()); em.flush();
        verify(reader, times(1)).read(t.project());
        var reviewer = result.stream().filter(s -> s.userId().equals(t.reviewer().getId())).findFirst().orElseThrow();
        assertEquals("NONE", reviewer.participationLevel());
        assertEquals(1, reviewer.items().get(4).count());
        assertFalse(alertRows.existsByProjectAndUserAndAlertTypeAndResolvedAtIsNull(t.project(), t.reviewer(), "FREE_RIDE"));
        assertTrue(alertRows.existsByProjectAndUserAndAlertTypeAndResolvedAtIsNull(t.project(), t.leader(), "FREE_RIDE"));
        em.clear();
        var stored = scores.getScores(t.project().getId(), t.reviewer()).stream()
                .filter(s -> s.userId().equals(t.reviewer().getId())).findFirst().orElseThrow();
        assertEquals(reviewer, stored, "같은 집계로 정한 점수 항목과 판정은 DB 재조회에도 유지된다");
    }

    @Test void independentLegacyAlertCallStillReadsReviewsAndProtectsReviewOnlyMember() {
        Team t = team(); clearInvocations(reader);
        ContributionScore score = new ContributionScore(); score.setProject(t.project()); score.setUser(t.reviewer());
        alerts.checkAlerts(t.project(), List.of(), List.of(score)); em.flush();
        verify(reader, times(1)).read(t.project());
        assertFalse(alertRows.existsByProjectAndUserAndAlertTypeAndResolvedAtIsNull(t.project(), t.reviewer(), "FREE_RIDE"));
        assertEquals("NONE", score.getParticipationLevel());
    }

    @Test void minimumTeamSizeStillSkipsReviewLookup() {
        User leader = user(em, "합성 단독 팀장"); Project p = project(em, leader);
        member(em, p, leader, "LEADER"); em.flush(); clearInvocations(reader);
        alerts.checkAlerts(p, List.of(), List.of());
        verifyNoInteractions(reader);
        assertTrue(alertRows.findActiveByProject(p).isEmpty());
    }
}
