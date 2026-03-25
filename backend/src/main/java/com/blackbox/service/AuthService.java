package com.blackbox.service;

import com.blackbox.dto.AuthResponse;
import com.blackbox.dto.LoginRequest;
import com.blackbox.dto.RefreshRequest;
import com.blackbox.dto.SignupRequest;
import com.blackbox.entity.User;
import com.blackbox.exception.DuplicateEmailException;
import com.blackbox.exception.InvalidCredentialsException;
import com.blackbox.repository.UserRepository;
import com.blackbox.security.JwtProperties;
import com.blackbox.security.JwtService;
import io.jsonwebtoken.JwtException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       JwtProperties jwtProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public AuthResponse signup(SignupRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new DuplicateEmailException(req.email());
        }
        User user = new User();
        user.setEmail(req.email());
        user.setName(req.name());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole("STUDENT");
        userRepository.save(user);
        return issueTokenPair(user.getEmail());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return issueTokenPair(user.getEmail());
    }

    public AuthResponse refresh(RefreshRequest req) {
        try {
            if (!jwtService.isRefreshToken(req.refreshToken())) {
                throw new InvalidCredentialsException();
            }
            String email = jwtService.extractSubject(req.refreshToken());
            userRepository.findByEmail(email)
                    .orElseThrow(InvalidCredentialsException::new);
            return issueTokenPair(email);
        } catch (JwtException ex) {
            throw new InvalidCredentialsException();
        }
    }

    private AuthResponse issueTokenPair(String email) {
        return AuthResponse.of(
                jwtService.generateAccessToken(email),
                jwtService.generateRefreshToken(email),
                jwtProperties.getExpirationMs()
        );
    }
}
