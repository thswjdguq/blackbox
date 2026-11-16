package com.blackbox.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * 화면이 안내를 나눠야 하는 오류(K-31 8장). ProblemDetail에 `code`와 칸(properties)을 더해 보낸다.
 * `code`가 필요 없는 오류는 지금처럼 ResponseStatusException을 쓴다.
 */
@Getter
public class CodedException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> properties;

    public CodedException(HttpStatus status, String code, String detail, Map<String, Object> properties) {
        super(detail);
        this.status = status; this.code = code; this.properties = properties;
    }
}
