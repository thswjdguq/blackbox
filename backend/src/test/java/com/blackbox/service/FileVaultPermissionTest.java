package com.blackbox.service;

import com.blackbox.entity.*;
import com.blackbox.exception.ForbiddenException;
import com.blackbox.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileVaultPermissionTest {
    final ProjectRepository projects = mock(ProjectRepository.class);
    final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
    final FileVaultRepository vaults = mock(FileVaultRepository.class);
    final FileStorageService storage = mock(FileStorageService.class);
    final FileVaultService service = new FileVaultService(vaults, mock(TamperDetectionLogRepository.class),
            mock(AlertRepository.class), mock(HashService.class), storage, new ProjectAccessChecker(projects, members),
            mock(ActivityLogService.class), mock(AlertService.class), mock(NotionService.class));
    final Project project = new Project();
    final User observer = new User();

    @BeforeEach void setup() {
        project.setId(UUID.randomUUID()); observer.setId(UUID.randomUUID());
        var member = new ProjectMember(); member.setProject(project); member.setUser(observer); member.setRole("OBSERVER");
        when(projects.findById(project.getId())).thenReturn(Optional.of(project));
        when(members.findByProjectAndUser(project, observer)).thenReturn(Optional.of(member));
    }

    @Test void observerCannotUpload() {
        assertThrows(ForbiddenException.class, () -> service.upload(project.getId(), mock(MultipartFile.class), observer));
        verifyNoInteractions(storage, vaults);
    }
    @Test void observerCanStillListFiles() {
        assertDoesNotThrow(() -> service.listFiles(project.getId(), observer));
    }
}
