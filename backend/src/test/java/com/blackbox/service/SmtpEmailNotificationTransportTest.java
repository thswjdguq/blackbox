package com.blackbox.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.blackbox.config.MailNotificationProperties;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.blackbox.service.EmailNotificationTransport.Result.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Spring의 실제 JavaMailSenderImpl을 쓰고 SMTP 연결(Transport)만 바꿔 끼운다. 네트워크·Gmail에 닿지 않는다.
 * 실패는 Angus SMTP 구현이 실제로 던지는 예외로 재현해, Spring이 감싸는 방식까지 함께 확인한다.
 */
class SmtpEmailNotificationTransportTest {
    static final String SENDER = "sender@example.test", TO = "member@example.test";
    final FakeSender smtp = new FakeSender();
    final MailNotificationProperties settings = new MailNotificationProperties();
    final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    final Logger logger = (Logger) LoggerFactory.getLogger(SmtpEmailNotificationTransport.class);
    final EmailNotificationComposer.Content content = new EmailNotificationComposer.Content(
            "[Team Blackbox] 결과 확인 요청 · 합성\r\n업무", "본문 한 줄\nhttps://app.example.test/p/1", URI.create("https://app.example.test/p/1"));

    SmtpEmailNotificationTransportTest() {
        settings.setEnabled(true);
        logs.start(); logger.addAppender(logs);
    }
    @AfterEach void detach() { logger.detachAppender(logs); }

    SmtpEmailNotificationTransport transport() { return transport(SENDER); }
    SmtpEmailNotificationTransport transport(String from) {
        return new SmtpEmailNotificationTransport(smtp, settings, from, "smtp.gmail.com", true, true);
    }

    @Test void acceptedMessageIsOnePlainTextUtf8MailWithStableMessageId() throws Exception {
        UUID id = UUID.randomUUID();
        assertEquals(ACCEPTED, transport().send(id, TO, content));
        assertEquals(ACCEPTED, transport().send(id, TO, content));
        assertEquals(2, smtp.sent.size());
        MimeMessage m = smtp.sent.getFirst();
        assertEquals("<" + id + "@example.test>", m.getMessageID(), "Spring이 saveChanges 뒤에도 지정한 값을 보존한다");
        assertEquals(m.getMessageID(), smtp.sent.get(1).getMessageID(), "재시도는 같은 Message-ID");
        assertArrayEquals(new Address[]{new InternetAddress(TO)}, m.getAllRecipients(), "To 한 명, CC·BCC 없음");
        InternetAddress from = (InternetAddress) m.getFrom()[0];
        assertEquals(SENDER, from.getAddress()); assertEquals("Team Blackbox", from.getPersonal());
        assertEquals("[Team Blackbox] 결과 확인 요청 · 합성 업무", m.getSubject(), "제목의 줄바꿈은 공백");
        assertTrue(m.getContentType().startsWith("text/plain"), m.getContentType());
        assertTrue(m.getContentType().toUpperCase().contains("CHARSET=UTF-8"), m.getContentType());
        assertEquals(content.text(), m.getContent());
        assertEquals(List.of("메일 발송 수락 delivery=" + id, "메일 발송 수락 delivery=" + id),
                logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
    }

    @Test void credentialsRejectedAtLoginIsAuthenticationFailure() {
        smtp.onConnect = new AuthenticationFailedException("535-5.7.8 Username and Password not accepted");
        assertEquals(AUTHENTICATION_FAILURE, transport().send(UUID.randomUUID(), TO, content));
    }

    @Test void failureBeforeAnythingIsSentIsTemporary() {
        smtp.onConnect = new MessagingException("Could not connect to SMTP host", new SocketTimeoutException("connect timed out"));
        assertEquals(TEMPORARY_FAILURE, transport().send(UUID.randomUUID(), TO, content));
        smtp.onConnect = new MessagingException("STARTTLS is required but host does not support STARTTLS");
        assertEquals(TEMPORARY_FAILURE, transport().send(UUID.randomUUID(), TO, content));
        assertTrue(smtp.sent.isEmpty());
    }

    @Test void smtpReplyCodeDecidesPermanentOrTemporary() throws Exception {
        smtp.onSend = new SMTPSendFailedException("DATA", 550, "550 5.4.5 Daily sending quota exceeded", null, null, null, null);
        assertEquals(PERMANENT_FAILURE, transport().send(UUID.randomUUID(), TO, content));
        smtp.onSend = new SendFailedException("Invalid Addresses",
                new SMTPAddressFailedException(new InternetAddress(TO), "RCPT TO", 553, "553 5.1.2 address rejected"));
        assertEquals(PERMANENT_FAILURE, transport().send(UUID.randomUUID(), TO, content));
        smtp.onSend = new SMTPSendFailedException("DATA", 421, "421 4.7.0 Try again later", null, null, null, null);
        assertEquals(TEMPORARY_FAILURE, transport().send(UUID.randomUUID(), TO, content));
    }

    @Test void connectionLostWithoutReplyIsUnknownOutcome() {
        smtp.onSend = new MessagingException("Exception reading response", new SocketTimeoutException("Read timed out"));
        assertEquals(UNKNOWN_OUTCOME, transport().send(UUID.randomUUID(), TO, content));
    }

    @Test void closeFailureAfterServerAcceptedIsStillAccepted() {
        smtp.onClose = new MessagingException("QUIT failed");
        assertEquals(ACCEPTED, transport().send(UUID.randomUUID(), TO, content));
        assertEquals(1, smtp.sent.size());
    }

    @Test void authAndStarttlsCanBeOffOnlyForLoopbackTestInbox() {
        for (String loopback : List.of("127.0.0.1", "localhost", " LOCALHOST ", "::1"))
            assertDoesNotThrow(() -> SmtpEmailNotificationTransport.requireProtectedUnlessLoopback(loopback, false, false), loopback);
        for (String outside : List.of("smtp.gmail.com", "127.0.0.2", "localhost.example.test", ""))
            for (boolean[] off : new boolean[][]{{false, true}, {true, false}, {false, false}})
                assertThrows(IllegalStateException.class,
                        () -> SmtpEmailNotificationTransport.requireProtectedUnlessLoopback(outside, off[0], off[1]), outside);
        assertThrows(IllegalStateException.class, () -> SmtpEmailNotificationTransport.requireProtectedUnlessLoopback(null, false, false));
        assertDoesNotThrow(() -> SmtpEmailNotificationTransport.requireProtectedUnlessLoopback("smtp.gmail.com", true, true));
    }

    @Test void nothingIsSentWhenDisabledUnconfiguredOrAddressIsNotOne() {
        assertEquals(AUTHENTICATION_FAILURE, transport(" ").send(UUID.randomUUID(), TO, content));
        for (String bad : List.of("a@example.test, b@example.test", "Group:alpha@example.test,beta@example.test;",
                "not-an-address", ""))
            assertEquals(PERMANENT_FAILURE, transport().send(UUID.randomUUID(), bad, content), bad);
        assertEquals(PERMANENT_FAILURE, transport("Group:" + SENDER + ";")
                .send(UUID.randomUUID(), TO, content), "보내는 주소도 한 명");
        assertEquals(PERMANENT_FAILURE, transport().send(UUID.randomUUID(), null, content));
        settings.setEnabled(false);
        assertThrows(IllegalStateException.class, () -> transport().send(UUID.randomUUID(), TO, content));
        assertEquals(0, smtp.connects);
    }

    @Test void failureLogHasIdResultAndCodeButNoAddressSubjectOrServerText() {
        UUID id = UUID.randomUUID();
        smtp.onSend = new SMTPSendFailedException("DATA", 550, "550 5.1.1 " + TO + " does not exist", null, null, null, null);
        transport().send(id, TO, content);
        String line = logs.list.getLast().getFormattedMessage();
        assertEquals("메일 발송 실패 delivery=" + id + " 결과=PERMANENT_FAILURE SMTP 응답=550 예외=SMTPSendFailedException", line);
        assertTrue(logs.list.stream().allMatch(e -> e.getThrowableProxy() == null), "예외 객체(메시지·스택)를 남기지 않는다");
    }

    /** 연결·발송·종료에서 정해 둔 예외를 던지는 가짜 SMTP 연결. 받은 메시지는 sent에 모은다 */
    static class FakeSender extends JavaMailSenderImpl {
        MessagingException onConnect, onSend, onClose;
        final List<MimeMessage> sent = new ArrayList<>();
        int connects;

        FakeSender() { setHost("smtp.example.test"); setPort(587); setUsername(SENDER); setPassword("synthetic"); }

        @Override protected Transport getTransport(Session session) {
            return new Transport(session, new URLName("smtp://smtp.example.test")) {
                @Override protected boolean protocolConnect(String host, int port, String user, String password) throws MessagingException {
                    connects++;
                    if (onConnect != null) throw onConnect;
                    return true;
                }
                @Override public void sendMessage(Message message, Address[] addresses) throws MessagingException {
                    if (onSend != null) throw onSend;
                    sent.add((MimeMessage) message);
                }
                @Override public synchronized void close() throws MessagingException {
                    super.close();
                    if (onClose != null) throw onClose;
                }
            };
        }
    }
}
