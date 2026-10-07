package com.blackbox.service;

import com.blackbox.dto.CreateProjectRequest;
import com.blackbox.dto.ProjectResponse;
import com.blackbox.dto.UpdateProjectRequest;
import com.blackbox.entity.*;
import com.blackbox.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// 프로젝트 하나를 돌려주는 응답마다 요청한 사람의 역할과 인원수가 들어가는지 확인한다.
// 화면이 이 값으로 쓰기 권한을 판단하므로, 비어 있으면 팀장이 관찰자처럼 보인다
class ProjectResponseRoleTest {
    final ProjectRepository projects = mock(ProjectRepository.class);
    final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
    final ProjectService service = new ProjectService(projects, members,
            new ProjectAccessChecker(projects, members), mock(EntityManager.class));
    final Project project = new Project();
    final User user = new User();
    final ProjectMember member = new ProjectMember();

    @BeforeEach void setup() {
        project.setId(UUID.randomUUID()); user.setId(UUID.randomUUID());
        project.setCreatedBy(user);
        member.setProject(project); member.setUser(user); member.setRole("LEADER");
        when(projects.findById(project.getId())).thenReturn(Optional.of(project));
        when(members.findByProjectAndUser(project, user)).thenReturn(Optional.of(member));
        when(members.countByProject(any())).thenReturn(3L);
    }

    @Test void getReturnsMyRoleAndMemberCount() {
        for (String role : List.of("LEADER", "MEMBER", "OBSERVER")) {
            member.setRole(role);
            ProjectResponse res = service.getProject(project.getId(), user);
            assertEquals(role, res.myRole());
            assertEquals(3L, res.memberCount());
        }
    }

    @Test void createReturnsLeaderRole() {
        ProjectResponse res = service.createProject(
                new CreateProjectRequest("새 프로젝트", null, null, null, null, null), user);
        assertEquals("LEADER", res.myRole());
        assertEquals(3L, res.memberCount());
    }

    @Test void updateAndInviteCodeKeepRole() {
        assertEquals("LEADER", service.updateProject(project.getId(),
                new UpdateProjectRequest("이름", null, null, null, null, null), user).myRole());
        assertEquals("LEADER", service.regenerateInviteCode(project.getId(), user).myRole());
    }
}
