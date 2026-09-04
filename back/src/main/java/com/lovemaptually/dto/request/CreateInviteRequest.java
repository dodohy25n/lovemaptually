package com.lovemaptually.dto.request;
import jakarta.validation.constraints.*;
public record CreateInviteRequest(@Min(1) Integer maxUses,@Min(1) Integer expiresInHours) {}
