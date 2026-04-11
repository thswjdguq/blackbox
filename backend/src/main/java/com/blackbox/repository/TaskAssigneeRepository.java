package com.blackbox.repository;

import com.blackbox.entity.Project;
import com.blackbox.entity.Task;
import com.blackbox.entity.TaskAssignee;
import com.blackbox.entity.TaskAssigneeId;
import com.blackbox.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskAssigneeRepository extends JpaRepository<TaskAssignee, TaskAssigneeId> {
    List<TaskAssignee> findByTask(Task task);
    void deleteByTask(Task task);

    /** 해당 프로젝트에서 유저가 담당한 DONE 태스크 수 */
    @Query("SELECT COUNT(a) FROM TaskAssignee a WHERE a.task.project = :project AND a.user = :user AND a.task.status = 'DONE'")
    long countDoneByProjectAndUser(@Param("project") Project project, @Param("user") User user);

    /** 해당 프로젝트에서 유저가 담당한 DONE 액션아이템 수 (description에 '[Action item from meeting:' 포함) */
    @Query("SELECT COUNT(a) FROM TaskAssignee a WHERE a.task.project = :project AND a.user = :user AND a.task.status = 'DONE' AND a.task.description LIKE '%[Action item from meeting:%'")
    long countDoneActionItemsByProjectAndUser(@Param("project") Project project, @Param("user") User user);
}
