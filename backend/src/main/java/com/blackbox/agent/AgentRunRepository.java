package com.blackbox.agent;

import com.blackbox.entity.Project;
import com.blackbox.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface AgentRunRepository extends JpaRepository<AgentRun, UUID> {
    boolean existsByProjectAndRequestedByAndStatusAndStartedAtAfter(Project project, User requestedBy, AgentRun.Status status, OffsetDateTime since);

    /**
     * 같은 사람의 실행이 아직 도는 중인가. since보다 먼저 시작한 실행은 보지 않는다.
     * 서버가 중간에 죽어 끝나지 못한 실행 때문에 계속 막히지 않게 하려는 것이다(K-30 2장)
     */
    default boolean hasRunning(Project project, User requestedBy, OffsetDateTime since) {
        return existsByProjectAndRequestedByAndStatusAndStartedAtAfter(project, requestedBy, AgentRun.Status.RUNNING, since);
    }

    /**
     * 같은 프로젝트의 실행 시작을 차례대로 처리하기 위한 행 잠금. 실행을 시작할 수 있는지 확인하는 것과 실행 기록을 만드는 것이
     * 한 묶음이 되어, 동시에 들어온 요청이 같은 빈자리를 함께 차지하지 못한다. 트랜잭션 안에서 부른다
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :id")
    Optional<Project> lockProject(@Param("id") UUID id);

    /** since 이후에 시작한 실행 수. 프로젝트의 시간당 상한에 쓴다 */
    long countByProjectAndStartedAtAfter(Project project, OffsetDateTime since);
}
