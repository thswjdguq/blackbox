package com.blackbox.service;

import com.blackbox.dto.*;
import com.blackbox.entity.*;
import com.blackbox.exception.ForbiddenException;
import com.blackbox.exception.NotFoundException;
import com.blackbox.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskAssignmentRegressionTest {
    final TaskRepository tasks = mock(TaskRepository.class);
    final TaskAssigneeRepository assignees = mock(TaskAssigneeRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final ProjectRepository projects = mock(ProjectRepository.class);
    final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
    final ProjectAccessChecker access = new ProjectAccessChecker(projects, members);
    final DeliverableService deliveries = mock(DeliverableService.class);
    final DiscordNotificationService discord = mock(DiscordNotificationService.class);
    final TaskService service = new TaskService(tasks, assignees, members, users, access,
            mock(ActivityLogService.class), mock(NotionService.class), mock(AlertService.class),
            discord, mock(ScoreService.class), mock(EntityManager.class), deliveries);
    final Project project = new Project();
    final User actor = new User();
    final User candidate = new User();
    final Task task = new Task();
    final Deliverable delivery = new Deliverable();
    final DeliverableRequirement requirement = new DeliverableRequirement();

    @BeforeEach void setup() {
        project.setId(UUID.randomUUID()); actor.setId(UUID.randomUUID()); candidate.setId(UUID.randomUUID());
        task.setId(UUID.randomUUID()); task.setProject(project); task.setCreatedBy(actor);
        delivery.setId(UUID.randomUUID()); delivery.setProject(project);
        requirement.setId(UUID.randomUUID()); requirement.setDeliverable(delivery);
        when(projects.findById(project.getId())).thenReturn(Optional.of(project));
        grant(actor, "MEMBER");
        when(users.findById(candidate.getId())).thenReturn(Optional.of(candidate));
        when(tasks.findByIdAndProject(task.getId(), project)).thenReturn(Optional.of(task));
        when(deliveries.find(project, delivery.getId())).thenReturn(delivery);
        when(deliveries.findRequirement(delivery, requirement.getId())).thenReturn(requirement);
        when(assignees.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }
    void grant(User user, String role) {
        var member = new ProjectMember(); member.setProject(project); member.setUser(user); member.setRole(role);
        when(members.findByProjectAndUser(project, user)).thenReturn(Optional.of(member));
    }
    UpdateTaskRequest update(UUID d, UUID r, boolean clearR) {
        return new UpdateTaskRequest(null, null, null, null, null, d, r, false, clearR, null);
    }
    @Test void newTaskLinksDeliverableAndRequirement() {
        var response = service.createTask(project.getId(), new CreateTaskRequest("조사", null, null,
                null, null, List.of(), null, delivery.getId(), requirement.getId(), "출처 포함"), actor);
        assertEquals(delivery.getId(), response.deliverableId());
        assertEquals(requirement.getId(), response.requirementId());
        assertEquals("출처 포함", response.completionCriteria());
        verify(tasks).save(argThat(saved -> saved.getDeliverable() == delivery && saved.getRequirement() == requirement));
    }
    @Test void unlinkedTaskCanBeLinked() {
        var result = service.updateTask(project.getId(), task.getId(), update(delivery.getId(), requirement.getId(), false), actor);
        assertEquals(delivery.getId(), result.deliverableId());
        assertEquals(requirement.getId(), result.requirementId());
    }
    @Test void sameDeliverablePreservesRequirement() {
        task.setDeliverable(delivery); task.setRequirement(requirement);
        service.updateTask(project.getId(), task.getId(), update(delivery.getId(), null, false), actor);
        assertSame(requirement, task.getRequirement());
    }
    @Test void requirementAndClearCannotBeCombined() {
        task.setDeliverable(delivery);
        var error = assertThrows(ResponseStatusException.class, () -> service.updateTask(project.getId(), task.getId(), update(null, requirement.getId(), true), actor));
        assertEquals(400, error.getStatusCode().value());
        verify(tasks, never()).save(any());
    }
    @Test void outsiderCannotBeAssigned() {
        assertThrows(ForbiddenException.class, () -> service.setAssignees(project.getId(), task.getId(), new AssignTaskRequest(List.of(candidate.getId())), actor));
        verify(assignees, never()).save(any()); verifyNoInteractions(discord);
    }
    @Test void observerCannotBeAssigned() {
        grant(candidate, "OBSERVER");
        assertThrows(ForbiddenException.class, () -> service.setAssignees(project.getId(), task.getId(), new AssignTaskRequest(List.of(candidate.getId())), actor));
        verify(assignees, never()).save(any()); verifyNoInteractions(discord);
    }
    @Test void duplicateAssigneeIsSavedOnce() {
        grant(candidate, "MEMBER");
        var response = service.setAssignees(project.getId(), task.getId(), new AssignTaskRequest(List.of(candidate.getId(), candidate.getId())), actor);
        assertEquals(1, response.assignees().size());
        assertEquals(candidate.getId(), response.assignees().get(0).userId());
        verify(assignees, times(1)).save(any());
    }
    @Test void emptyListClearsAssignees() {
        var response = service.setAssignees(project.getId(), task.getId(), new AssignTaskRequest(List.of()), actor);
        assertTrue(response.assignees().isEmpty());
        verify(assignees).deleteByTask(task); verify(assignees, never()).save(any());
        verifyNoInteractions(discord);
    }
    @Test void observerCannotChangeAssignments() {
        grant(actor, "OBSERVER");
        assertThrows(ForbiddenException.class, () -> service.setAssignees(project.getId(), task.getId(), new AssignTaskRequest(List.of()), actor));
        verifyNoInteractions(assignees);
    }
    @Test void observerCannotCreateTask() {
        grant(actor, "OBSERVER");
        assertThrows(ForbiddenException.class, () -> service.createTask(project.getId(),
                new CreateTaskRequest("조사", null, null, null, null, List.of(), null, null, null, null), actor));
        verifyNoInteractions(tasks, assignees, discord);
    }
    @Test void outsiderCannotCreateTask() {
        when(members.findByProjectAndUser(project, actor)).thenReturn(Optional.empty());
        assertThrows(ForbiddenException.class, () -> service.createTask(project.getId(),
                new CreateTaskRequest("조사", null, null, null, null, List.of(), null, null, null, null), actor));
        verifyNoInteractions(tasks, assignees, discord);
    }
    @Test void observerCannotEditTask() {
        grant(actor, "OBSERVER");
        assertThrows(ForbiddenException.class, () -> service.updateTask(project.getId(), task.getId(), update(delivery.getId(), null, false), actor));
        verifyNoInteractions(tasks, deliveries);
    }
    @Test void observerCannotChangeStatus() {
        grant(actor, "OBSERVER");
        assertThrows(ForbiddenException.class, () -> service.updateStatus(project.getId(), task.getId(), new UpdateTaskStatusRequest("DONE"), actor));
        assertNull(task.getCompletedAt());
        verifyNoInteractions(tasks, discord);
    }
    @Test void missingAssigneeIsRejectedWithoutAssignmentOrNotification() {
        when(users.findById(candidate.getId())).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.setAssignees(project.getId(), task.getId(), new AssignTaskRequest(List.of(candidate.getId())), actor));
        verify(assignees, never()).save(any());
        verifyNoInteractions(discord);
        // 실제 DB에서 기존 배정 삭제의 롤백 여부는 통합 테스트로 별도 검증한다.
    }
    @Test void memberCannotDeleteAnotherMembersTask() {
        task.setCreatedBy(candidate);
        assertThrows(ForbiddenException.class, () -> service.deleteTask(project.getId(), task.getId(), actor));
        verify(tasks, never()).delete(any());
        verifyNoInteractions(assignees);
    }
    @Test void creatorCanDeleteOwnTask() {
        service.deleteTask(project.getId(), task.getId(), actor);
        verify(assignees).deleteByTask(task);
        verify(tasks).delete(task);
    }
    @Test void leaderCanDeleteAnotherMembersTask() {
        grant(actor, "LEADER"); task.setCreatedBy(candidate);
        service.deleteTask(project.getId(), task.getId(), actor);
        verify(assignees).deleteByTask(task);
        verify(tasks).delete(task);
    }
    @Test void observerCannotDeleteEvenOwnTask() {
        grant(actor, "OBSERVER");
        assertThrows(ForbiddenException.class, () -> service.deleteTask(project.getId(), task.getId(), actor));
        verifyNoInteractions(tasks, assignees);
    }
}
