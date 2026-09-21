package com.blackbox.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test void preservesStatusAndReason() {
        var result = handler.handleStatus(new ResponseStatusException(HttpStatus.CONFLICT, "연결된 업무가 있습니다"));
        assertEquals(409, result.getStatus());
        assertEquals("연결된 업무가 있습니다", result.getDetail());
    }
    @Test void providesFallbackForMissingReason() {
        var result = handler.handleStatus(new ResponseStatusException(HttpStatus.BAD_REQUEST));
        assertEquals(400, result.getStatus());
        assertNotNull(result.getDetail());
    }
    @Test void integrityFailureDoesNotExposeDatabaseMessage() {
        var result = handler.handleConflict(new DataIntegrityViolationException("internal SQL detail"));
        assertEquals(409, result.getStatus());
        assertFalse(result.getDetail().contains("internal SQL detail"));
    }
    @Test void retainsMainAccessDeniedHandling() {
        var result = handler.handleAccessDenied(new AccessDeniedException("internal permission detail"));
        assertEquals(403, result.getStatus());
        assertEquals("접근 권한이 없습니다", result.getDetail());
    }
    @Test void retainsMainUnhandledFailureHandling() {
        var result = handler.handleUnhandled(new IllegalStateException("internal detail"));
        assertEquals(500, result.getStatus());
        assertEquals("서버 내부 오류가 발생했습니다", result.getDetail());
    }
}
