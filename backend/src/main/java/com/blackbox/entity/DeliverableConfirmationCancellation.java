package com.blackbox.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 취소한 확정(K-20 0.3). 확정 기록을 지우기 전에 확정했던 회차·사람·시각을 옮겨 두고 취소한 사람·시각·사유를 더한다.
 * 만든 뒤에는 바뀌지 않는다.
 */
@Entity @Table(name = "deliverable_confirmation_cancellations") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliverableConfirmationCancellation {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deliverable_id", nullable = false, updatable = false) private Deliverable deliverable;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false, updatable = false) private ReviewRound review;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "confirmed_by", nullable = false, updatable = false) private User confirmedBy;
    @Column(name = "confirmed_at", nullable = false, updatable = false) private OffsetDateTime confirmedAt;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cancelled_by", nullable = false, updatable = false) private User cancelledBy;
    @Column(name = "cancelled_at", nullable = false, updatable = false) private OffsetDateTime cancelledAt;
    @Column(nullable = false, length = 1000, updatable = false) private String reason;

    public static DeliverableConfirmationCancellation of(DeliverableConfirmation confirmed, User cancelledBy, String reason,
                                                         OffsetDateTime now) {
        DeliverableConfirmationCancellation c = new DeliverableConfirmationCancellation();
        c.deliverable = confirmed.getDeliverable();
        c.project = confirmed.getProject();
        c.review = confirmed.getReview();
        c.confirmedBy = confirmed.getConfirmedBy();
        c.confirmedAt = confirmed.getConfirmedAt();
        c.cancelledBy = cancelledBy;
        c.cancelledAt = DeliverableConfirmation.stored(now);
        c.reason = reason.trim();
        return c;
    }
}
