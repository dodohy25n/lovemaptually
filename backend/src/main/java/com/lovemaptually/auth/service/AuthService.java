package com.lovemaptually.auth.service;

import com.lovemaptually.auth.dto.request.LoginRequest;
import com.lovemaptually.auth.dto.request.SignupRequest;
import com.lovemaptually.auth.dto.response.AuthResponse;
import com.lovemaptually.global.exception.AppException;
import com.lovemaptually.global.security.JwtService;
import com.lovemaptually.user.entity.User;
import com.lovemaptually.user.repository.UserRepository;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw emailAlreadyExists();
        }

        User user = new User(email, passwordEncoder.encode(request.password()), request.nickname().trim());
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw emailAlreadyExists();
        }
        return responseFor(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(AuthService::invalidCredentials);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        return responseFor(user);
    }

    private AuthResponse responseFor(User user) {
        return new AuthResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                jwtService.issueAccessToken(user.getId()),
                "Bearer",
                jwtService.expiresIn()
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static AppException emailAlreadyExists() {
        return new AppException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "이미 사용 중인 이메일입니다");
    }

    private static AppException invalidCredentials() {
        return new AppException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "이메일 또는 비밀번호가 올바르지 않습니다");
    }
}
