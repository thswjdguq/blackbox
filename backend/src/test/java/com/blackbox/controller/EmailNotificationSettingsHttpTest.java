package com.blackbox.controller;

import com.blackbox.entity.User;
import com.blackbox.exception.GlobalExceptionHandler;
import com.blackbox.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 MVC/JSON/principal·ProblemDetail 검사. JWT 필터·DB/트랜잭션·SMTP의 통합 검사가 아니다. */
class EmailNotificationSettingsHttpTest {
    final EmailNotificationPreferenceStore store = mock(EmailNotificationPreferenceStore.class);
    final EmailNotificationVerificationReader reader = mock(EmailNotificationVerificationReader.class);
    final User user = new User();
    MockMvc mvc;

    @BeforeEach void prepare() {
        user.setId(UUID.randomUUID());
        when(reader.current(user.getId())).thenReturn(Optional.of(new EmailNotificationVerificationReader.Status(
                "synthetic@example.test", null, true, null)));
        when(store.find(user.getId())).thenReturn(Optional.empty());
        when(store.update(eq(user.getId()), anyBoolean(), anyString(), any())).thenAnswer(call ->
                EmailNotificationPreferenceStore.Preference.off().transition(call.getArgument(1), call.getArgument(2), call.getArgument(3)));
        mvc = mvc(new EmailNotificationSettingsService(store, reader));
    }
    @AfterEach void clearAccount() { SecurityContextHolder.clearContext(); }

    @Test void getAndPutRequireALoggedInPrincipal() throws Exception {
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        mvc.perform(put(path()).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(store, reader);
    }

    @Test void getReturnsTheContractWithNullReasonAndTimeWithoutLeakingAnAddress() throws Exception {
        signIn(); var response = mvc.perform(get(path())).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.verificationRequired").value(false)).andExpect(jsonPath("$.verificationAvailable").value(true))
                .andExpect(jsonPath("$.verificationUnavailableReason").value(nullValue()))
                .andExpect(jsonPath("$.updatedAt").value(nullValue())).andReturn().getResponse();
        assertFalse(response.getContentAsString().contains("synthetic@example.test"));
        verify(store, never()).update(any(), anyBoolean(), anyString(), any());
    }

    @Test void putOnAnUnverifiedAddressReturnsTheImmediateVerificationPromptState() throws Exception {
        signIn(); mvc.perform(put(path()).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.emailVerified").value(false)).andExpect(jsonPath("$.verificationRequired").value(true))
                .andExpect(jsonPath("$.updatedAt").isString());
    }

    @Test void clientSuppliedAccountOrVerificationDoesNotReplaceTheServerState() throws Exception {
        signIn(); UUID other = UUID.randomUUID();
        mvc.perform(put(path()).contentType(MediaType.APPLICATION_JSON).content(
                "{\"enabled\":true,\"userId\":\"" + other + "\",\"emailVerified\":true,\"email\":\"other@example.test\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(false));
        verify(store).update(eq(user.getId()), eq(true), anyString(), any());
        verify(store, never()).update(eq(other), anyBoolean(), anyString(), any());
        verify(reader).current(user.getId());
    }

    @Test void enabledMustBeAJsonBooleanRatherThanAStringNumberOrContainer() throws Exception {
        signIn();
        for (String content : List.of("{}", "{\"enabled\":null}", "{\"enabled\":\"true\"}",
                "{\"enabled\":1}", "{\"enabled\":{}}", "{\"enabled\":[]}"))
            mvc.perform(put(path()).contentType(MediaType.APPLICATION_JSON).content(content))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("enabled는 true 또는 false로 입력해 주세요."));
        verifyNoInteractions(store, reader);
    }

    @Test void verificationAvailabilityAndServerReasonAreVisibleBeforeAClick() throws Exception {
        signIn(); when(reader.current(user.getId())).thenReturn(Optional.of(new EmailNotificationVerificationReader.Status(
                "synthetic@example.test", null, false, "합성 서버의 현재 사용 불가 안내")));
        mvc.perform(get(path())).andExpect(status().isOk()).andExpect(jsonPath("$.verificationAvailable").value(false))
                .andExpect(jsonPath("$.verificationUnavailableReason").value("합성 서버의 현재 사용 불가 안내"));
    }

    @Test void databaseFailureDoesNotReturnASuccessfulSaveOrExposeItsDetails() throws Exception {
        signIn(); when(store.update(any(), anyBoolean(), anyString(), any()))
                .thenThrow(new DataAccessResourceFailureException("synthetic-private-database-detail"));
        mvc.perform(put(path()).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isInternalServerError());
    }

    @Test void anotherFrameworkPrincipalIsNotAcceptedAsAnApplicationAccount() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("synthetic", null, List.of()));
        mvc.perform(get(path())).andExpect(status().isUnauthorized()); verifyNoInteractions(store, reader);
    }

    private String path() { return "/api/notifications/email-settings"; }
    private void signIn() { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of())); }
    private MockMvc mvc(EmailNotificationSettingsService service) {
        return MockMvcBuilders.standaloneSetup(new EmailNotificationSettingsController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
    }
}
