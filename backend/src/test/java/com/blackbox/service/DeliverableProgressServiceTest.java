package com.blackbox.service;

import com.blackbox.entity.Deliverable;
import com.blackbox.entity.Project;
import com.blackbox.entity.User;
import com.blackbox.exception.ForbiddenException;
import com.blackbox.exception.NotFoundException;
import com.blackbox.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeliverableProgressServiceTest {
    private final TaskRepository tasks = mock(TaskRepository.class);
    private final ProjectAccessChecker access = mock(ProjectAccessChecker.class);
    private final DeliverableService deliverables = mock(DeliverableService.class);
    private final DeliverableProgressService service = new DeliverableProgressService(tasks, access, deliverables);
    private final UUID projectId = UUID.randomUUID();
    private final UUID deliverableId = UUID.randomUUID();
    private final Project project = new Project();
    private final Deliverable deliverable = new Deliverable();
    private final User user = new User();

    @BeforeEach
    void setUp() {
        project.setId(projectId);
        deliverable.setId(deliverableId);
        deliverable.setProject(project);
        user.setId(UUID.randomUUID());
        when(access.getProject(projectId)).thenReturn(project);
        when(deliverables.find(project, deliverableId)).thenReturn(deliverable);
    }

    @Test
    void returnsTaskAndRequirementProgressRoundedHalfUp() {
        when(tasks.countByProjectAndDeliverable(project, deliverable)).thenReturn(3L);
        when(tasks.countByProjectAndDeliverableAndStatus(project, deliverable, "DONE")).thenReturn(2L);
        when(deliverables.countRequiredRequirements(deliverable)).thenReturn(3L);
        when(deliverables.countMetRequiredRequirements(deliverable)).thenReturn(1L);

        var result = service.get(projectId, deliverableId, user);

        assertEquals(3, result.tasks().total());
        assertEquals(2, result.tasks().completed());
        assertEquals(new BigDecimal("66.67"), result.tasks().percent());
        assertEquals(3, result.requiredRequirements().total());
        assertEquals(1, result.requiredRequirements().met());
        assertEquals(new BigDecimal("33.33"), result.requiredRequirements().percent());
        assertTrue(result.requiredRequirements().assessmentAvailable());
    }

    @Test
    void emptyCountsReturnZeroPercentInsteadOfOneHundred() {
        var result = service.get(projectId, deliverableId, user);

        assertEquals(new BigDecimal("0.00"), result.tasks().percent());
        assertEquals(new BigDecimal("0.00"), result.requiredRequirements().percent());
    }

    @Test
    void observerUsesTheSameMemberReadPermission() {
        service.get(projectId, deliverableId, user);
        verify(access).requireMember(project, user);
        verify(access, never()).requireContributor(any(), any());
    }

    @Test
    void outsiderFailureStopsBeforeDeliverableAndCounts() {
        doThrow(new ForbiddenException("프로젝트 멤버가 아닙니다")).when(access).requireMember(project, user);

        assertThrows(ForbiddenException.class, () -> service.get(projectId, deliverableId, user));
        verify(deliverables, never()).find(any(), any());
        verifyNoInteractions(tasks);
    }

    @Test
    void deliverableOutsideProjectRemainsNotFound() {
        when(deliverables.find(project, deliverableId)).thenThrow(new NotFoundException("제출물을 찾을 수 없습니다"));

        assertThrows(NotFoundException.class, () -> service.get(projectId, deliverableId, user));
        verifyNoInteractions(tasks);
    }

    @Test
    void repositoryFailureIsNotConvertedToFakeZeroProgress() {
        when(tasks.countByProjectAndDeliverable(project, deliverable)).thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class, () -> service.get(projectId, deliverableId, user));
    }
}
