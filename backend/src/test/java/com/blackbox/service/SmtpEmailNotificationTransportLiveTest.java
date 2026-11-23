package com.blackbox.service;

import com.blackbox.config.MailNotificationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 실제 Gmail SMTP로 한 통을 보낸다. MAIL_LIVE_TO가 있을 때만 돈다(CI에서는 건너뛴다).
 * application.yml의 spring.mail(587·STARTTLS·인증·시간 제한)을 그대로 쓰고, DB는 띄우지 않는다.
 * 실행: MAIL_USERNAME·MAIL_PASSWORD(앱 비밀번호)·MAIL_LIVE_TO(받을 주소)를 환경 변수로 넣고
 *   ./gradlew test --tests '*SmtpEmailNotificationTransportLiveTest' --rerun
 */
@SpringBootTest(classes = {SmtpEmailNotificationTransport.class, MailNotificationProperties.class},
        properties = "app.mail.enabled=true")
@ImportAutoConfiguration(MailSenderAutoConfiguration.class)
@EnabledIfEnvironmentVariable(named = "MAIL_LIVE_TO", matches = ".+")
class SmtpEmailNotificationTransportLiveTest {
    @Autowired EmailNotificationTransport transport;

    @Test void gmailAcceptsOneMessage() {
        UUID id = UUID.randomUUID();
        var content = new EmailNotificationComposer.Content("[Team Blackbox] 메일 발송 시험",
                "Team Blackbox 이메일 알림의 발송 시험입니다.\n발송 id: " + id + "\n이 메일에는 답하지 않아도 됩니다.",
                URI.create("https://example.test/"));
        assertEquals(EmailNotificationTransport.Result.ACCEPTED, transport.send(id, System.getenv("MAIL_LIVE_TO"), content));
    }
}
