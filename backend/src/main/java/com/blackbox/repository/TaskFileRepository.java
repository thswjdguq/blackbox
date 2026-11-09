package com.blackbox.repository;

import com.blackbox.entity.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface TaskFileRepository extends JpaRepository<TaskFile, UUID> {
    @EntityGraph(attributePaths = {"file", "file.uploader", "task"})
    List<TaskFile> findByTaskOrderByCreatedAtDesc(Task task);
    Optional<TaskFile> findByTaskAndFile(Task task, FileVault file);
    boolean existsByTask(Task task);
    @EntityGraph(attributePaths = {"file", "file.uploader", "task"})
    List<TaskFile> findByTask_DeliverableOrderByCreatedAtDesc(Deliverable deliverable);
}
