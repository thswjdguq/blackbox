package com.blackbox.repository;

import com.blackbox.entity.ContributionScore;
import com.blackbox.entity.Project;
import com.blackbox.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContributionScoreRepository extends JpaRepository<ContributionScore, UUID> {
    List<ContributionScore> findByProjectOrderByTotalScoreDesc(Project project);
    Optional<ContributionScore> findByProjectAndUser(Project project, User user);
}
