package com.blackbox.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 저장 엔티티가 아닌, 획득한 발송 대기와 결과 저장용 fencing 값. 주소·본문은 읽지 않는다. */
public record EmailDeliveryLease(UUID id, UUID projectId, UUID recipientId, UUID actorId,
        String actorName, String eventType, String eventKey, UUID taskId, UUID deliverableId,
        UUID reviewId, String targetTitle, OffsetDateTime createdAt, OffsetDateTime claimedAt, int attemptCount) {
    @Override public String toString() { return "EmailDeliveryLease[id=" + id + ", attempt=" + attemptCount + "]"; }
}
