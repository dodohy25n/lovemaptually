package com.lovemaptually.recommendation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateRecommendationRequest(@NotBlank(message = "무엇을 찾는지 적어 주세요") String query) {
}
