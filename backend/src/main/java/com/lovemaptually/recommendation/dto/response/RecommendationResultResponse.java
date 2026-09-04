package com.lovemaptually.recommendation.dto.response;

import com.lovemaptually.recommendation.entity.RecStatus;
import java.math.BigDecimal;
import java.util.List;

public record RecommendationResultResponse(
        Long requestId,
        String query,
        RecommendationIntentResponse intent,
        Integer candidateCount,
        BigDecimal cfWeight,
        RecStatus status,
        List<RecommendationItemResponse> recommendations
) {
}
