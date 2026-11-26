package com.blackbox.service;

import com.blackbox.config.MailNotificationProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.blackbox.service.EmailNotificationTransport.Result.*;

/**
 * EmailNotificationTransport의 SMTP 구현(K-41 1·3·4장). spring.mail.*의 Gmail 587·STARTTLS·인증·시간 제한으로
 * 수신자 한 명에게 plain-text UTF-8 메일 한 통을 보낸다. 같은 deliveryId는 같은 Message-ID를 쓴다.
 * 로그에는 deliveryId·결과·SMTP 응답 코드·예외 종류만 남긴다(예외 메시지에는 주소와 서버 응답 원문이 들어 있을 수 있다).
 */
@Component
public class SmtpEmailNotificationTransport implements EmailNotificationTransport {
    private static final Logger log = LoggerFactory.getLogger(SmtpEmailNotificationTransport.class);
    static final String SENDER_NAME = "Team Blackbox";

    private final JavaMailSender mail;
    private final MailNotificationProperties settings;
    private final String from;

    public SmtpEmailNotificationTransport(JavaMailSender mail, MailNotificationProperties settings,
                                          @Value("${spring.mail.username:}") String from,
                                          @Value("${spring.mail.host:}") String host,
                                          @Value("${spring.mail.properties.mail.smtp.auth:false}") boolean auth,
                                          @Value("${spring.mail.properties.mail.smtp.starttls.required:false}") boolean starttls) {
        requireProtectedUnlessLoopback(host, auth, starttls);
        this.mail = mail; this.settings = settings; this.from = from == null ? "" : from.strip();
    }

    private static final Set<String> LOOPBACK = Set.of("127.0.0.1", "localhost", "::1", "[::1]");

    /**
     * 인증·STARTTLS를 끄는 것은 메일 테스트 수신함(test-mail 프로필, loopback의 Mailpit)에서만 허용한다(K-41 11장).
     * 다른 주소에서 둘 중 하나라도 꺼져 있으면 기동을 멈춰, 설정 실수로 외부 SMTP에 평문·무인증으로 보내지 않는다
     */
    static void requireProtectedUnlessLoopback(String host, boolean auth, boolean starttls) {
        if (auth && starttls) return;
        if (host == null || !LOOPBACK.contains(host.strip().toLowerCase(Locale.ROOT)))
            throw new IllegalStateException("SMTP 인증과 STARTTLS는 loopback 주소의 메일 테스트 수신함에서만 끌 수 있습니다(spring.mail.host).");
    }

    @Override
    public Result send(UUID deliveryId, String recipient, EmailNotificationComposer.Content content) {
        Objects.requireNonNull(deliveryId); Objects.requireNonNull(content);
        // worker는 꺼져 있으면 부르지 않는다. 불렸다면 연결 실수이므로 SMTP에 닿기 전에 멈춘다
        if (!settings.isEnabled()) throw new IllegalStateException("메일 발송이 꺼져 있습니다(MAIL_ENABLED).");
        if (from.isEmpty()) return failed(deliveryId, AUTHENTICATION_FAILURE, null);   // 발송 계정(MAIL_USERNAME)이 없다
        if (recipient == null) return failed(deliveryId, PERMANENT_FAILURE, null);

        MimeMessage message;
        try {
            message = message(deliveryId, recipient, content);
        } catch (MessagingException | UnsupportedEncodingException e) {
            return failed(deliveryId, PERMANENT_FAILURE, e);   // 주소·내용으로 메일을 만들 수 없다. 다시 해도 같다
        }
        try {
            mail.send(message);
        } catch (MailException e) {
            Exception cause = e instanceof MailSendException s && !s.getFailedMessages().isEmpty()
                    ? s.getFailedMessages().values().iterator().next() : e;
            Result result = classify(e);
            if (result != ACCEPTED) return failed(deliveryId, result, cause);
        }
        log.info("메일 발송 수락 delivery={}", deliveryId);
        return ACCEPTED;
    }

    private MimeMessage message(UUID deliveryId, String recipient, EmailNotificationComposer.Content content)
            throws MessagingException, UnsupportedEncodingException {
        InternetAddress sender = single(from);
        InternetAddress to = single(recipient);
        MimeMessage message = mail.createMimeMessage();
        var helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(sender.getAddress(), SENDER_NAME);
        helper.setTo(to);
        helper.setSubject(content.subject().replaceAll("[\\r\\n]+", " "));   // 헤더에 줄바꿈이 들어가지 않게 한다
        helper.setText(content.text(), false);
        // JavaMailSenderImpl은 미리 지정한 Message-ID를 보존한다. 재시도해도 같은 값이라 받는 쪽이 중복을 알아볼 수 있다
        String domain = sender.getAddress().substring(sender.getAddress().lastIndexOf('@') + 1);
        message.setHeader("Message-ID", "<" + deliveryId + "@" + domain + ">");
        return message;
    }

    /** 주소 하나만 받는다. 그룹 문법("목록:a@x,b@y;")도 주소 객체 하나로 해석되므로 따로 거절한다 */
    private static InternetAddress single(String address) throws AddressException {
        InternetAddress parsed = new InternetAddress(address, true);
        if (parsed.isGroup()) throw new AddressException("그룹 주소는 받지 않는다");
        return parsed;
    }

    /**
     * JavaMailSenderImpl이 실패를 감싸는 방식에 따라 나눈다.
     * 인증 거절은 MailAuthenticationException, 연결 단계(접속·STARTTLS) 실패는 cause와 실패 목록의 예외가 같은
     * MailSendException, 보내는 중 실패는 실패 목록에만 예외가 있고, 보낸 뒤 연결 닫기만 실패하면 실패 목록이 비어 있다.
     */
    static Result classify(MailException e) {
        if (e instanceof MailAuthenticationException) return AUTHENTICATION_FAILURE;
        if (!(e instanceof MailSendException send)) return UNKNOWN_OUTCOME;
        if (send.getFailedMessages().isEmpty()) return ACCEPTED;      // 서버가 이미 받았다
        Exception failure = send.getFailedMessages().values().iterator().next();
        if (send.getCause() == failure) return TEMPORARY_FAILURE;     // 아직 아무것도 보내지 않았다
        int code = replyCode(failure);
        if (code >= 500) return PERMANENT_FAILURE;                    // 수신자 없음·한도 초과 등. 다시 보내도 같다
        if (code >= 400) return TEMPORARY_FAILURE;                    // 서버가 나중에 다시 보내라고 했다
        return UNKNOWN_OUTCOME;   // 응답 없이 끊겼다(읽기 시간 초과 등). 서버가 받았을 수 있다
    }

    private static int replyCode(Throwable t) {
        for (int depth = 0; t != null && depth < 10; t = t.getCause(), depth++) {   // MessagingException은 다음 예외를 cause로 돌려준다
            if (t instanceof SMTPSendFailedException s) return s.getReturnCode();
            if (t instanceof SMTPAddressFailedException a) return a.getReturnCode();
        }
        return 0;
    }

    private static Result failed(UUID deliveryId, Result result, Exception cause) {
        log.warn("메일 발송 실패 delivery={} 결과={} SMTP 응답={} 예외={}", deliveryId, result,
                cause == null ? "-" : replyCode(cause), cause == null ? "-" : cause.getClass().getSimpleName());
        return result;
    }
}
