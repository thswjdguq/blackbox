package com.blackbox.repository;

import com.blackbox.entity.Alert;
import com.blackbox.entity.Project;
import com.blackbox.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

    // 활성 경보만 조회 (resolved_at IS NULL)
    @Query("SELECT a FROM Alert a WHERE a.project = :project AND a.resolvedAt IS NULL ORDER BY a.createdAt DESC")
    List<Alert> findActiveByProject(@Param("project") Project project);

    // 전체 조회 (기존 호환)
    List<Alert> findByProjectOrderByCreatedAtDesc(Project project);

    // 활성 경보 중복 체크 (resolved_at IS NULL)
    boolean existsByProjectAndUserAndAlertTypeAndResolvedAtIsNull(Project project, User user, String alertType);
    boolean existsByProjectAndUserIsNullAndAlertTypeAndResolvedAtIsNull(Project project, String alertType);

    // 특정 유저+타입의 활성 경보 조회
    Optional<Alert> findByProjectAndUserAndAlertTypeAndResolvedAtIsNull(Project project, User user, String alertType);

    // OVERLOAD 활성 경보 조회
    @Query("SELECT a FROM Alert a WHERE a.project = :project AND a.alertType = :type AND a.resolvedAt IS NULL")
    List<Alert> findActiveByProjectAndType(@Param("project") Project project, @Param("type") String type);

    // ── 이전 코드 호환 (isRead 기반) — 아직 남아있는 곳 대비 ─────────────
    boolean existsByProjectAndUserAndAlertTypeAndIsReadFalse(Project project, User user, String alertType);
    boolean existsByProjectAndUserIsNullAndAlertTypeAndIsReadFalse(Project project, String alertType);
}
