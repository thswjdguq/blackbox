package com.blackbox.service;

import com.blackbox.dto.DeliverableDtos.*;
import com.blackbox.entity.*;
import com.blackbox.exception.*;
import com.blackbox.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeliverableServiceTest {
    final DeliverableRepository deliveries = mock(DeliverableRepository.class);
    final DeliverableRequirementRepository requirements = mock(DeliverableRequirementRepository.class);
    final TaskRepository tasks = mock(TaskRepository.class);
    final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
    final ProjectRepository projects = mock(ProjectRepository.class);
    final ProjectAccessChecker access = new ProjectAccessChecker(projects, members);
    final DeliverableService service = new DeliverableService(deliveries, requirements, tasks, members, access);
    final Project project = new Project();
    final User user = new User();
    final Deliverable delivery = new Deliverable();
    final ProjectMember member = new ProjectMember();

    @BeforeEach void setup() {
        project.setId(UUID.randomUUID()); user.setId(UUID.randomUUID());
        delivery.setId(UUID.randomUUID()); delivery.setProject(project);
        member.setUser(user); member.setProject(project); member.setRole("MEMBER");
        when(projects.findById(project.getId())).thenReturn(Optional.of(project));
        when(members.findByProjectAndUser(project, user)).thenReturn(Optional.of(member));
        when(deliveries.findByIdAndProject(delivery.getId(), project)).thenReturn(Optional.of(delivery));
    }

    @Test void observerCanReadButCannotWrite() {
        member.setRole("OBSERVER");
        assertEquals(List.of(), service.list(project.getId(), user));
        assertThrows(ForbiddenException.class, () -> service.save(project.getId(), null,
                new SaveRequest("보고서", null, LocalDate.now(), null, null), user));
        verify(deliveries, never()).save(any());
    }
    @Test void outsiderCannotRead() {
        when(members.findByProjectAndUser(project, user)).thenReturn(Optional.empty());
        assertThrows(ForbiddenException.class, () -> service.list(project.getId(), user));
    }
    @Test void cannotAccessOtherProjectsDeliverable() {
        UUID other = UUID.randomUUID();
        assertThrows(NotFoundException.class, () -> service.delete(project.getId(), other, user));
        verify(deliveries, never()).delete(any());
    }
    @Test void ownerMustBeContributingMember() {
        when(members.findByProject(project)).thenReturn(List.of(member));
        assertThrows(ResponseStatusException.class, () -> service.save(project.getId(), null,
                new SaveRequest("보고서", null, LocalDate.now(), null, UUID.randomUUID()), user));
        verify(deliveries, never()).save(any());
    }
    @Test void linkedDeliverableCannotBeDeleted() {
        when(tasks.existsByDeliverable(delivery)).thenReturn(true);
        var error = assertThrows(ResponseStatusException.class, () -> service.delete(project.getId(), delivery.getId(), user));
        assertEquals(409, error.getStatusCode().value());
        verify(requirements, never()).deleteByDeliverable(any());
        verify(deliveries, never()).delete(any());
    }
    @Test void observerCannotBeAssignedAsOwner() {
        var observer = new ProjectMember();
        var viewer = new User(); viewer.setId(UUID.randomUUID());
        observer.setUser(viewer); observer.setRole("OBSERVER");
        when(members.findByProject(project)).thenReturn(List.of(member, observer));
        assertThrows(ResponseStatusException.class, () -> service.save(project.getId(), null,
                new SaveRequest("보고서", null, LocalDate.now(), null, viewer.getId()), user));
        verify(deliveries, never()).save(any());
    }
    @Test void linkedRequirementCannotBeDeleted() {
        var requirement = new DeliverableRequirement(); requirement.setId(UUID.randomUUID());
        when(requirements.findByIdAndDeliverable(requirement.getId(), delivery)).thenReturn(Optional.of(requirement));
        when(tasks.existsByRequirement(requirement)).thenReturn(true);
        var error = assertThrows(ResponseStatusException.class, () -> service.deleteRequirement(project.getId(), delivery.getId(), requirement.getId(), user));
        assertEquals(409, error.getStatusCode().value());
        verify(requirements, never()).delete(any());
    }
    @Test void requirementMustBelongToSelectedDeliverable() {
        assertThrows(NotFoundException.class, () -> service.saveRequirement(project.getId(), delivery.getId(), UUID.randomUUID(), new RequirementRequest("출처 표기", true), user));
        verify(requirements, never()).save(any());
    }
    @Test void unlinkedDeliverableCanBeDeleted() {
        service.delete(project.getId(), delivery.getId(), user);
        var order = inOrder(requirements, deliveries);
        order.verify(requirements).deleteByDeliverable(delivery);
        order.verify(requirements).flush();
        order.verify(deliveries).delete(delivery);
    }
}
