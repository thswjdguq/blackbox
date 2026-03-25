package com.blackbox.repository;

import com.blackbox.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    Optional<Project> findByInviteCode(String inviteCode);
    boolean existsByInviteCode(String inviteCode);
}
