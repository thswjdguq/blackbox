package com.blackbox.repository;

import com.blackbox.entity.ReviewComment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ReviewCommentRepository extends JpaRepository<ReviewComment, UUID> {
    @EntityGraph(attributePaths = {"author", "resolvedBy", "linkedTask", "deliverable", "deliverable.owner", "review", "review.openedBy", "review.file", "review.file.uploader"})
    List<ReviewComment> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
    List<ReviewComment> findByDeliverableId(UUID deliverableId);
    @EntityGraph(attributePaths = {"author", "resolvedBy", "linkedTask"})
    List<ReviewComment> findByProjectIdAndDeliverableIdAndReviewIdOrderByCreatedAtAscIdAsc(UUID projectId, UUID deliverableId, UUID reviewId);
    Optional<ReviewComment> findByIdAndProjectIdAndDeliverableIdAndReviewId(UUID id, UUID projectId, UUID deliverableId, UUID reviewId);
    long countByProjectIdAndDeliverableIdAndResolvedAtIsNull(UUID projectId, UUID deliverableId);
    boolean existsByLinkedTaskId(UUID taskId);
}
