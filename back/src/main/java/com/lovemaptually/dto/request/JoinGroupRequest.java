package com.lovemaptually.dto.request;
import jakarta.validation.constraints.*;
public record JoinGroupRequest(@NotBlank String inviteCode) {}
