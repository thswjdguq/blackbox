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

    /**
     * 안내문 하나의 수정·삭제·걸기·떼기(K-31 2.3·2.4·3장)를 차례대로 처리하는 행 잠금. 트랜잭션 안에서 부른다.
     * 이 잠금을 잡은 뒤에 제출물 행을 잠근다. 안내문 행을 잠그는 요청은 모두 이 순서를 따르므로, 그 요청들 사이에는 교착 상태가 생기지 않는다.
     * PESSIMISTIC_WRITE는 PostgreSQL에서 FOR NO KEY UPDATE가 되어, 이 안내문을 가리키는 줄의 삽입(외래 키 확인의 FOR KEY SHARE)을 막지 못한다.
     * 그래서 FOR UPDATE를 직접 쓴다. 삭제가 검토 대기 카드 수를 센 뒤 그 안내문을 근거로 한 카드가 저장되면, 삭제가 그 근거 줄까지 지우기 때문이다
     */
    @Query(value = "select * from project_briefs where id = :id and project_id = :projectId for update", nativeQuery = true)
    Optional<ProjectBrief> lockByIdAndProject(@Param("id") UUID id, @Param("projectId") UUID projectId);

    /** 안내문마다 그것을 근거로 한 검토 대기 제안 카드의 수. [brief_id, count] 줄이고, 카드가 없는 안내문은 줄이 없다 */
    @Query(value = "select b.brief_id, count(*) from agent_proposal_briefs b join agent_proposals p on p.id = b.proposal_id "
            + "where p.project_id = :projectId and p.status = 'PENDING' group by b.brief_id", nativeQuery = true)
    List<Object[]> countPendingProposals(@Param("projectId") UUID projectId);
}
