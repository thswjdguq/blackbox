package com.blackbox.repository;

import com.blackbox.entity.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface TaskMessageRepository extends JpaRepository<TaskMessage, UUID> {
    @EntityGraph(attributePaths = {"author", "recipient", "parent", "file"})
    Slice<TaskMessage> findByTaskOrderByCreatedAtDescIdDesc(Task task, Pageable pageable);
    Optional<TaskMessage> findByIdAndTask(UUID id, Task task);
    @EntityGraph(attributePaths = {"author", "recipient", "task", "file"})
    List<TaskMessage> findByProjectAndKindAndAnsweredAtIsNullOrderByCreatedAtDesc(Project project, String kind);
}
