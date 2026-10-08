package com.blackbox.repository;

import com.blackbox.entity.ReviewComment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ReviewCommentRepository extends JpaRepository<ReviewComment, UUID> {
    @EntityGraph(attributePaths = {"author", "resolvedBy", "linkedTask"})
    List<ReviewComment> findByProjectIdAndDeliverableIdAndReviewIdOrderByCreatedAtAscIdAsc(UUID projectId, UUID deliverableId, UUID reviewId);
    Optional<ReviewComment> findByIdAndProjectIdAndDeliverableIdAndReviewId(UUID id, UUID projectId, UUID deliverableId, UUID reviewId);
    long countByProjectIdAndDeliverableIdAndResolvedAtIsNull(UUID projectId, UUID deliverableId);
}
