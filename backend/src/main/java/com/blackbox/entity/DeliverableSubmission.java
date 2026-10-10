package com.blackbox.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 제출 기록(K-20). 사람이 학교 시스템 등에 낸 뒤 남기는 기록이고 앱이 대신 내지 않는다.
 * 확정된 제출물에 하나이고, 만든 뒤에는 바뀌지 않는다.
 */
@Entity @Table(name = "deliverable_submissions") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliverableSubmission {
    @Id @Column(name = "deliverable_id") private UUID deliverableId;
    // 키를 확정에서 받는다. 같은 제출물의 기록을 다시 저장하면 덮어쓰지 않고 DB가 거절한다
    @MapsId @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deliverable_id") private DeliverableConfirmation confirmation;
    // 낸 곳. 비어 있으면 null
    @Column(length = 500, updatable = false) private String channel;
    // 실제로 낸 시각. 기록한 시각과 다르다
    @Column(name = "submitted_at", nullable = false, updatable = false) private OffsetDateTime submittedAt;
    @Column(length = 1000, updatable = false) private String note;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by", nullable = false, updatable = false) private User recordedBy;
    @Column(name = "recorded_at", nullable = false, updatable = false) private OffsetDateTime recordedAt;

    public static DeliverableSubmission of(DeliverableConfirmation confirmation, String channel, OffsetDateTime submittedAt,
                                           String note, User recordedBy, OffsetDateTime now) {
        DeliverableSubmission s = new DeliverableSubmission();
        s.confirmation = confirmation;
        s.channel = channel;
        s.submittedAt = submittedAt;
        s.note = note;
        s.recordedBy = recordedBy;
        s.recordedAt = now;
        return s;
    }
}
