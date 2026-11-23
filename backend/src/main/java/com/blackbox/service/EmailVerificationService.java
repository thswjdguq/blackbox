package com.blackbox.service;

import com.blackbox.config.MailNotificationProperties;
import com.blackbox.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * 계정 이메일 주소 확인(K-41 0.5 9·10장). 사용자가 프로필에서 요청할 때만 확인 메일을 보낸다.
 * 토큰 원문은 메일 링크에만 있고 DB에는 SHA-256만 둔다. 확인에 성공해도 로그인이나 다른 권한을 주지 않는다.
 * 사용자에게 보이는 문장은 이 클래스의 상수 하나로 관리한다(B의 수신 설정 응답도 availability의 문장을 그대로 쓴다).
 */
@Service
public class EmailVerificationService {
    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    public static final String REQUESTED = "확인 메일 발송을 요청했습니다.";
    public static final String UNAVAILABLE = "현재 이메일 주소 인증 기능을 사용할 수 없습니다.";
    public static final String TOO_MANY = "확인 메일 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.";
    public static final String INVALID = "이 계정에서 사용할 수 없는 확인 링크입니다. 메일을 받은 계정으로 로그인했는지 확인해 주세요.";
    public static final String EXPIRED = "확인 링크가 만료됐습니다. 확인 메일을 다시 요청해 주세요.";
    public static final String ADDRESS_CHANGED = "이메일 주소가 변경되어 링크를 사용할 수 없습니다. 현재 주소로 확인 메일을 다시 요청해 주세요.";

    static final Duration TOKEN_TTL = Duration.ofMinutes(15);
    static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    static final int PER_ACCOUNT_PER_HOUR = 5;
    // 서비스 Gmail 한 계정의 하루 발송 한도를 업무 메일과 나눠 쓰므로, 확인 메일은 서비스 전체에서 하루 이만큼만 보낸다
    static final int SERVICE_PER_DAY = 100;

    /** B의 수신 설정 응답(verificationAvailable·verificationUnavailableReason)이 읽는 값. 가능하면 reason은 null이다 */
    public record Availability(boolean available, String reason) {}

    public enum Outcome { REQUESTED, ALREADY_VERIFIED }

    /** 사용자에게 보여 줄 문장과 상태. retryAfterSeconds는 429일 때만 있다 */
    public static class Rejected extends RuntimeException {
        private final HttpStatus status;
        private final Long retryAfterSeconds;
        Rejected(HttpStatus status, String detail, Long retryAfterSeconds) {
            super(detail, null, false, false);   // 사용자 문장이라 스택이 필요 없다
            this.status = status; this.retryAfterSeconds = retryAfterSeconds;
        }
        public HttpStatus status() { return status; }
        public Long retryAfterSeconds() { return retryAfterSeconds; }
    }

    private record Token(UUID id, UUID userId, String email, OffsetDateTime expiresAt, OffsetDateTime usedAt) {}
    private record Issued(UUID id, String email) {}

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final MailNotificationProperties settings;
    private final ObjectProvider<EmailNotificationTransport> transports;
    private final String frontendBaseUrl;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public EmailVerificationService(JdbcTemplate jdbc, PlatformTransactionManager transactions, MailNotificationProperties settings,
                                    ObjectProvider<EmailNotificationTransport> transports,
                                    @Value("${app.frontend-base-url:}") String frontendBaseUrl) {
        this(jdbc, transactions, settings, transports, frontendBaseUrl, Clock.systemUTC());
    }

    EmailVerificationService(JdbcTemplate jdbc, PlatformTransactionManager transactions, MailNotificationProperties settings,
                             ObjectProvider<EmailNotificationTransport> transports, String frontendBaseUrl, Clock clock) {
        this.jdbc = jdbc; this.tx = new TransactionTemplate(transactions); this.settings = settings;
        this.transports = transports; this.frontendBaseUrl = frontendBaseUrl; this.clock = clock;
    }

    /** 지금 이 사용자가 확인 메일을 요청할 수 있는지. 메일을 보내거나 SMTP에 접속하지 않는다. 요청 횟수 제한은 요청할 때 429로 알린다 */
    public Availability availability(User user) {
        boolean ready = origin().isPresent() && transports.getIfAvailable() != null
                && new EmailNotificationPolicy(settings.isEnabled(), settings.getRecipientAllowlist()).allowsAddress(user.getEmail());
        return ready ? new Availability(true, null) : new Availability(false, UNAVAILABLE);
    }

    public Outcome request(User user) {
        if (!availability(user).available()) throw new Rejected(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE, null);
        String token = newToken();
        Issued issued = tx.execute(status -> {
            // 같은 사용자의 동시 요청이 둘 다 간격 검사를 통과해 메일 두 통을 만들지 않게 사용자 행을 잠근다
            var row = jdbc.queryForMap("SELECT email, email_verified_at FROM users WHERE id = ? FOR UPDATE", user.getId());
            if (row.get("email_verified_at") != null) return null;
            OffsetDateTime now = now();
            long wait = retryAfterSeconds(user.getId(), now);
            if (wait > 0) throw new Rejected(HttpStatus.TOO_MANY_REQUESTS, TOO_MANY, wait);
            String email = (String) row.get("email");
            UUID id = jdbc.queryForObject("""
                    INSERT INTO email_verification_tokens(user_id, email, token_hash, created_at, expires_at)
                    VALUES (?, ?, ?, ?, ?) RETURNING id""", UUID.class,
                    user.getId(), email, hash(token), now, now.plus(TOKEN_TTL));
            return new Issued(id, email);
        });
        if (issued == null) return Outcome.ALREADY_VERIFIED;
        // 커밋한 뒤에 보낸다. SMTP 동안 사용자 행을 잠그지 않는다. 실패해도 발급 기록은 남겨 요청 횟수에 넣는다
        var transport = transports.getIfAvailable();
        var result = EmailNotificationTransport.Result.UNKNOWN_OUTCOME;
        try {
            if (transport != null) result = transport.send(issued.id(), issued.email(), content(origin().orElseThrow(), token));
        } catch (RuntimeException e) {   // 검사 직후 발송이 꺼진 경우 등. 500이 아니라 사용 불가로 답한다
            log.warn("주소 확인 메일 발송 예외 user={} 예외={}", user.getId(), e.getClass().getSimpleName());
        }
        log.info("주소 확인 메일 user={} 결과={}", user.getId(), result);
        if (result != EmailNotificationTransport.Result.ACCEPTED) throw new Rejected(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE, null);
        return Outcome.REQUESTED;
    }

    public void confirm(User user, String token) {
        if (token == null || token.isBlank() || token.length() > 200) throw new Rejected(HttpStatus.BAD_REQUEST, INVALID, null);
        tx.executeWithoutResult(status -> {
            Token t = jdbc.query("""
                    SELECT id, user_id, email, expires_at, used_at FROM email_verification_tokens
                    WHERE token_hash = ? FOR UPDATE""", (rs, n) -> new Token(rs.getObject("id", UUID.class),
                    rs.getObject("user_id", UUID.class), rs.getString("email"),
                    rs.getObject("expires_at", OffsetDateTime.class), rs.getObject("used_at", OffsetDateTime.class)),
                    hash(token.strip())).stream().findFirst().orElse(null);
            // 없는 토큰·다른 계정의 토큰·이미 쓴 토큰은 같은 문장으로 답한다(남의 링크가 있는지 드러내지 않는다)
            if (t == null || !t.userId().equals(user.getId()) || t.usedAt() != null)
                throw new Rejected(HttpStatus.BAD_REQUEST, INVALID, null);
            OffsetDateTime now = now();
            if (!t.expiresAt().isAfter(now)) throw new Rejected(HttpStatus.GONE, EXPIRED, null);
            String current = jdbc.queryForObject("SELECT email FROM users WHERE id = ? FOR UPDATE", String.class, user.getId());
            if (!t.email().equals(current)) throw new Rejected(HttpStatus.CONFLICT, ADDRESS_CHANGED, null);
            jdbc.update("UPDATE email_verification_tokens SET used_at = ? WHERE id = ?", now, t.id());
            // 이미 확인된 주소면 처음 확인한 시각을 유지한다(그 뒤의 알림이 늦게 확인한 것으로 밀리지 않게)
            jdbc.update("UPDATE users SET email_verified_at = COALESCE(email_verified_at, ?) WHERE id = ?", now, user.getId());
        });
    }

    /** 계정별 60초 간격·시간당 5회, 서비스 전체 하루 100회. 넘었으면 다시 요청할 수 있을 때까지 남은 초, 아니면 0 */
    private long retryAfterSeconds(UUID userId, OffsetDateTime now) {
        OffsetDateTime hourAgo = now.minusHours(1), dayAgo = now.minusDays(1);
        Window account = jdbc.queryForObject("""
                SELECT max(created_at) AS last, count(*) FILTER (WHERE created_at > ?) AS n,
                       min(created_at) FILTER (WHERE created_at > ?) AS first
                FROM email_verification_tokens WHERE user_id = ?""", Window::of, hourAgo, hourAgo, userId);
        Window service = jdbc.queryForObject("""
                SELECT NULL::timestamptz AS last, count(*) AS n, min(created_at) AS first
                FROM email_verification_tokens WHERE created_at > ?""", Window::of, dayAgo);
        long wait = 0;
        if (account.last() != null) wait = Math.max(wait, secondsUntil(account.last().plus(RESEND_INTERVAL), now));
        if (account.count() >= PER_ACCOUNT_PER_HOUR) wait = Math.max(wait, secondsUntil(account.first().plusHours(1), now));
        // ponytail: 서비스 전체 예산은 잠그지 않고 센다. 동시에 몰리면 몇 건 넘을 수 있다(사용자 수가 적어 충분하다)
        if (service.count() >= SERVICE_PER_DAY) wait = Math.max(wait, secondsUntil(service.first().plusDays(1), now));
        return wait;
    }

    /** 기간 안의 발급 수와 가장 이른·늦은 발급 시각 */
    private record Window(OffsetDateTime last, long count, OffsetDateTime first) {
        static Window of(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
            return new Window(rs.getObject("last", OffsetDateTime.class), rs.getLong("n"), rs.getObject("first", OffsetDateTime.class));
        }
    }

    private static long secondsUntil(OffsetDateTime until, OffsetDateTime now) {
        long millis = Duration.between(now, until).toMillis();
        return millis <= 0 ? 0 : (millis + 999) / 1000;
    }

    /** 메일 링크의 출처. 업무 메일과 같은 규칙(https, 개발은 localhost)을 통과해야 한다 */
    private Optional<URI> origin() {
        try {
            URI uri = URI.create(frontendBaseUrl == null ? "" : frontendBaseUrl.strip());
            new EmailNotificationComposer(uri);   // 출처 규칙만 검사한다
            return Optional.of(uri);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static EmailNotificationComposer.Content content(URI origin, String token) {
        // 토큰은 fragment로만 싣는다. 서버 접근 로그와 next·쿼리에 남지 않는다(K-41 9장)
        URI link = origin.resolve("/email/verify#token=" + token);
        return new EmailNotificationComposer.Content("[Team Blackbox] 이메일 주소 확인",
                "Team Blackbox 계정의 이메일 주소를 확인하려면 아래 링크를 열어 주세요.\n"
                        + "링크는 " + TOKEN_TTL.toMinutes() + "분 동안 한 번만 사용할 수 있습니다.\n" + link
                        + "\n\n직접 요청하지 않았다면 이 메일을 무시해 주세요. 주소를 확인하지 않으면 업무 알림 메일은 보내지 않습니다.", link);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);   // SHA-256은 모든 JDK에 있다
        }
    }

    private OffsetDateTime now() { return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS); }
}
