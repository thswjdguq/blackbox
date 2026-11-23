package com.blackbox.service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/** A의 실제 인증/요청 가용 상태를 B API에 연결하는 소비자 포트. 인증/SMTP를 중복 구현하지 않는다. */
public interface EmailNotificationVerificationReader {
    Optional<Status> current(UUID userId);

    /** verifiedAt은 currentAddress에 묶인 인증이며 available은 연결 시험/배달 성공을 뜻하지 않는다. */
    record Status(String currentAddress, OffsetDateTime verifiedAt, boolean available, String unavailableReason) {
        @Override public String toString() { return "EmailNotificationVerificationStatus[redacted]"; }
    }
}
