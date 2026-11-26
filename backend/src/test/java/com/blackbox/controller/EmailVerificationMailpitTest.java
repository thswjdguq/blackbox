package com.blackbox.controller;

import com.blackbox.config.MailNotificationProperties;
import com.blackbox.entity.User;
import com.blackbox.scheduler.ScoreScheduler;
import com.blackbox.security.JwtService;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.blackbox.repository.ReviewFixtures.user;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 메일 테스트 수신함(K-41 11장). test-mail 프로필로 실제 SMTP 발송을 Mailpit에 보내고, 받은 메일의 링크로 확인 API까지 간다.
 * SMTP와 인증 서비스 모두 mock이 아니다. Mailpit이 있을 때만 돈다(BB_MAIL_TEST_API, CI는 service로 띄운다).
 * 토큰 발급은 호출자와 따로 커밋되므로 테스트 트랜잭션으로 감싸지 않고, 만든 사용자와 받은 메일은 끝나면 지운다
 */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles({"test", "test-mail"})
@EnabledIfEnvironmentVariable(named = "BB_MAIL_TEST_API", matches = "https?://.+")
class EmailVerificationMailpitTest {
    static final String INBOX = System.getenv("BB_MAIL_TEST_API");
    static final Pattern LINK_TOKEN = Pattern.compile("/email/verify#token=([A-Za-z0-9_-]+)");

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired MailNotificationProperties settings;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean ScoreScheduler scoreScheduler;

    final HttpClient http = HttpClient.newHttpClient();
    User member;

    @BeforeEach void setUp() {
        member = new TransactionTemplate(transactions).execute(status -> {
            User u = user(em, "수신함 회원"); u.setEmail("mailpit-" + UUID.randomUUID() + "@example.test"); em.flush(); return u;
        });
        settings.setRecipientAllowlist(List.of(member.getEmail()));
    }

    @AfterEach void cleanUp() throws Exception {
        jdbc.update("DELETE FROM users WHERE id = ?", member.getId());
        inbox("DELETE", "/api/v1/search?query=" + toQuery());
    }

    @Test void verificationMailArrivesInTestInboxAndItsLinkVerifiesTheAddress() throws Exception {
        String bearer = "Bearer " + jwt.generateAccessToken(member.getEmail());
        mvc.perform(post("/api/auth/email-verification/request").header("Authorization", bearer)).andExpect(status().isAccepted());

        String message = inbox("GET", "/api/v1/message/" + awaitMessageId());
        assertEquals("blackbox@example.test", JsonPath.read(message, "$.From.Address"), "실제 Gmail 계정이 아니라 합성 발신 주소");
        assertEquals(List.of(member.getEmail()), JsonPath.read(message, "$.To[*].Address"));
        var token = LINK_TOKEN.matcher(JsonPath.<String>read(message, "$.Text"));
        assertTrue(token.find(), "본문에 확인 링크가 있다");

        mvc.perform(post("/api/auth/email-verification/confirm").header("Authorization", bearer)
                .contentType("application/json").content("{\"token\":\"" + token.group(1) + "\"}"))
                .andExpect(status().isNoContent());
        assertNotNull(jdbc.queryForObject("SELECT email_verified_at FROM users WHERE id = ?", OffsetDateTime.class, member.getId()));
    }

    /** 발송은 요청 처리 안에서 끝나지만 Mailpit 저장까지 잠깐 걸릴 수 있어 몇 초 기다린다 */
    String awaitMessageId() throws Exception {
        for (long deadline = System.nanoTime() + 10_000_000_000L; System.nanoTime() < deadline; Thread.sleep(200)) {
            List<String> ids = JsonPath.read(inbox("GET", "/api/v1/search?query=" + toQuery()), "$.messages[*].ID");
            if (!ids.isEmpty()) { assertEquals(1, ids.size(), "확인 메일은 한 통"); return ids.get(0); }
        }
        return fail("10초 안에 확인 메일이 테스트 수신함에 도착하지 않았다");
    }

    String toQuery() { return URLEncoder.encode("to:\"" + member.getEmail() + "\"", StandardCharsets.UTF_8); }

    String inbox(String method, String path) throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create(INBOX + path)).method(method, HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), path);
        return response.body();
    }
}
