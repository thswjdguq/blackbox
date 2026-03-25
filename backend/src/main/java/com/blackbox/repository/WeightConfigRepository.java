package com.blackbox.repository;

import com.blackbox.entity.Project;
import com.blackbox.entity.WeightConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WeightConfigRepository extends JpaRepository<WeightConfig, UUID> {
    Optional<WeightConfig> findTopByProjectOrderByUpdatedAtDesc(Project project);
}
