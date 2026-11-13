package com.blackbox.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 제출물에 건 과제 안내문(K-31 3장). 걸고 떼기만 하고 고치지 않는다. */
@Entity @Table(name = "deliverable_briefs") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliverableBrief {
    @Embeddable
    public record Key(@Column(name = "deliverable_id") UUID deliverableId, @Column(name = "brief_id") UUID briefId) {}

    @EmbeddedId private Key id;
    @MapsId("deliverableId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deliverable_id") private Deliverable deliverable;
    @MapsId("briefId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brief_id") private ProjectBrief brief;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "linked_by", nullable = false, updatable = false) private User linkedBy;
    @Column(name = "linked_at", nullable = false, updatable = false) private OffsetDateTime linkedAt;

    public DeliverableBrief(Deliverable deliverable, ProjectBrief brief, User by, OffsetDateTime now) {
        id = new Key(deliverable.getId(), brief.getId());
        this.deliverable = deliverable; this.brief = brief; linkedBy = by; linkedAt = now;
    }
}
