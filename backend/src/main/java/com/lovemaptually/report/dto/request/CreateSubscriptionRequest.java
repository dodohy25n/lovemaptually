package com.lovemaptually.report.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateSubscriptionRequest(@NotBlank(message = "플랜을 지정해 주세요") String plan) {
}
