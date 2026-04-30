package com.blackbox.controller;

import com.blackbox.dto.*;
import com.blackbox.entity.User;
import com.blackbox.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return ResponseEntity.ok(authService.refresh(req));
    }

    @GetMapping("/profile")
    public ResponseEntity<ProfileResponse> getProfile(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(authService.getProfile(user));
    }

    @PutMapping("/profile")
    public ResponseEntity<ProfileResponse> updateProfile(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ProfileUpdateRequest req) {
        return ResponseEntity.ok(authService.updateProfile(user, req));
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody PasswordChangeRequest req) {
        authService.changePassword(user, req);
        return ResponseEntity.noContent().build();
    }
}
