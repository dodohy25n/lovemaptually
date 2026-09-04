package com.lovemaptually.report.dto.response;

import com.lovemaptually.group.entity.Plan;
import java.time.OffsetDateTime;

public record SubscriptionResponse(
        Long subscriptionId,
        Long groupId,
        Plan plan,
        OffsetDateTime startedAt,
        String paymentRef
) {
}
