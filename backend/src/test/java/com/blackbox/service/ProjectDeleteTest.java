package com.blackbox.service;

import com.blackbox.entity.*;
import com.blackbox.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectDeleteTest {
    final ProjectRepository projects = mock(ProjectRepository.class);
    final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
    final ProjectService service = new ProjectService(projects, members,
            new ProjectAccessChecker(projects, members), mock(EntityManager.class));
    final Project project = new Project();
    final User leader = new User();

    @BeforeEach void setup() {
        project.setId(UUID.randomUUID()); leader.setId(UUID.randomUUID());
        var member = new ProjectMember(); member.setProject(project); member.setUser(leader); member.setRole("LEADER");
        when(projects.findById(project.getId())).thenReturn(Optional.of(project));
        when(members.findByProjectAndUser(project, leader)).thenReturn(Optional.of(member));
    }

    @Test void deletesProjectWithoutRecords() {
        assertDoesNotThrow(() -> service.deleteProject(project.getId(), leader));
        verify(projects).delete(project);
        verify(projects).flush();
    }
    @Test void projectWithRecordsIsRejectedWithGuidance() {
        doThrow(new DataIntegrityViolationException("contribution_scores_project_id_fkey")).when(projects).flush();
        var error = assertThrows(ResponseStatusException.class, () -> service.deleteProject(project.getId(), leader));
        assertEquals(409, error.getStatusCode().value());
        assertEquals("기록 보존을 위해 이 프로젝트는 삭제할 수 없습니다", error.getReason());
    }
}
