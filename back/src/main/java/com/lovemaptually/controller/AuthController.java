package com.lovemaptually.controller;

import com.lovemaptually.common.*;
import com.lovemaptually.dto.request.*;
import com.lovemaptually.dto.response.AuthResponse;
import com.lovemaptually.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service=service; }

    @PostMapping("/signup")
    ResponseEntity<ApiResponse<AuthResponse>> signup(@Valid @RequestBody SignupRequest body) {
        return ResponseEntity.status(201).body(ApiResponse.of(201,"회원가입했습니다", service.signup(body)));
    }

    @PostMapping("/login")
    ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest body) {
        return ApiResponse.of(200,"로그인했습니다",service.login(body));
    }
}
