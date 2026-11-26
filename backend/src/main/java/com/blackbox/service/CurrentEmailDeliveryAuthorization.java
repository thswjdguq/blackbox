package com.blackbox.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** 예약과 발송이 같은 현재 계정 주소·인증·수신 설정을 소비한다. 과거 대기의 자격은 State.permits가 판정한다. */
@Component
public class CurrentEmailDeliveryAuthorization implements EmailDeliveryAuthorization {
    private final EmailNotificationPreferenceStore preferences;
    private final EmailNotificationVerificationReader verifications;

    public CurrentEmailDeliveryAuthorization(EmailNotificationPreferenceStore preferences,
            EmailNotificationVerificationReader verifications) {
        this.preferences = preferences; this.verifications = verifications;
    }

    @Override @Transactional(readOnly = true)
    public Optional<State> current(UUID recipientId) {
        return verifications.current(recipientId).map(verification -> {
            var preference = preferences.find(recipientId).orElseGet(EmailNotificationPreferenceStore.Preference::off);
            return new State(verification.currentAddress(), preference.enabled(), preference.enabledAt(), verification.verifiedAt());
        });
    }
}
