package com.blackbox.repository;

import com.blackbox.entity.DeliverableConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** 키는 제출물 id다. */
public interface DeliverableConfirmationRepository extends JpaRepository<DeliverableConfirmation, UUID> {
}
