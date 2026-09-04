package com.lovemaptually.recommendation.client;

import java.util.List;

public record RecommendationCommand(Long groupId, List<Long> memberIds, String region, int count, Integer budget) {
}
