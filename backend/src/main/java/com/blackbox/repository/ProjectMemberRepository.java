package com.blackbox.repository;

import com.blackbox.entity.Project;
import com.blackbox.entity.ProjectMember;
import com.blackbox.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {
    List<ProjectMember> findByProject(Project project);
    List<ProjectMember> findByUser(User user);
    Optional<ProjectMember> findByProjectAndUser(Project project, User user);
    boolean existsByProjectAndUser(Project project, User user);
    long countByProject(Project project);
    long countByProjectAndRole(Project project, String role);
}
