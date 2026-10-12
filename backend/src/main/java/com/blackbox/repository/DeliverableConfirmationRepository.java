package com.blackbox.repository;

import com.blackbox.entity.DeliverableConfirmation;
import com.blackbox.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;
import java.util.UUID;

/** 키는 제출물 id다. */
public interface DeliverableConfirmationRepository extends JpaRepository<DeliverableConfirmation, UUID> {
    @Query("select c.deliverableId from DeliverableConfirmation c where c.project = :project")
    Set<UUID> findDeliverableIdsByProject(@Param("project") Project project);
}
