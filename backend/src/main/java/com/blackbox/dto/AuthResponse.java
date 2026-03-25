package com.blackbox.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn   // 초 단위
) {
    public static AuthResponse of(String accessToken, String refreshToken, long expiresInMs) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresInMs / 1000);
    }
}
