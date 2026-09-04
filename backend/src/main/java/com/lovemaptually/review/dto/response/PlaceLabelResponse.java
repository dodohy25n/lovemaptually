package com.lovemaptually.review.dto.response;

import com.lovemaptually.place.entity.PlaceLabel;

public record PlaceLabelResponse(PlaceLabel label, int reviewedCount, int likedCount) {
}
