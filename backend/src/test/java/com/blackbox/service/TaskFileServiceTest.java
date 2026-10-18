package com.blackbox.service;

import com.blackbox.dto.FileUploadResponse;
import com.blackbox.dto.ReviewDtos;
import com.blackbox.entity.*;
import com.blackbox.exception.*;
import com.blackbox.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskFileServiceTest {
    final TaskRepository tasks = mock(TaskRepository.class);
    final TaskFileRepository links = mock(TaskFileRepository.class);
    final FileVaultRepository files = mock(FileVaultRepository.class);
    final DeliverableRepository deliverables = mock(DeliverableRepository.class);
    final ProjectRepository projects = mock(ProjectRepository.class);
    final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
    final FileVaultService storage = mock(FileVaultService.class);
    final ReviewRoundService reviews = mock(ReviewRoundService.class);
    final TaskFileService service = new TaskFileService(tasks, links, files, deliverables, new ProjectAccessChecker(projects, members), storage, reviews, mock(TaskWorkflowService.class), mock(NotificationService.class));
    final Project project = new Project(); final User user = new User(); final Task task = new Task(); final FileVault file = new FileVault(); final ProjectMember member = new ProjectMember();
    @BeforeEach void setup() {
        project.setId(UUID.randomUUID()); user.setId(UUID.randomUUID()); task.setId(UUID.randomUUID()); task.setProject(project); task.setTitle("조사");
        file.setId(UUID.randomUUID()); file.setProject(project); file.setUploader(user); file.setFileName("조사.txt"); file.setVersion(1);
        member.setRole("MEMBER");
        when(projects.findById(project.getId())).thenReturn(Optional.of(project));
        when(members.findByProjectAndUser(project, user)).thenReturn(Optional.of(member));
        when(tasks.findByIdAndProject(task.getId(), project)).thenReturn(Optional.of(task));
        when(files.findByIdAndProject(file.getId(), project)).thenReturn(Optional.of(file));
    }
    @Test void uploadsAndLinksWithoutCompletingTask() throws Exception {
        task.setStatus("IN_PROGRESS");
        var bytes = new MockMultipartFile("file", "조사.txt", "text/plain", "자료".getBytes());
        when(storage.upload(project.getId(), bytes, user)).thenReturn(new FileUploadResponse(file.getId(), project.getId(), file.getFileName(), null, 4L, 1, false, null, null));
        assertEquals(file.getId(), service.upload(project.getId(), task.getId(), bytes, user).file().id());
        verify(links).save(argThat(link -> link.getTask() == task && link.getFile() == file && link.getProject() == project));
        assertEquals("IN_PROGRESS", task.getStatus());
    }
    @Test void observerCanReadButCannotUpload() {
        member.setRole("OBSERVER"); when(links.findByTaskOrderByCreatedAtDesc(task)).thenReturn(List.of());
        assertTrue(service.list(project.getId(), task.getId(), user).isEmpty());
        assertThrows(ForbiddenException.class, () -> service.upload(project.getId(), task.getId(), new MockMultipartFile("file", new byte[]{1}), user));
        verifyNoInteractions(storage);
    }
    @Test void foreignTaskCannotUpload() {
        when(tasks.findByIdAndProject(task.getId(), project)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.upload(project.getId(), task.getId(), new MockMultipartFile("file", new byte[]{1}), user));
        verifyNoInteractions(storage);
    }
    @Test void fileMustBeLinkedToTaskToRequestReview() {
        when(links.findByTaskAndFile(task, file)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.requestReview(project.getId(), task.getId(), file.getId(), user));
        verifyNoInteractions(reviews);
    }
    @Test void unlinkedDeliverableIsConflict() {
        when(links.findByTaskAndFile(task, file)).thenReturn(Optional.of(new TaskFile()));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.requestReview(project.getId(), task.getId(), file.getId(), user)).getStatusCode().value());
    }
    @Test void reviewUsesStoredDeliverableAndExactFileVersion() {
        var deliverable = new Deliverable(); deliverable.setId(UUID.randomUUID()); task.setDeliverable(deliverable);
        when(links.findByTaskAndFile(task, file)).thenReturn(Optional.of(new TaskFile()));
        service.requestReview(project.getId(), task.getId(), file.getId(), user);
        verify(reviews).open(eq(project.getId()), eq(deliverable.getId()), eq(new ReviewDtos.OpenRequest(file.getId())), eq(user));
    }
}
