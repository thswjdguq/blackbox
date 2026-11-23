package com.blackbox.service;

import com.blackbox.dto.EmailNotificationSettingsDtos;
import com.blackbox.exception.CodedException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 프로필 수신 의향만 저장한다. 인증 토큰 발급·SMTP 호출·과거 알림 재발행은 하지 않는다. */
@Service
public class EmailNotificationSettingsService {
    public static final String NOTICE_VERSION = "K-41-0.5";
    private final ObjectProvider<EmailNotificationPreferenceStore> preferences;
    private final ObjectProvider<EmailNotificationVerificationReader> verifications;
    private final Clock clock;

    @Autowired
    public EmailNotificationSettingsService(ObjectProvider<EmailNotificationPreferenceStore> preferences,
            ObjectProvider<EmailNotificationVerificationReader> verifications) {
        this(preferences, verifications, Clock.systemUTC());
    }

    EmailNotificationSettingsService(ObjectProvider<EmailNotificationPreferenceStore> preferences,
            ObjectProvider<EmailNotificationVerificationReader> verifications, Clock clock) {
        this.preferences = preferences; this.verifications = verifications; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public EmailNotificationSettingsDtos.View get(UUID userId) {
        var sources = sources();
        var verification = verification(sources, userId);
        return view(sources.preferences().find(userId).orElseGet(EmailNotificationPreferenceStore.Preference::off), verification);
    }

    @Transactional
    public EmailNotificationSettingsDtos.View update(UUID userId, boolean enabled) {
        var sources = sources();
        // 조회 실패를 미인증/저장 성공으로 바꾸지 않는다. 실제 구현은 같은 트랜잭션의 현재 상태를 읽는다.
        var verification = verification(sources, userId);
        return view(sources.preferences().update(userId, enabled, NOTICE_VERSION, OffsetDateTime.now(clock)), verification);
    }

    private record Sources(EmailNotificationPreferenceStore preferences, EmailNotificationVerificationReader verifications) {}

    private Sources sources() {
        var store = preferences.getIfAvailable();
        var reader = verifications.getIfAvailable();
        if (store == null || reader == null) throw new CodedException(HttpStatus.SERVICE_UNAVAILABLE,
                "EMAIL_SETTINGS_UNAVAILABLE", "이메일 알림 설정 기능을 준비 중입니다.", Map.of());
        return new Sources(store, reader);
    }

    private EmailNotificationVerificationReader.Status verification(Sources sources, UUID userId) {
        Objects.requireNonNull(userId);
        var current = sources.verifications().current(userId).orElseThrow(() ->
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
