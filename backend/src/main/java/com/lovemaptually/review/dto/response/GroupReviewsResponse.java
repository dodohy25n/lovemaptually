package com.lovemaptually.review.dto.response;

import com.lovemaptually.place.entity.PlaceLabel;
import java.util.List;

public record GroupReviewsResponse(
        PlaceLabel placeLabel,
        int reviewedCount,
        int likedCount,
        ReviewResponse myReview,
        List<ReviewResponse> otherReviews,
        boolean otherReviewsLocked,
        String lockedReason
) {
}
