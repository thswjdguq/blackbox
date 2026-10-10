package com.blackbox.agent;

import com.blackbox.entity.Project;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface AgentProposalRepository extends JpaRepository<AgentProposal, UUID> {
    // 목록은 채택 결과를 함께 끌어오지 않는다. 묶음을 같이 조회하면 건수 제한이 DB가 아니라 메모리에서 걸린다.
    // 결과는 AgentProposal.results의 @BatchSize로 한 번에 따로 읽는다. 몇 건까지 줄지는 부르는 쪽이 정한다
    @EntityGraph(attributePaths = {"run", "run.requestedBy", "decidedBy"})
    List<AgentProposal> findByProjectOrderByCreatedAtDesc(Project project, Limit limit);

    @EntityGraph(attributePaths = {"run", "run.requestedBy", "decidedBy"})
    List<AgentProposal> findByProjectAndStatusOrderByCreatedAtDesc(Project project, AgentProposal.Status status, Limit limit);

    @EntityGraph(attributePaths = {"run", "run.requestedBy", "decidedBy", "results"})
    Optional<AgentProposal> findByIdAndProject(UUID id, Project project);

    /** 채택과 거절은 카드 행을 잠근 뒤 상태를 확인한다. 동시에 두 번 채택해도 한 번만 만들어진다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from AgentProposal p where p.id = :id and p.project = :project")
    Optional<AgentProposal> lockByIdAndProject(@Param("id") UUID id, @Param("project") Project project);

    long countByProjectAndStatus(Project project, AgentProposal.Status status);

    /** 이 프로젝트에서 제안 카드의 채택으로 만들어진 기록 전부. 화면이 "AI 제안" 표시를 붙일 때 쓴다 */
    @Query("select r from AgentProposal p join p.results r where p.project = :project")
    List<AgentProposal.Result> findResultsByProject(@Param("project") Project project);

    /** 이 기록이 제안 카드의 채택으로 만들어졌는가(K-30 7장) */
    @Query("select count(p) > 0 from AgentProposal p join p.results r "
            + "where p.project.id = :projectId and r.targetType = :targetType and r.targetId = :targetId")
    boolean existsResult(@Param("projectId") UUID projectId, @Param("targetType") String targetType, @Param("targetId") UUID targetId);
}
