package com.blackbox.controller;

import com.blackbox.config.MailNotificationProperties;
import com.blackbox.entity.User;
import com.blackbox.scheduler.ScoreScheduler;
import com.blackbox.security.JwtService;
import com.blackbox.service.EmailNotificationComposer;
import com.blackbox.service.EmailNotificationSettingsService;
import com.blackbox.service.EmailNotificationTransport;
import com.blackbox.service.EmailNotificationVerificationReader;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.blackbox.repository.ReviewFixtures.user;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 JWT·V40 저장·A 인증 API·B Reader를 결합한다. 외부 SMTP만 대역이다. */
@SpringBootTest(properties = {"spring.datasource.hikari.minimum-idle=0", "app.frontend-base-url=https://app.example.test"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class EmailNotificationSettingsIntegrationHttpTest {
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired MailNotificationProperties mail;
    @Autowired EmailNotificationVerificationReader reader;
    @Autowired EmailNotificationSettingsService service;
    @MockitoBean EmailNotificationTransport transport;
    @MockitoBean ScoreScheduler scheduler;
    final List<UUID> fixtures = new ArrayList<>();
    User account;

    @BeforeEach void prepare() {
        account = account(); mail.setEnabled(true); mail.setRecipientAllowlist(List.of(account.getEmail()));
        when(transport.send(any(), any(), any())).thenReturn(EmailNotificationTransport.Result.ACCEPTED);
    }
    @AfterEach void removeOwnSyntheticFixtures() {
        // REQUIRES_NEW 인증 발급을 검증하므로 테스트 TX로 감싸지 않는다. 이 검사에서 만든 합성 계정만 정리한다.
        for (var id : fixtures) jdbc.update("DELETE FROM users WHERE id=?", id);
    }

    @Test void optInRequestConfirmAndOptOutUseTheSamePersistedAccountState() throws Exception {
        mvc.perform(get(path()).header("Authorization", bearer(account)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.emailVerified").value(false)).andExpect(jsonPath("$.updatedAt").isEmpty());
        putSetting(account, true).andExpect(status().isOk()).andExpect(jsonPath("$.verificationRequired").value(true));
        assertNull(verifiedAt());
        mvc.perform(post("/api/auth/email-verification/request").header("Authorization", bearer(account)))
                .andExpect(status().isAccepted());
        var content = ArgumentCaptor.forClass(EmailNotificationComposer.Content.class);
        verify(transport).send(any(), eq(account.getEmail()), content.capture());
        String token = content.getValue().target().getFragment().substring("token=".length());
        mvc.perform(post("/api/auth/email-verification/confirm").header("Authorization", bearer(account))
                        .contentType("application/json").content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get(path()).header("Authorization", bearer(account)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.emailVerified").value(true)).andExpect(jsonPath("$.verificationRequired").value(false));
        assertNotNull(verifiedAt());
        putSetting(account, false).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.emailVerified").value(true));
        verify(transport, times(1)).send(any(), any(), any());
    }

    @Test void verifiedAddressDoesNotOptInAndAccountOrVerificationInputCannotBeForged() throws Exception {
        User other = account();
        jdbc.update("UPDATE users SET email_verified_at=now() WHERE id=?", account.getId());
        mvc.perform(get(path()).header("Authorization", bearer(account)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(put(path()).header("Authorization", bearer(other)).contentType("application/json").content(
                "{\"enabled\":true,\"userId\":\"" + account.getId() + "\",\"emailVerified\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(false));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM email_notification_preferences WHERE user_id=?", Integer.class, account.getId()));
        verifyNoInteractions(transport);
    }

    @Test void currentAvailabilityAndMixedCaseAddressComeFromAWithoutCallingSmtp() {
        var status = reader.current(account.getId()).orElseThrow();
        assertEquals(account.getEmail(), status.currentAddress()); assertTrue(status.available());
        mail.setEnabled(false);
        var unavailable = service.get(account.getId());
        assertFalse(unavailable.verificationAvailable());
        assertEquals(com.blackbox.service.EmailVerificationService.UNAVAILABLE, unavailable.verificationUnavailableReason());
        verifyNoInteractions(transport);
    }

    @Test void readerRefreshesAJdbcVerificationEvenWhenUserWasAlreadyLoaded() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            User cached = em.find(User.class, account.getId()); assertNull(cached.getEmailVerifiedAt());
            jdbc.update("UPDATE users SET email_verified_at=now() WHERE id=?", account.getId());
            assertNotNull(reader.current(account.getId()).orElseThrow().verifiedAt());
            tx.setRollbackOnly();
        });
    }

    private User account() {
        User value = new TransactionTemplate(transactions).execute(tx -> {
            User created = user(em, "합성 이메일 설정 연동");
            created.setEmail("MixedLocal-" + UUID.randomUUID() + "@Example.TEST"); em.flush(); return created;
        });
        fixtures.add(value.getId()); return value;
    }
    private org.springframework.test.web.servlet.ResultActions putSetting(User user, boolean enabled) throws Exception {
        return mvc.perform(put(path()).header("Authorization", bearer(user)).contentType("application/json")
                .content("{\"enabled\":" + enabled + "}"));
    }
    private OffsetDateTime verifiedAt() { return jdbc.queryForObject("SELECT email_verified_at FROM users WHERE id=?", OffsetDateTime.class, account.getId()); }
    private String bearer(User user) { return "Bearer " + jwt.generateAccessToken(user.getEmail()); }
    private String path() { return "/api/notifications/email-settings"; }
}
