package com.lovemaptually.recommendation.dto.response;

import com.lovemaptually.recommendation.entity.RecStatus;
import java.time.OffsetDateTime;

public record RecommendationAcceptedResponse(Long requestId, RecStatus status, OffsetDateTime createdAt) {
}
