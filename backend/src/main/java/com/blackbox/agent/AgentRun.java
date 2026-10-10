package com.blackbox.agent;

import com.blackbox.entity.Project;
import com.blackbox.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 에이전트 실행 한 번. 안내문과 질문의 원문은 남기지 않고 종류와 결과만 남긴다(K-30 8장).
 * 상태는 여기 있는 메서드로만 바뀐다. 끝난 실행을 다시 끝내는 것 같은 잘못된 전환을 한 곳에서 막는다.
 */
@Entity @Table(name = "agent_runs") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentRun {
    public enum Status { RUNNING, DONE, FAILED }

    private static final int ERROR_MAX = 500;   // error 열의 길이

    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false) private User requestedBy;
    // 실행 종류는 등록으로 늘어나므로 열거형으로 묶지 않는다(K-30 2장)
    @Column(nullable = false, length = 40) private String skill;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status = Status.RUNNING;
    @Column(length = ERROR_MAX) private String error;
    @Column(name = "started_at", nullable = false) private OffsetDateTime startedAt;
    @Column(name = "finished_at") private OffsetDateTime finishedAt;

    public static AgentRun start(Project project, User requestedBy, String skill, OffsetDateTime now) {
        AgentRun run = new AgentRun();
        run.project = project; run.requestedBy = requestedBy; run.skill = skill; run.startedAt = now;
        return run;
    }

    public void finish(OffsetDateTime now) { end(Status.DONE, null, now); }

    public void fail(String reason, OffsetDateTime now) {
        end(Status.FAILED, reason != null && reason.length() > ERROR_MAX ? reason.substring(0, ERROR_MAX) : reason, now);
    }

    private void end(Status to, String reason, OffsetDateTime now) {
        if (status != Status.RUNNING) throw new IllegalStateException("이미 끝난 실행입니다");
        status = to; error = reason; finishedAt = now;
    }
}
