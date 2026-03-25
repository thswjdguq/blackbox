package com.blackbox.repository;

import com.blackbox.entity.ActivityLog;
import com.blackbox.entity.Project;
import com.blackbox.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {

    @Query("SELECT COUNT(a) FROM ActivityLog a WHERE a.project = :project AND a.user = :user AND a.actionType IN :types")
    long countByProjectAndUserAndTypes(
            @Param("project") Project project,
            @Param("user") User user,
            @Param("types") List<String> types);

    @Query("SELECT COUNT(a) > 0 FROM ActivityLog a WHERE a.project = :project AND a.user = :user AND a.occurredAt > :since")
    boolean hasActivityAfter(
            @Param("project") Project project,
            @Param("user") User user,
            @Param("since") OffsetDateTime since);
}
