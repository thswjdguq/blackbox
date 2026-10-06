package com.blackbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 검토 회차(K-10). 열 때 고른 파일 버전을 가리키며, 이후 파일이 바뀌어도 대상은 바뀌지 않는다. */
@Entity @Table(name = "review_rounds") @Getter @Setter
public class ReviewRound {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deliverable_id", nullable = false) private Deliverable deliverable;
    @Column(name = "round_no", nullable = false) private int roundNo;
    // 파일 없는 중간 검토면 null
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "file_id") private FileVault file;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opened_by", nullable = false) private User openedBy;
    @Column(name = "opened_at", nullable = false) private OffsetDateTime openedAt = OffsetDateTime.now();
    // null(결정 전) · APPROVED · CHANGES_REQUESTED
    @Column(length = 20) private String decision;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "decided_by") private User decidedBy;
    @Column(name = "decided_at") private OffsetDateTime decidedAt;

    public void decide(String decision, User user) { this.decision = decision; decidedBy = user; decidedAt = OffsetDateTime.now(); }
}
