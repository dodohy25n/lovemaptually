package com.lovemaptually.recommendation.client;

import java.util.List;

public record RecommendationItem(
        Long placeId,
        List<String> matchedTags,
        String basis,
        String reason,
        int displayOrder
) {
}
