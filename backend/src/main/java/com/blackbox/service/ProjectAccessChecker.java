package com.blackbox.service;

import com.blackbox.entity.Project;
import com.blackbox.entity.ProjectMember;
import com.blackbox.entity.User;
import com.blackbox.exception.ForbiddenException;
import com.blackbox.exception.NotFoundException;
import com.blackbox.repository.ProjectMemberRepository;
import com.blackbox.repository.ProjectRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ProjectAccessChecker {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    public ProjectAccessChecker(ProjectRepository projectRepository,
                                ProjectMemberRepository projectMemberRepository) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
    }

    /** 프로젝트 조회 (없으면 404) */
    public Project getProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("프로젝트를 찾을 수 없습니다"));
    }

    /** 멤버 조회 (없으면 403) */
    public ProjectMember requireMember(Project project, User user) {
        return projectMemberRepository.findByProjectAndUser(project, user)
                .orElseThrow(() -> new ForbiddenException("프로젝트 멤버가 아닙니다"));
    }

    /** LEADER 권한 확인 */
    public ProjectMember requireLeader(Project project, User user) {
        ProjectMember member = requireMember(project, user);
        if (!"LEADER".equals(member.getRole())) {
            throw new ForbiddenException("리더만 수행할 수 있는 작업입니다");
        }
        return member;
    }
}
