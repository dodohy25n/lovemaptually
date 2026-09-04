package com.lovemaptually.recommendation.client;

import java.util.List;

public record RecommendationResult(
        int candidateCount,
        double cfWeight,
        boolean degraded,
        String notice,
        List<RecommendationItem> items
) {
}
