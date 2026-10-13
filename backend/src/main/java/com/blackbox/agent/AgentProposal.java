package com.blackbox.agent;

import com.blackbox.entity.Project;
import com.blackbox.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * 제안 카드. 사람이 채택할 때만 실제 기록이 만들어진다(K-30 2장).
 * 카드는 내용이 어떤 모양인지 모른다. 종류(kind)마다의 검증과 채택은 카드 밖에서 등록으로 더한다.
 * 상태는 여기 있는 메서드로만 바뀌고, 결정된 카드는 고치거나 다시 결정할 수 없다.
 */
@Entity @Table(name = "agent_proposals") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentProposal {
    public enum Status { PENDING, ACCEPTED, REJECTED }

    private static final int TITLE_MAX = 255;   // title 열의 길이

    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false) private AgentRun run;
    // 카드 종류는 등록으로 늘어나므로 열거형으로 묶지 않는다
    @Column(nullable = false, length = 40) private String kind;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status = Status.PENDING;
    @Column(nullable = false, length = TITLE_MAX) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String rationale;
    // 채택하면 만들어질 내용. 사람이 고친 것이 들어간다
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false) private JsonNode content;
    // 모델이 만든 원본. 저장한 뒤 바꾸지 않는다
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, updatable = false) private JsonNode original;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "decided_by") private User decidedBy;
    @Column(name = "decided_at") private OffsetDateTime decidedAt;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    // 목록에서 카드마다 따로 조회하지 않도록 여러 카드의 결과를 한 번에 읽는다.
    // 한 번에 읽는 묶음의 크기일 뿐이라, 목록이 이보다 길어져도 조회가 한두 번 늘 뿐 결과는 같다
    @ElementCollection @BatchSize(size = 50) @Getter(AccessLevel.NONE)
    @CollectionTable(name = "agent_proposal_results", joinColumns = @JoinColumn(name = "proposal_id"))
    private Set<Result> results = new LinkedHashSet<>();

    /**
     * 채택으로 만들어진 기록 하나. 대상의 종류(targetType)도 카드 종류와 함께 늘어나므로 문자열로 둔다.
     * 대상 테이블을 직접 가리키지 않아, 에이전트가 제출물·업무의 구조를 몰라도 된다.
     */
    @Embeddable
    public record Result(@Column(name = "target_type", nullable = false, length = 40) String targetType,
                         @Column(name = "target_id", nullable = false) UUID targetId) {}

    /** 실행이 만든 카드. 프로젝트는 실행에서 가져오므로 둘이 어긋날 수 없다 */
    public static AgentProposal propose(AgentRun run, String kind, String title, String rationale, JsonNode content, OffsetDateTime now) {
        AgentProposal p = new AgentProposal();
        p.project = run.getProject(); p.run = run; p.kind = kind; p.rationale = rationale;
        // 제목은 모델이 쓰므로 길이를 믿지 않는다. 넘치면 저장이 실패하는 대신 잘라 둔다
        p.title = title.length() > TITLE_MAX ? title.substring(0, TITLE_MAX) : title;
        p.content = content; p.original = content.deepCopy(); p.createdAt = now;
        return p;
    }

    public void edit(JsonNode newContent) { requirePending(); content = newContent; }

    public void accept(User by, Collection<Result> created, OffsetDateTime now) {
        requirePending();
        status = Status.ACCEPTED; decidedBy = by; decidedAt = now; results.addAll(created);
    }

    public void reject(User by, OffsetDateTime now) {
        requirePending();
        status = Status.REJECTED; decidedBy = by; decidedAt = now;
    }

    public boolean isPending() { return status == Status.PENDING; }

    /** 사람이 모델의 원본을 고쳤는가 */
    public boolean isEdited() { return !content.equals(original); }

    public Set<Result> getResults() { return Collections.unmodifiableSet(results); }

    private void requirePending() {
        if (status != Status.PENDING) throw new IllegalStateException("이미 결정된 제안입니다");
    }
}
