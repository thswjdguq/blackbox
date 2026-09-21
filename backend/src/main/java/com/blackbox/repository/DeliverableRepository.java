package com.blackbox.repository;
import com.blackbox.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface DeliverableRepository extends JpaRepository<Deliverable, UUID> {
    List<Deliverable> findByProjectOrderByDueDateAscCreatedAtAsc(Project project);
    Optional<Deliverable> findByIdAndProject(UUID id, Project project);
}
