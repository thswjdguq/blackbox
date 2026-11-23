package com.blackbox.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** A의 V40 수신 설정을 읽고 쓴다. 행이 없으면 동의하지 않은 상태이며 읽기만으로 기록을 만들지 않는다. */
@Repository
public class EmailNotificationPreferenceStore {
    private static final String COLUMNS = "enabled, notice_version, enabled_at, disabled_at, updated_at";
    private static final RowMapper<Preference> ROW = (rs, row) -> new Preference(rs.getBoolean("enabled"),
            rs.getString("notice_version"), rs.getObject("enabled_at", OffsetDateTime.class),
            rs.getObject("disabled_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
    private final JdbcTemplate jdbc;

    public EmailNotificationPreferenceStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public Optional<Preference> find(UUID userId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM email_notification_preferences WHERE user_id=?",
                ROW, Objects.requireNonNull(userId)).stream().findFirst();
    }

    /** 처음 켜는 동시 요청도 같은 계정 행으로 직렬화한다. 같은 값은 UPDATE하지 않아 DB 트리거의 시각도 보존한다. */
    @Transactional
    public Preference update(UUID userId, boolean enabled, String noticeVersion, OffsetDateTime at) {
        if (jdbc.query("SELECT id FROM users WHERE id=? FOR UPDATE", (rs, row) -> rs.getObject(1, UUID.class),
                Objects.requireNonNull(userId)).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인 정보를 다시 확인해 주세요.");
        }
        var existing = find(userId);
        var previous = existing.orElseGet(Preference::off);
        if (previous.enabled() == enabled) return previous;
        var next = previous.transition(enabled, noticeVersion, at);
        // V40 UPDATE 트리거가 updated_at을 정하므로 Java 예상 값 대신 저장된 행을 돌려준다.
        if (existing.isEmpty()) {
            return jdbc.queryForObject("INSERT INTO email_notification_preferences(user_id," + COLUMNS
                    + ") VALUES (?,?,?,?,?,?) RETURNING " + COLUMNS, ROW, userId, next.enabled(),
                    next.noticeVersion(), next.enabledAt(), next.disabledAt(), next.updatedAt());
        }
        return jdbc.queryForObject("UPDATE email_notification_preferences SET enabled=?, notice_version=?,"
                + " enabled_at=?, disabled_at=?, updated_at=? WHERE user_id=? RETURNING " + COLUMNS,
                ROW, next.enabled(), next.noticeVersion(), next.enabledAt(), next.disabledAt(), next.updatedAt(), userId);
    }

    public record Preference(boolean enabled, String noticeVersion, OffsetDateTime enabledAt,
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
