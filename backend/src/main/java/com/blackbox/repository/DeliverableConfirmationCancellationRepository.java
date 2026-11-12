package com.blackbox.repository;

import com.blackbox.entity.Deliverable;
import com.blackbox.entity.DeliverableConfirmationCancellation;
import com.blackbox.entity.Project;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface DeliverableConfirmationCancellationRepository extends JpaRepository<DeliverableConfirmationCancellation, UUID> {
    boolean existsByReviewId(UUID reviewId);

    // 오래된 것부터. 응답에 쓰는 회차와 사람을 함께 읽는다
    @EntityGraph(attributePaths = {"review", "confirmedBy", "cancelledBy"})
    List<DeliverableConfirmationCancellation> findByDeliverableOrderByCancelledAtAsc(Deliverable deliverable);

    // 제출물 목록의 상태가 프로젝트의 취소한 회차를 한 번에 읽는다
    @Query("select c.review.id from DeliverableConfirmationCancellation c where c.project = :project")
    Set<UUID> findReviewIdsByProject(@Param("project") Project project);
}
