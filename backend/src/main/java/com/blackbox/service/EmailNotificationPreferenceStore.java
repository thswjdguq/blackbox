package com.blackbox.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** V40 실제 스키마가 제공된 뒤 구현할 B 저장 경계. 메모리 저장 대역은 테스트에만 둔다. */
public interface EmailNotificationPreferenceStore {
    Optional<Preference> find(UUID userId);

    /** 동시 변경을 직렬화한 현재 값에 transition을 적용한다. 동일 값의 시각/동의 버전은 보존한다. */
    Preference update(UUID userId, boolean enabled, String noticeVersion, OffsetDateTime at);

    record Preference(boolean enabled, String noticeVersion, OffsetDateTime enabledAt,
            OffsetDateTime disabledAt, OffsetDateTime updatedAt) {
        public static Preference off() { return new Preference(false, null, null, null, null); }

        public Preference transition(boolean nextEnabled, String version, OffsetDateTime at) {
            if (enabled == nextEnabled) return this;
            OffsetDateTime changed = Objects.requireNonNull(at).withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
            if (nextEnabled && (version == null || version.isBlank())) throw new IllegalArgumentException("동의 문구 버전이 필요합니다.");
            return new Preference(nextEnabled, nextEnabled ? version : noticeVersion,
                    nextEnabled ? changed : enabledAt, nextEnabled ? disabledAt : changed, changed);
        }
    }
}
