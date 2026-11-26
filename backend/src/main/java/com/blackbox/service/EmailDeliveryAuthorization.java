package com.blackbox.service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/** K-41 0.5 제안의 읽기 경계. A의 현재 주소 인증과 B의 수신 설정을 함께 읽는 실제 구현이 필요하다. */
public interface EmailDeliveryAuthorization {
    Optional<State> current(UUID recipientId);

    /** verifiedAt은 현재 계정 주소에 대한 인증만 의미한다. 주소 변경 시 이전 인증을 반환하지 않는다. */
    record State(String address, boolean enabled, OffsetDateTime enabledAt, OffsetDateTime verifiedAt) {
        public boolean permits(OffsetDateTime eventAt, String currentAddress) {
            return enabled && address != null && !address.isBlank() && eventAt != null
                    && enabledAt != null && verifiedAt != null
                    && !enabledAt.isAfter(eventAt) && !verifiedAt.isAfter(eventAt)
                    && (currentAddress == null || address.strip().equals(currentAddress));
        }
        @Override public String toString() { return "EmailDeliveryAuthorization[redacted]"; }
    }
}
