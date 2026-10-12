package com.blackbox.service;

import com.blackbox.dto.UpdateTaskRequest;
import com.blackbox.entity.*;
import com.blackbox.exception.NotFoundException;
import com.blackbox.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskDeliverableTest {
    final TaskRepository tasks = mock(TaskRepository.class);
    final TaskAssigneeRepository assignees = mock(TaskAssigneeRepository.class);
    final ProjectAccessChecker access = mock(ProjectAccessChecker.class);
    final DeliverableService deliveries = mock(DeliverableService.class);
    final TaskService service = new TaskService(tasks, assignees, mock(ProjectMemberRepository.class),
            mock(UserRepository.class), access, mock(ActivityLogService.class), mock(NotionService.class),
            mock(AlertService.class), mock(DiscordNotificationService.class), mock(ScoreService.class),
            mock(EntityManager.class), deliveries);
    final Project project = new Project();
    final User user = new User();
    final Task task = new Task();
    final Deliverable delivery = new Deliverable();
    final DeliverableRequirement requirement = new DeliverableRequirement();

    @BeforeEach void setup() {
        project.setId(UUID.randomUUID()); user.setId(UUID.randomUUID()); task.setId(UUID.randomUUID());
        task.setProject(project); task.setCreatedBy(user);
        delivery.setId(UUID.randomUUID()); delivery.setProject(project);
        requirement.setId(UUID.randomUUID()); requirement.setDeliverable(delivery);
        task.setDeliverable(delivery); task.setRequirement(requirement);
        when(access.getProject(project.getId())).thenReturn(project);
        when(tasks.findByIdAndProject(task.getId(), project)).thenReturn(Optional.of(task));
    }
    void update(UUID d, UUID r, boolean clearD, boolean clearR) {
        service.updateTask(project.getId(), task.getId(), new UpdateTaskRequest(null, null, null, null, null, d, r, clearD, clearR, " 출처 3개 포함 "), user);
    }
    @Test void unrelatedEditPreservesLinksAndSavesCriteria() {
        update(null, null, false, false);
        assertSame(delivery, task.getDeliverable()); assertSame(requirement, task.getRequirement());
        assertEquals("출처 3개 포함", task.getCompletionCriteria());
    }
    @Test void unlinkDeliverableAlsoUnlinksRequirement() {
        update(null, null, true, false);
        assertNull(task.getDeliverable()); assertNull(task.getRequirement());
    }
    @Test void unlinkRequirementPreservesDeliverable() {
        update(null, null, false, true);
        assertSame(delivery, task.getDeliverable()); assertNull(task.getRequirement());
    }
    @Test void changingDeliverableClearsOldRequirement() {
        var next = new Deliverable(); next.setId(UUID.randomUUID());
        when(deliveries.find(project, next.getId())).thenReturn(next);
        update(next.getId(), null, false, false);
        assertSame(next, task.getDeliverable()); assertNull(task.getRequirement());
    }
    @Test void rejectsCrossProjectDeliverableBeforeSaving() {
        var other = UUID.randomUUID();
        when(deliveries.find(project, other)).thenThrow(new NotFoundException("제출물 없음"));
        assertThrows(NotFoundException.class, () -> update(other, null, false, false));
        verify(tasks, never()).save(any());
    }
    @Test void rejectsRequirementFromOtherDeliverable() {
        var other = UUID.randomUUID();
        when(deliveries.findRequirement(delivery, other)).thenThrow(new NotFoundException("요구사항 없음"));
        assertThrows(NotFoundException.class, () -> update(null, other, false, false));
        verify(tasks, never()).save(any());
    }
    @Test void requirementCannotExistWithoutDeliverable() {
        task.setDeliverable(null); task.setRequirement(null);
        assertThrows(ResponseStatusException.class, () -> update(null, requirement.getId(), false, false));
        verify(tasks, never()).save(any());
    }
    @Test void contradictoryLinkAndClearAreRejected() {
        assertThrows(ResponseStatusException.class, () -> update(delivery.getId(), null, true, false));
        verify(tasks, never()).save(any());
    }
}
