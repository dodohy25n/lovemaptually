package com.lovemaptually.auth.controller;

import com.lovemaptually.auth.dto.request.LoginRequest;
import com.lovemaptually.auth.dto.request.SignupRequest;
import com.lovemaptually.auth.dto.response.AuthResponse;
import com.lovemaptually.auth.service.AuthService;
import com.lovemaptually.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "1. 인증", description = "PostgreSQL과 연결된 실제 회원가입·로그인 API")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "회원가입", description = "사용자를 저장하고 서명된 JWT access token을 발급합니다. [실동작]")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(HttpStatus.CREATED.value(), "회원가입했습니다", authService.signup(request)));
    }

    @Operation(summary = "로그인", description = "이메일과 비밀번호를 검증하고 JWT access token을 발급합니다. [실동작]")
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.of(HttpStatus.OK.value(), "로그인했습니다", authService.login(request));
    }
}
