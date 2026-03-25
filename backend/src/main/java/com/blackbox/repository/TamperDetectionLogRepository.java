package com.blackbox.repository;

import com.blackbox.entity.TamperDetectionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TamperDetectionLogRepository extends JpaRepository<TamperDetectionLog, UUID> {
}
