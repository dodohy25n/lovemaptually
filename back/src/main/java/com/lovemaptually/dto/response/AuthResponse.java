package com.lovemaptually.dto.response;
public record AuthResponse(Long userId,String email,String nickname,String accessToken,String tokenType,long expiresIn) {}
