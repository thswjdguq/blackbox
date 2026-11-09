package com.blackbox.controller;

import com.blackbox.entity.User;
import com.blackbox.exception.GlobalExceptionHandler;
import com.blackbox.service.CalendarOAuthStateService;
import com.blackbox.service.GoogleCalendarService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** 실제 MVC 바인딩/쿠키/리다이렉트 검사. Google 서버와 DB는 사용하지 않는다. */
class CalendarOAuthHttpTest {
    final GoogleCalendarService calendar = mock(GoogleCalendarService.class);
    final CalendarOAuthStateService states = new CalendarOAuthStateService();
    final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CalendarController(calendar, states))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
    @AfterEach void clearAccount() { SecurityContextHolder.clearContext(); }

    @Test void authorizeSetsBrowserBoundHttpOnlyCookieAndKeepsJsonShape() throws Exception {
        var user = account(); signIn(user);
        when(calendar.buildAuthorizationUrl(anyString())).thenAnswer(i -> "https://accounts.google.com/?state=" + i.getArgument(0));
        var result = mvc.perform(get("/api/calendar/authorize").header("X-Forwarded-Proto", "https"))
                .andReturn().getResponse();
        assertEquals(200, result.getStatus());
        String cookie = result.getHeader("Set-Cookie");
        assertNotNull(cookie); assertTrue(cookie.contains("HttpOnly")); assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("SameSite=Lax")); assertTrue(cookie.contains("Path=/api/auth/google/callback"));
        assertTrue(cookie.contains("Max-Age=600")); assertTrue(result.getContentAsString().contains("authUrl"));
        assertFalse(result.getContentAsString().contains(user.getId().toString()));
    }
    @Test void callbackRequiresBrowserCookieAndOnlyExchangesCodeOnce() throws Exception {
        var user = account(); String state = states.issue(user.getId());
        var path = "/api/auth/google/callback";
        assertEquals(403, mvc.perform(get(path).param("code", "synthetic-code").param("state", state)).andReturn().getResponse().getStatus());
        verifyNoInteractions(calendar);
        var response = mvc.perform(get(path).param("code", "synthetic-code").param("state", state)
                .cookie(new Cookie("calendarOAuthState", state))).andReturn().getResponse();
        assertEquals(302, response.getStatus()); assertEquals("/profile/settings?calendar=connected", response.getRedirectedUrl());
        assertTrue(response.getHeader("Set-Cookie").contains("Max-Age=0"));
        verify(calendar).handleCallback("synthetic-code", user.getId());
        assertEquals(403, mvc.perform(get(path).param("code", "synthetic-code").param("state", state)
                .cookie(new Cookie("calendarOAuthState", state))).andReturn().getResponse().getStatus());
        verifyNoMoreInteractions(calendar);
    }
    @Test void accountSwitchCannotLinkEarlierUsersCalendar() throws Exception {
        var owner = account(); String state = states.issue(owner.getId()); signIn(account());
        var response = mvc.perform(get("/api/auth/google/callback").param("code", "synthetic-code").param("state", state)
                .cookie(new Cookie("calendarOAuthState", state))).andReturn().getResponse();
        assertEquals(403, response.getStatus()); verifyNoInteractions(calendar);
    }
    private User account() { var user = new User(); user.setId(UUID.randomUUID()); return user; }
    private void signIn(User user) { SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, java.util.List.of())); }
}
