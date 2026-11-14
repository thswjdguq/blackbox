package com.blackbox.service;

import com.blackbox.entity.*;
import com.blackbox.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScoreContributorPolicyTest {
    final ProjectRepository projects = mock(ProjectRepository.class);
    final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
    final ContributionScoreRepository scores = mock(ContributionScoreRepository.class);
    final MeetingRepository meetings = mock(MeetingRepository.class);
    final MeetingAttendeeRepository attendance = mock(MeetingAttendeeRepository.class);
    final TaskAssigneeRepository tasks = mock(TaskAssigneeRepository.class);
    final ScoreSnapshotService snapshots = mock(ScoreSnapshotService.class);
    final AlertService alerts = mock(AlertService.class);
    final ScoreService service = new ScoreService(projects, members, scores, meetings, attendance, tasks, snapshots, alerts,
            new ProjectAccessChecker(projects, members));
    final Project project = new Project();
    final ProjectMember leader = member("LEADER"), contributor = member("MEMBER"), observer = member("OBSERVER");
    @BeforeEach void setup() {
        project.setId(UUID.randomUUID());
        when(scores.save(any())).thenAnswer(i -> i.getArgument(0));
    }
    @Test void onlyContributorRolesAreQueriedSavedAndPassedToAlerts() {
        when(members.findByProject(project)).thenReturn(List.of(leader, contributor, observer));
        var result = service.recalculate(project);
        assertEquals(Set.of(leader.getUser().getId(), contributor.getUser().getId()),
                result.stream().map(r -> r.userId()).collect(java.util.stream.Collectors.toSet()));
        verify(scores, times(2)).save(any());
        verify(scores, never()).findByProjectAndUser(project, observer.getUser());
        verify(tasks, never()).countDoneByProjectAndUser(project, observer.getUser());
        verify(tasks, never()).countDoneActionItemsByProjectAndUser(project, observer.getUser());
        verify(snapshots).read(project); // 파일/검토는 기여자별 조회 대신 프로젝트 단위로 묶어 읽는다.
        verify(alerts).checkAlerts(eq(project), argThat(ms -> ms.size() == 2 && !ms.contains(observer)),
                argThat(ss -> ss.size() == 2 && ss.stream().noneMatch(s -> s.getUser().equals(observer.getUser()))));
    }
    @Test void observerOnlyOrUnknownRolesPerformNoCalculationOrWrites() {
        when(members.findByProject(project)).thenReturn(List.of(observer, member("UNKNOWN")));
        assertTrue(service.recalculate(project).isEmpty());
        verifyNoInteractions(scores, meetings, attendance, tasks, snapshots, alerts);
    }
    @Test void becomingContributorUsesExistingScoreInsteadOfReplacingHistory() {
        var existing = new ContributionScore(); existing.setProject(project); existing.setUser(observer.getUser());
        existing.setId(UUID.randomUUID()); observer.setRole("MEMBER");
        when(members.findByProject(project)).thenReturn(List.of(observer));
        when(scores.findByProjectAndUser(project, observer.getUser())).thenReturn(Optional.of(existing));
        when(tasks.countDoneByProjectAndUser(project, observer.getUser())).thenReturn(1L);
        assertTrue(service.recalculate(project).get(0).taskParticipated());
        verify(scores).save(same(existing)); verify(scores, never()).delete(any());
    }
    private ProjectMember member(String role) {
        var user = new User(); user.setId(UUID.randomUUID()); user.setName("합성 사용자");
        var member = new ProjectMember(); member.setUser(user); member.setRole(role); return member;
    }
}
