package com.blackbox.service;

import com.blackbox.entity.*;
import com.blackbox.repository.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AlertParticipationTest {
    final AlertRepository alerts=mock(AlertRepository.class);
    final ActivityLogRepository activity=mock(ActivityLogRepository.class);
    final ProjectMemberRepository members=mock(ProjectMemberRepository.class);
    final ContributionScoreRepository scores=mock(ContributionScoreRepository.class);
    final ProjectAccessChecker access=mock(ProjectAccessChecker.class);
    final AlertService service=new AlertService(alerts,activity,mock(ProjectRepository.class),members,scores,
            mock(DiscordNotificationService.class),access);
    final Project project=new Project();
    final User leader=user(),worker=user(),observer=user(),removed=user();
    User user(){User u=new User();u.setId(UUID.randomUUID());u.setName("합성 실명");return u;}
    ProjectMember member(User u,String role){var m=new ProjectMember();m.setUser(u);m.setProject(project);m.setRole(role);m.setJoinedAt(OffsetDateTime.now());return m;}
    List<ProjectMember> roster(){var list=List.of(member(leader,"LEADER"),member(worker,"MEMBER"),member(observer,"OBSERVER"));when(members.findByProject(project)).thenReturn(list);return list;}
    ContributionScore score(User u,String level){var s=new ContributionScore();s.setUser(u);s.setProject(project);s.setParticipationLevel(level);return s;}
    Alert alert(User u,String type){var a=new Alert();a.setId(UUID.randomUUID());a.setProject(project);a.setUser(u);a.setAlertType(type);a.setMessage("합성 실명 (미참여)");return a;}

    @Test void historicalObserverAndRemovedParticipationHiddenButIntegrityRetained() {
        roster();when(access.getProject(project.getId())).thenReturn(project);
        Alert legitimate=alert(worker,"FREE_RIDE"),tamper=alert(observer,"TAMPER");
        when(alerts.findActiveByProject(project)).thenReturn(List.of(legitimate,tamper,
                alert(observer,"FREE_RIDE"),alert(observer,"DROPOUT"),alert(removed,"OVERLOAD")));
        var result=service.getAlerts(project.getId(),leader);
        assertEquals(2,result.size());assertEquals(worker.getId(),result.get(0).userId());
        assertTrue(result.get(0).message().contains("검토·답변"));
        assertFalse(result.get(0).message().contains("합성 실명"));assertFalse(result.get(0).message().contains("미참여"));
        assertEquals(tamper.getMessage(),result.get(1).message());
        assertEquals("합성 실명 (미참여)",legitimate.getMessage());
        verify(alerts,never()).save(any());verify(alerts,never()).delete(any());
    }
    @Test void onlyCurrentContributorsGenerateNeutralNoticesAndAffectOverloadCounts() {
        var roster=roster();
        var all=List.of(score(leader,"FULL"),score(worker,"NONE"),score(observer,"NONE"),score(removed,"NONE"));
        when(scores.findByProjectOrderByTotalScoreDesc(project)).thenReturn(all);
        service.checkAlerts(project,roster,all);
        var saved=ArgumentCaptor.forClass(Alert.class);verify(alerts).save(saved.capture());
        assertEquals("FREE_RIDE",saved.getValue().getAlertType());assertSame(worker,saved.getValue().getUser());
        assertTrue(saved.getValue().getMessage().contains("자동 집계"));assertFalse(saved.getValue().getMessage().contains(worker.getName()));
        verify(activity,never()).hasActivityAfter(eq(project),eq(observer),any());
        verify(activity,never()).hasActivityAfter(eq(project),eq(removed),any());
    }
    @Test void observerDoesNotSatisfyMinimumTeamSize() {
        var roster=List.of(member(leader,"LEADER"),member(observer,"OBSERVER"));when(members.findByProject(project)).thenReturn(roster);
        service.checkAlerts(project,roster,List.of(score(leader,"NONE"),score(observer,"NONE")));
        verifyNoInteractions(alerts,activity,scores);
    }
    @Test void observerFullScoreCannotResolveMemberOverload() {
        roster();when(scores.findByProjectOrderByTotalScoreDesc(project)).thenReturn(List.of(score(leader,"FULL"),score(worker,"NONE"),score(observer,"FULL"),score(removed,"FULL")));
        service.reevaluate(leader,project);
        verify(alerts,never()).findActiveByProjectAndType(project,"OVERLOAD");
        clearInvocations(alerts,activity,scores);
        service.reevaluate(observer,project);service.reevaluate(removed,project);
        verifyNoInteractions(alerts,activity,scores);
    }
}
