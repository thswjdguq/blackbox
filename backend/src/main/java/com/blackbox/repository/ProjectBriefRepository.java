package com.blackbox.repository;

import com.blackbox.entity.Project;
import com.blackbox.entity.ProjectBrief;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectBriefRepository extends JpaRepository<ProjectBrief, UUID> {
    @EntityGraph(attributePaths = {"createdBy", "updatedBy", "sourceFile"})
    List<ProjectBrief> findByProjectOrderByUpdatedAtDesc(Project project);

    @EntityGraph(attributePaths = {"createdBy", "updatedBy", "sourceFile"})
    Optional<ProjectBrief> findByIdAndProject(UUID id, Project project);

    /** 안내문마다 그것을 근거로 한 검토 대기 제안 카드의 수. [brief_id, count] 줄이고, 카드가 없는 안내문은 줄이 없다 */
    @Query(value = "select b.brief_id, count(*) from agent_proposal_briefs b join agent_proposals p on p.id = b.proposal_id "
            + "where p.project_id = :projectId and p.status = 'PENDING' group by b.brief_id", nativeQuery = true)
    List<Object[]> countPendingProposals(@Param("projectId") UUID projectId);
}
