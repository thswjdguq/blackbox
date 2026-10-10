package com.blackbox.repository;

import com.blackbox.entity.DeliverableSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** 키는 제출물 id다. */
public interface DeliverableSubmissionRepository extends JpaRepository<DeliverableSubmission, UUID> {
}
