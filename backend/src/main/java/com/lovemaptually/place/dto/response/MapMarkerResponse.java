package com.lovemaptually.place.dto.response;

import com.lovemaptually.place.entity.PlaceLabel;
import java.math.BigDecimal;

public record MapMarkerResponse(
        Long groupPlaceId,
        Long placeId,
        String name,
        String address,
        String category,
        BigDecimal latitude,
        BigDecimal longitude,
        PlaceLabel label,
        int reviewedCount,
        int likedCount
) {
}
