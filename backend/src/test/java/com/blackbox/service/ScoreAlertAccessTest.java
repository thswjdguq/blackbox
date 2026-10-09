package com.blackbox.service;

import com.blackbox.entity.*;
import com.blackbox.exception.ForbiddenException;
import com.blackbox.exception.NotFoundException;
import com.blackbox.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScoreAlertAccessTest {
    final ProjectRepository projects = mock(ProjectRepository.class);
    final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
    final ProjectAccessChecker access = new ProjectAccessChecker(projects, members);
    final ContributionScoreRepository scores = mock(ContributionScoreRepository.class);
    final AlertRepository alerts = mock(AlertRepository.class);
    final AlertService alertService = new AlertService(alerts, mock(ActivityLogRepository.class),
            projects, members, scores, mock(DiscordNotificationService.class), access);
    final ScoreService scoreService = new ScoreService(projects, members, scores,
            mock(MeetingRepository.class), mock(MeetingAttendeeRepository.class),
            mock(TaskAssigneeRepository.class), mock(FileVaultRepository.class), alertService, access);
    final Project project = new Project();
    final User actor = new User();
    final Alert alert = new Alert();

    @BeforeEach void setup() {
        project.setId(UUID.randomUUID());
        actor.setId(UUID.randomUUID());
        alert.setId(UUID.randomUUID());
        alert.setProject(project);
        when(projects.findById(project.getId())).thenReturn(Optional.of(project));
        when(alerts.findById(alert.getId())).thenReturn(Optional.of(alert));
    }

    void grant(String role) {
        ProjectMember member = new ProjectMember();
        member.setProject(project); member.setUser(actor); member.setRole(role);
        when(members.findByProjectAndUser(project, actor)).thenReturn(Optional.of(member));
    }

    // 실제 권한 검사기를 사용하여 역할 규칙 변경도 이 검사에서 드러나게 한다.
    @ParameterizedTest @ValueSource(strings = {"LEADER", "MEMBER", "OBSERVER"})
    void membersCanRead(String role) {
        grant(role);
        assertNotNull(scoreService.getScores(project.getId(), actor));
        assertNotNull(alertService.getAlerts(project.getId(), actor));
        verify(scores).findByProjectOrderByTotalScoreDesc(project);
        verify(alerts).findActiveByProject(project);
    }

    @Test void outsiderCannotReadOrWrite() {
        assertThrows(ForbiddenException.class, () -> scoreService.getScores(project.getId(), actor));
        assertThrows(ForbiddenException.class, () -> scoreService.recalculate(project.getId(), actor));
        assertThrows(ForbiddenException.class, () -> alertService.getAlerts(project.getId(), actor));
        assertThrows(ForbiddenException.class, () -> alertService.markAsRead(project.getId(), alert.getId(), actor));
        verifyNoInteractions(scores, alerts);
    }

    @Test void observerCannotMutateSharedState() {
        grant("OBSERVER");
        assertThrows(ForbiddenException.class, () -> scoreService.recalculate(project.getId(), actor));
        assertThrows(ForbiddenException.class, () -> alertService.markAsRead(project.getId(), alert.getId(), actor));
        assertFalse(alert.isRead());
        verifyNoInteractions(scores, alerts);
    }

    @ParameterizedTest @ValueSource(strings = {"LEADER", "MEMBER"})
    void contributorsCanRecalculateAndMarkRead(String role) {
        grant(role);
        assertEquals(List.of(), scoreService.recalculate(project.getId(), actor));
        alertService.markAsRead(project.getId(), alert.getId(), actor);
        assertTrue(alert.isRead());
        verify(alerts).save(alert);
    }

    @Test void missingProjectIsNotFoundBeforeDataAccess() {
        UUID missing = UUID.randomUUID();
        assertThrows(NotFoundException.class, () -> scoreService.getScores(missing, actor));
        assertThrows(NotFoundException.class, () -> scoreService.recalculate(missing, actor));
        assertThrows(NotFoundException.class, () -> alertService.getAlerts(missing, actor));
        assertThrows(NotFoundException.class, () -> alertService.markAsRead(missing, alert.getId(), actor));
        verifyNoInteractions(scores, alerts);
    }

    @Test void foreignAlertIsNotFoundAndUnchanged() {
        grant("MEMBER");
        Project other = new Project(); other.setId(UUID.randomUUID());
        alert.setProject(other);
        assertThrows(NotFoundException.class, () -> alertService.markAsRead(project.getId(), alert.getId(), actor));
        assertFalse(alert.isRead());
        verify(alerts, never()).save(any());
    }

    @Test void missingAlertIsNotFound() {
        grant("MEMBER");
        assertThrows(NotFoundException.class, () -> alertService.markAsRead(project.getId(), UUID.randomUUID(), actor));
        verify(alerts, never()).save(any());
    }

    @Test void schedulerPathDoesNotRequireHttpActor() {
        assertEquals(List.of(), scoreService.recalculate(project.getId()));
        verify(members, never()).findByProjectAndUser(any(), any());
    }
}
