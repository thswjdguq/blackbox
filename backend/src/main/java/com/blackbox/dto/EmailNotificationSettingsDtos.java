package com.blackbox.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

/** K-41 8장. 주소/인증 상태/대상 사용자 id를 입력으로 사용하지 않는다. */
public final class EmailNotificationSettingsDtos {
    private EmailNotificationSettingsDtos() {}

    public record View(boolean enabled, boolean emailVerified, boolean verificationRequired,
            boolean verificationAvailable, String verificationUnavailableReason, OffsetDateTime updatedAt) {}

    /** Object로 받아 JSON 문자열/숫자의 Boolean 자동 변환을 허용하지 않는다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Update(Object enabled) {
        public boolean requiredEnabled() {
            if (enabled instanceof Boolean value) return value;
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "enabled는 true 또는 false로 입력해 주세요.");
        }
    }
}
