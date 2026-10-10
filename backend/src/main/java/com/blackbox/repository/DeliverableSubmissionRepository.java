package com.blackbox.repository;

import com.blackbox.entity.DeliverableSubmission;
import com.blackbox.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;
import java.util.UUID;

/** 키는 제출물 id다. */
public interface DeliverableSubmissionRepository extends JpaRepository<DeliverableSubmission, UUID> {
    @Query("select s.deliverableId from DeliverableSubmission s where s.confirmation.project = :project")
    Set<UUID> findDeliverableIdsByProject(@Param("project") Project project);
}
