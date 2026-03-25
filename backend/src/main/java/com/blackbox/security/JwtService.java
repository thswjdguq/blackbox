package com.blackbox.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    private final JwtProperties props;
    private SecretKey signingKey;

    public JwtService(JwtProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        // plain UTF-8 bytes — JWT_SECRET은 최소 32자 필요 (HMAC-SHA256 256-bit)
        signingKey = Keys.hmacShaKeyFor(
                props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(String subject) {
        return buildToken(subject, props.getExpirationMs(), Map.of("type", "access"));
    }

    public String generateRefreshToken(String subject) {
        return buildToken(subject, props.getRefreshExpirationMs(), Map.of("type", "refresh"));
    }

    private String buildToken(String subject, long ttlMs, Map<String, Object> claims) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMs))
                .signWith(signingKey)
                .compact();
    }

    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractSubject(String token) {
        return parseAndValidate(token).getSubject();
    }

    public boolean isAccessToken(String token) {
        try {
            return "access".equals(parseAndValidate(token).get("type", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isRefreshToken(String token) {
        try {
            return "refresh".equals(parseAndValidate(token).get("type", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
