package com.blackbox.agent;

import com.blackbox.entity.Project;
import com.blackbox.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.OffsetDateTime;
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

    /** since 이후에 시작한 실행 수. 프로젝트의 시간당 상한에 쓴다 */
    long countByProjectAndStartedAtAfter(Project project, OffsetDateTime since);
}
