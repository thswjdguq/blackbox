package com.blackbox.controller;

import com.blackbox.config.MailNotificationProperties;
import com.blackbox.entity.User;
import com.blackbox.scheduler.ScoreScheduler;
import com.blackbox.security.JwtService;
import com.blackbox.service.EmailNotificationComposer;
import com.blackbox.service.EmailNotificationTransport;
import com.blackbox.service.EmailVerificationService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static com.blackbox.repository.ReviewFixtures.user;
import static com.blackbox.service.EmailNotificationTransport.Result.*;
import static com.blackbox.service.EmailVerificationService.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 이메일 주소 확인 API(K-41 0.5 9장). 실제 PostgreSQL과 인증을 쓰고 SMTP 발송만 mock이다. 메일은 보내지 않는다 */
@SpringBootTest(properties = {"app.mail.enabled=true", "app.frontend-base-url=https://app.example.test"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class EmailVerificationHttpTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired MailNotificationProperties settings;
    @Autowired EmailVerificationService verification;
    @MockitoBean EmailNotificationTransport transport;
    @MockitoBean ScoreScheduler scoreScheduler;

    User member;

    @BeforeEach void setUp() {
        // 허용 목록은 ASCII 주소만 받으므로(EmailNotificationPolicy) 이름이 들어간 기본 주소 대신 ASCII 주소를 쓴다
        member = user(em, "확인 회원"); member.setEmail("member-" + UUID.randomUUID() + "@example.com"); em.flush();
        settings.setEnabled(true);
        settings.setRecipientAllowlist(List.of(member.getEmail()));
        when(transport.send(any(), any(), any())).thenReturn(ACCEPTED);
    }

    ResultActions request(User u) throws Exception {
        return mvc.perform(post("/api/auth/email-verification/request").header("Authorization", bearer(u)));
    }
    ResultActions confirm(User u, String token) throws Exception {
        return mvc.perform(post("/api/auth/email-verification/confirm").header("Authorization", bearer(u))
                .contentType("application/json").content(token == null ? "{}" : "{\"token\":\"" + token + "\"}"));
    }
    String bearer(User u) { return "Bearer " + jwt.generateAccessToken(u.getEmail()); }

    /** 마지막으로 보낸 확인 메일의 링크에서 토큰을 꺼낸다 */
    String sentToken() {
        var content = ArgumentCaptor.forClass(EmailNotificationComposer.Content.class);
        verify(transport, atLeastOnce()).send(any(), any(), content.capture());
        String link = content.getValue().target().toString();
        assertTrue(link.startsWith("https://app.example.test/email/verify#token="), "토큰은 fragment로만 싣는다");
        assertTrue(content.getValue().text().contains(link));
        return content.getValue().target().getFragment().substring("token=".length());
    }
    OffsetDateTime verifiedAt(User u) {
        return jdbc.queryForObject("SELECT email_verified_at FROM users WHERE id = ?", OffsetDateTime.class, u.getId());
    }
    void shiftIssuedBack(String interval) {   // 시간이 지난 것처럼 이 회원의 발급 시각을 앞당긴다
        jdbc.update("UPDATE email_verification_tokens SET created_at = created_at - ?::interval WHERE user_id = ?", interval, member.getId());
    }

    @Test void requestSendsOneLinkToTheAccountAddressAndConfirmVerifiesIt() throws Exception {
        assertEquals(new Availability(true, null), verification.availability(member));
        request(member).andExpect(status().isAccepted()).andExpect(jsonPath("$.message").value(REQUESTED));
        var id = ArgumentCaptor.forClass(UUID.class);
        verify(transport).send(id.capture(), eq(member.getEmail()), any());
        String token = sentToken();
        // DB에는 원문이 아니라 해시만 있다. 발송 id는 토큰 행 id라 재시도해도 같은 Message-ID가 된다
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM email_verification_tokens WHERE token_hash = ?", Integer.class, token));
        assertEquals(id.getValue(), jdbc.queryForObject("SELECT id FROM email_verification_tokens WHERE user_id = ?", UUID.class, member.getId()));
        assertNull(verifiedAt(member));

        confirm(member, token).andExpect(status().isNoContent());
        OffsetDateTime first = verifiedAt(member);
        assertNotNull(first);
        // 한 번 쓴 링크는 다시 쓸 수 없고, 확인된 주소는 다시 요청해도 메일을 보내지 않는다
        confirm(member, token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(INVALID));
        request(member).andExpect(status().isNoContent());
        verify(transport, times(1)).send(any(), any(), any());
        assertEquals(first, verifiedAt(member));
    }

    @Test void resendIntervalAndHourlyLimitAnswer429WithRetryAfter() throws Exception {
        request(member).andExpect(status().isAccepted());
        String wait = request(member).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.detail").value(TOO_MANY))
                .andReturn().getResponse().getHeader("Retry-After");
        assertTrue(Long.parseLong(wait) >= 1 && Long.parseLong(wait) <= 60, wait);
        for (int i = 2; i <= 5; i++) {   // 60초씩 지나며 한 시간에 다섯 번까지
            shiftIssuedBack("61 seconds");
            request(member).andExpect(status().isAccepted());
        }
        shiftIssuedBack("61 seconds");
        wait = request(member).andExpect(status().isTooManyRequests()).andReturn().getResponse().getHeader("Retry-After");
        assertTrue(Long.parseLong(wait) > 60, "가장 이른 요청이 한 시간을 넘길 때까지 기다린다: " + wait);
        verify(transport, times(5)).send(any(), any(), any());
    }

    @Test void serviceWideDailyBudgetAlsoAnswers429() throws Exception {
        User other = user(em, "다른 회원"); em.flush();
        for (int i = 0; i < 100; i++)
            jdbc.update("INSERT INTO email_verification_tokens(user_id,email,token_hash,created_at,expires_at) VALUES (?,?,?,now()-interval '1 hour',now())",
                    other.getId(), other.getEmail(), "%064d".formatted(i));
        String wait = request(member).andExpect(status().isTooManyRequests()).andReturn().getResponse().getHeader("Retry-After");
        assertTrue(Long.parseLong(wait) > 3600, wait);
        verify(transport, never()).send(any(), any(), any());
    }

    @Test void unavailableWhenOffOutsideAllowlistOrSmtpRefusesAndNothingLeaksTheReason() throws Exception {
        settings.setRecipientAllowlist(List.of("someone-else@example.com"));
        assertEquals(new Availability(false, UNAVAILABLE), verification.availability(member));
        request(member).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.detail").value(UNAVAILABLE));
        settings.setRecipientAllowlist(List.of(member.getEmail()));
        settings.setEnabled(false);
        request(member).andExpect(status().isServiceUnavailable());
        verify(transport, never()).send(any(), any(), any());

        settings.setEnabled(true);
        when(transport.send(any(), any(), any())).thenReturn(TEMPORARY_FAILURE);
        request(member).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.detail").value(UNAVAILABLE));
        // 보내지 못한 요청도 횟수에 넣어, 실패할 때 SMTP를 연달아 두드리지 않는다
        request(member).andExpect(status().isTooManyRequests());
    }

    @Test void transportExceptionAnswers503Not500() throws Exception {
        when(transport.send(any(), any(), any())).thenThrow(new IllegalStateException("메일 발송이 꺼져 있습니다(MAIL_ENABLED)."));
        request(member).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.detail").value(UNAVAILABLE));
    }

    @Test void confirmRejectsForeignGarbageExpiredAndOldAddressTokens() throws Exception {
        request(member).andExpect(status().isAccepted());
        String token = sentToken();
        User other = user(em, "다른 회원"); em.flush();
        confirm(other, token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(INVALID));
        confirm(member, "not-a-token").andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(INVALID));
        confirm(member, null).andExpect(status().isBadRequest());

        jdbc.update("UPDATE email_verification_tokens SET created_at = now() - interval '20 minutes', expires_at = now() - interval '5 minutes' WHERE user_id = ?", member.getId());
        confirm(member, token).andExpect(status().isGone()).andExpect(jsonPath("$.detail").value(EXPIRED));

        jdbc.update("UPDATE email_verification_tokens SET expires_at = now() + interval '10 minutes' WHERE user_id = ?", member.getId());
        jdbc.update("UPDATE users SET email = ? WHERE id = ?", "changed-" + UUID.randomUUID() + "@example.com", member.getId());
        em.clear();
        User changed = em.find(User.class, member.getId());
        confirm(changed, token).andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(ADDRESS_CHANGED));
        assertNull(verifiedAt(member));
    }

    @Test void bothEndpointsRequireLogin() throws Exception {
        mvc.perform(post("/api/auth/email-verification/request")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/email-verification/confirm").contentType("application/json").content("{\"token\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        verify(transport, never()).send(any(), any(), any());
    }
}
