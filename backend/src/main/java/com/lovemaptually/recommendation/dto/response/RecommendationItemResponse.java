package com.lovemaptually.recommendation.dto.response;

import com.lovemaptually.recommendation.entity.RecBasis;
import java.math.BigDecimal;
import java.util.List;

public record RecommendationItemResponse(
        Long recommendationId,
        Long placeId,
        String name,
        String category,
        Integer priceBand,
        BigDecimal latitude,
        BigDecimal longitude,
        List<String> matchedTags,
        RecBasis basis,
        String reason,
        int displayOrder
) {
}
