package com.blackbox.repository;

import com.blackbox.entity.Project;
import com.blackbox.entity.Task;
import com.blackbox.entity.Deliverable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {
    boolean existsByDeliverable(com.blackbox.entity.Deliverable deliverable);
    boolean existsByRequirement(com.blackbox.entity.DeliverableRequirement requirement);
    // 목록 응답에 쓰는 단일 연관을 함께 읽어 업무 수만큼 지연 조회가 늘지 않게 한다.
    @EntityGraph(attributePaths = {"deliverable", "requirement", "createdBy"})
    List<Task> findByProjectOrderByCreatedAtDesc(Project project);
    @EntityGraph(attributePaths = {"deliverable", "requirement", "createdBy"})
    List<Task> findByProjectAndStatusOrderByCreatedAtDesc(Project project, String status);
    Optional<Task> findByIdAndProject(UUID id, Project project);
    long countByProjectAndDeliverable(Project project, Deliverable deliverable);
    long countByProjectAndDeliverableAndStatus(Project project, Deliverable deliverable, String status);
}
