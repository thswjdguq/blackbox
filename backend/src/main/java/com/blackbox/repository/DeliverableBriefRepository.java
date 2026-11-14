package com.blackbox.repository;

import com.blackbox.entity.Deliverable;
import com.blackbox.entity.DeliverableBrief;
import com.blackbox.entity.Project;
import com.blackbox.entity.ProjectBrief;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DeliverableBriefRepository extends JpaRepository<DeliverableBrief, DeliverableBrief.Key> {
    /** 건 순서(오래된 것부터, K-31 3장) */
    @Query("select l from DeliverableBrief l join fetch l.brief where l.deliverable = :deliverable order by l.linkedAt, l.brief.id")
    List<DeliverableBrief> findByDeliverable(@Param("deliverable") Deliverable deliverable);

    /** 이 안내문이 걸린 제출물의 id */
    @Query("select l.deliverable.id from DeliverableBrief l where l.brief = :brief")
    List<UUID> findDeliverableIdsByBrief(@Param("brief") ProjectBrief brief);

    /** 프로젝트의 연결 전부. 제출물 목록과 안내문의 usage가 한 번에 읽는다 */
    @Query("select l from DeliverableBrief l join fetch l.brief join fetch l.deliverable "
            + "where l.deliverable.project = :project order by l.linkedAt, l.brief.id")
    List<DeliverableBrief> findByProject(@Param("project") Project project);
}
