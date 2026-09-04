package com.lovemaptually.place.dto.response;

import com.lovemaptually.place.entity.PlaceLabel;
import com.lovemaptually.review.dto.response.ReviewResponse;
import java.time.OffsetDateTime;
import java.util.List;

public record PinDetailResponse(
        Long groupPlaceId,
        PlaceResponse place,
        PlaceLabel label,
        int reviewedCount,
        int likedCount,
        OffsetDateTime labelUpdatedAt,
        List<VisitSummaryResponse> visits,
        List<ReviewResponse> reviews,
        boolean otherReviewsLocked,
        String lockedReason
) {
}
