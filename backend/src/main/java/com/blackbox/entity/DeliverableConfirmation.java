package com.blackbox.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * 제출물의 최종 확정(K-20). 제출물마다 하나이고, 만든 뒤에는 바뀌지 않는다.
 * 확정본은 회차가 가리키는 파일 버전이다. 회차의 대상은 바뀌지 않으므로 파일 버전을 따로 저장하지 않는다.
 */
@Entity @Table(name = "deliverable_confirmations") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliverableConfirmation {
    @Id @Column(name = "deliverable_id") private UUID deliverableId;
    // 키를 제출물에서 받는다. 저장 전에는 키가 비어 있어, 같은 제출물을 다시 저장하면 덮어쓰지 않고 DB가 거절한다
    @MapsId @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deliverable_id") private Deliverable deliverable;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false, updatable = false) private ReviewRound review;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "confirmed_by", nullable = false, updatable = false) private User confirmedBy;
    @Column(name = "confirmed_at", nullable = false, updatable = false) private OffsetDateTime confirmedAt;

    /** 승인된 회차로 확정한다. 제출물과 프로젝트는 회차에서 가져오므로 다른 제출물의 회차로 확정할 수 없다 */
    public static DeliverableConfirmation of(ReviewRound approved, User confirmedBy, OffsetDateTime now) {
        DeliverableConfirmation c = new DeliverableConfirmation();
        c.deliverable = approved.getDeliverable();
        c.project = approved.getProject();
        c.review = approved;
        c.confirmedBy = confirmedBy;
        c.confirmedAt = stored(now);
        return c;
    }

    /** DB가 돌려주는 모양(UTC, 마이크로초)으로 맞춘다. 방금 만든 기록과 다시 읽은 기록이 같은 값을 낸다. */
    static OffsetDateTime stored(OffsetDateTime time) {
        return time.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }
}
