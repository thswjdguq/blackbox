package com.blackbox.repository;

import com.blackbox.entity.Task;
import com.blackbox.entity.TaskAssignee;
import com.blackbox.entity.TaskAssigneeId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskAssigneeRepository extends JpaRepository<TaskAssignee, TaskAssigneeId> {
    List<TaskAssignee> findByTask(Task task);
    void deleteByTask(Task task);
}
