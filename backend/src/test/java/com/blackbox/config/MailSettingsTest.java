package com.blackbox.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 이메일 알림의 SMTP 설정(K-41 3장). 실제 SMTP에는 접속하지 않는다.
 * 컨텍스트가 뜬다는 것 자체가 "메일 계정 없이도 서버가 기동한다"는 확인이다
 */
// 웹 서버는 필요 없지만, 같은 설정의 ProposalHttpTest와 Spring 컨텍스트를 함께 쓰려고 RANDOM_PORT로 둔다(컨텍스트를 새로 띄우지 않는다)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MailSettingsTest {
    @Autowired JavaMailSender mailSender;
    @Autowired MailNotificationProperties mail;

    @Test void gmailSmtpRequiresAuthStartTlsAndTimeouts() {
        var sender = assertInstanceOf(JavaMailSenderImpl.class, mailSender);
        assertEquals("smtp.gmail.com", sender.getHost());
        assertEquals(587, sender.getPort());
        var props = sender.getJavaMailProperties();
        assertEquals("true", props.getProperty("mail.smtp.auth"));
        assertEquals("true", props.getProperty("mail.smtp.starttls.enable"));
        assertEquals("true", props.getProperty("mail.smtp.starttls.required"));
        assertEquals("true", props.getProperty("mail.smtp.ssl.checkserveridentity"));
        for (var key : List.of("mail.smtp.connectiontimeout", "mail.smtp.timeout", "mail.smtp.writetimeout"))
            assertEquals("5000", props.getProperty(key), key);
    }

    @Test void sendingIsOffAndAllowlistEmptyByDefault() {
        assertFalse(mail.isEnabled());
        assertTrue(mail.getRecipientAllowlist().isEmpty());
    }

    @Test void allowlistBindsCommaSeparatedAddresses() {
        var props = new Binder(new MapConfigurationPropertySource(Map.of(
                "app.mail.enabled", "true", "app.mail.recipient-allowlist", "a@example.com, b@example.com")))
                .bind("app.mail", MailNotificationProperties.class).get();
        assertTrue(props.isEnabled());
        assertEquals(List.of("a@example.com", "b@example.com"), props.getRecipientAllowlist());
        var empty = new Binder(new MapConfigurationPropertySource(Map.of("app.mail.recipient-allowlist", "")))
                .bindOrCreate("app.mail", MailNotificationProperties.class);
        assertTrue(empty.getRecipientAllowlist().isEmpty());
    }
}
