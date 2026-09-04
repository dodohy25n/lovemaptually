package com.lovemaptually.dto.request;
import jakarta.validation.constraints.*;
public record SignupRequest(@Email @NotBlank String email,@Size(min=8,max=72) String password,@NotBlank @Size(max=50) String nickname) {}
