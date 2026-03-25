package com.blackbox.repository;

import com.blackbox.entity.Alert;
import com.blackbox.entity.Project;
import com.blackbox.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {
    List<Alert> findByProjectOrderByCreatedAtDesc(Project project);
    boolean existsByProjectAndUserAndAlertTypeAndIsReadFalse(Project project, User user, String alertType);
    boolean existsByProjectAndUserIsNullAndAlertTypeAndIsReadFalse(Project project, String alertType);
}
