package com.blackbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name = "deliverable_requirements") @Getter @Setter
public class DeliverableRequirement {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deliverable_id", nullable = false) private Deliverable deliverable;
    @Column(nullable = false, length = 1000) private String content;
    @Column(nullable = false) private boolean required = true;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "assessed_by") private User assessedBy;
    @Column(name = "assessed_at") private OffsetDateTime assessedAt;

    public void assess(User user) { assessedBy = user; assessedAt = OffsetDateTime.now(); }
    public void clearAssessment() { assessedBy = null; assessedAt = null; }
}
