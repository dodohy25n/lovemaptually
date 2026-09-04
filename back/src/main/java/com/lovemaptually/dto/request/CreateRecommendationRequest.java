package com.lovemaptually.dto.request;
import jakarta.validation.constraints.*;
public record CreateRecommendationRequest(@NotBlank String query) {}
