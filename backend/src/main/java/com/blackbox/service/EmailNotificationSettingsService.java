package com.blackbox.service;

import com.blackbox.dto.EmailNotificationSettingsDtos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** 프로필 수신 의향만 저장한다. 인증 토큰 발급·SMTP 호출·과거 알림 재발행은 하지 않는다. */
@Service
public class EmailNotificationSettingsService {
    public static final String NOTICE_VERSION = "K-41-0.5";
    private final EmailNotificationPreferenceStore preferences;
    private final EmailNotificationVerificationReader verifications;
    private final Clock clock;

    @Autowired
    public EmailNotificationSettingsService(EmailNotificationPreferenceStore preferences,
            EmailNotificationVerificationReader verifications) {
        this(preferences, verifications, Clock.systemUTC());
    }

    EmailNotificationSettingsService(EmailNotificationPreferenceStore preferences,
            EmailNotificationVerificationReader verifications, Clock clock) {
        this.preferences = Objects.requireNonNull(preferences); this.verifications = Objects.requireNonNull(verifications);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(readOnly = true)
    public EmailNotificationSettingsDtos.View get(UUID userId) {
        var verification = verification(userId);
        return view(preferences.find(userId).orElseGet(EmailNotificationPreferenceStore.Preference::off), verification);
    }

    @Transactional
    public EmailNotificationSettingsDtos.View update(UUID userId, boolean enabled) {
        // 조회 실패를 미인증/저장 성공으로 바꾸지 않는다. 실제 구현은 같은 트랜잭션의 현재 상태를 읽는다.
        var verification = verification(userId);
        return view(preferences.update(userId, enabled, NOTICE_VERSION, OffsetDateTime.now(clock)), verification);
    }

    private EmailNotificationVerificationReader.Status verification(UUID userId) {
        Objects.requireNonNull(userId);
        var current = verifications.current(userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인 정보를 다시 확인해 주세요."));
        if (current.currentAddress() == null || current.currentAddress().isBlank()
                || !current.available() && (current.unavailableReason() == null || current.unavailableReason().isBlank())) {
            throw new IllegalStateException("이메일 인증 조회 계약에 필요한 값이 없습니다.");
        }
        return current;
    }

    private EmailNotificationSettingsDtos.View view(EmailNotificationPreferenceStore.Preference preference,
            EmailNotificationVerificationReader.Status verification) {
        Objects.requireNonNull(preference);
        boolean verified = verification.verifiedAt() != null;
        return new EmailNotificationSettingsDtos.View(preference.enabled(), verified, preference.enabled() && !verified,
                verification.available(), verification.available() ? null : verification.unavailableReason(), preference.updatedAt());
    }
}
