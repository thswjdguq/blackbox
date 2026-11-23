package com.blackbox.controller;

import com.blackbox.entity.User;
import com.blackbox.scheduler.ScoreScheduler;
import com.blackbox.security.JwtService;
import com.blackbox.service.EmailNotificationPreferenceStore;
import com.blackbox.service.EmailNotificationVerificationReader;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.blackbox.repository.ReviewFixtures.user;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 JWT 필터·PostgreSQL 계정 조회·MVC. 설정 저장/주소 인증은 대역이며 V40·SMTP 검증이 아니다. */
@SpringBootTest(properties = "spring.datasource.hikari.minimum-idle=0")
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class EmailNotificationSettingsAuthenticatedHttpTest {
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean ScoreScheduler scheduler;
    @MockitoBean EmailNotificationPreferenceStore store;
    @MockitoBean EmailNotificationVerificationReader reader;
    final Map<UUID, EmailNotificationPreferenceStore.Preference> values = new HashMap<>();

    @BeforeEach void configure() {
        when(store.find(any())).thenAnswer(call -> Optional.ofNullable(values.get(call.getArgument(0))));
        when(store.update(any(), anyBoolean(), anyString(), any())).thenAnswer(call -> {
            UUID id = call.getArgument(0);
            var next = values.getOrDefault(id, EmailNotificationPreferenceStore.Preference.off())
                    .transition(call.getArgument(1), call.getArgument(2), call.getArgument(3));
            values.put(id, next); return next;
        });
    }

    @Test void noTokenInvalidTokenAndRefreshTokenCannotAccessTheSettings() throws Exception {
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        mvc.perform(put(path()).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(path()).header("Authorization", "Bearer synthetic-invalid"))
                .andExpect(status().isUnauthorized());
        User account = account();
        mvc.perform(get(path()).header("Authorization", "Bearer " + jwt.generateRefreshToken(account.getEmail())))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(store, reader);
    }

    @Test void signedInUserCannotWriteAnotherAccountOrAssertAddressVerification() throws Exception {
        User owner = account(), other = account(); unverified(owner);
        mvc.perform(put(path()).header("Authorization", token(owner)).contentType(MediaType.APPLICATION_JSON).content(
                "{\"enabled\":true,\"userId\":\"" + other.getId() + "\",\"emailVerified\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.emailVerified").value(false)).andExpect(jsonPath("$.verificationRequired").value(true));
        verify(store).update(eq(owner.getId()), eq(true), anyString(), any());
        verify(store, never()).update(eq(other.getId()), anyBoolean(), anyString(), any());
        mvc.perform(get(path()).header("Authorization", token(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
    }

    @Test void anAlreadyVerifiedAccountStillDefaultsToMailOff() throws Exception {
        User account = account();
        when(reader.current(account.getId())).thenReturn(Optional.of(new EmailNotificationVerificationReader.Status(
                account.getEmail(), OffsetDateTime.now().minusDays(1), true, null)));
        mvc.perform(get(path()).header("Authorization", token(account))).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.verificationRequired").value(false));
        verify(store, never()).update(any(), anyBoolean(), anyString(), any());
    }

    @Test void producerUnavailableReasonSurvivesAuthenticatedJsonSerialization() throws Exception {
        User account = account();
        when(reader.current(account.getId())).thenReturn(Optional.of(new EmailNotificationVerificationReader.Status(
                account.getEmail(), null, false, "합성 인증 서비스 안내")));
        mvc.perform(get(path()).header("Authorization", token(account))).andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationAvailable").value(false))
                .andExpect(jsonPath("$.verificationUnavailableReason").value("합성 인증 서비스 안내"));
    }

    private void unverified(User account) {
        when(reader.current(account.getId())).thenReturn(Optional.of(new EmailNotificationVerificationReader.Status(
                account.getEmail(), null, true, null)));
    }
    private User account() { var account = user(em, "합성 수신 설정 사용자"); em.flush(); return account; }
    private String token(User account) { return "Bearer " + jwt.generateAccessToken(account.getEmail()); }
    private String path() { return "/api/notifications/email-settings"; }
}
