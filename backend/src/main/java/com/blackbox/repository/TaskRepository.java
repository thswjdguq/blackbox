package com.blackbox.repository;

import com.blackbox.entity.Project;
import com.blackbox.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findByProjectOrderByCreatedAtDesc(Project project);
    List<Task> findByProjectAndStatusOrderByCreatedAtDesc(Project project, String status);
    Optional<Task> findByIdAndProject(UUID id, Project project);
}
