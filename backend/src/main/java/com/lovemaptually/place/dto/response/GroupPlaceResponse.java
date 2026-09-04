package com.lovemaptually.place.dto.response;

import com.lovemaptually.place.entity.PlaceLabel;
import java.time.OffsetDateTime;

public record GroupPlaceResponse(
        Long groupPlaceId,
        Long groupId,
        Long placeId,
        Long addedByUserId,
        PlaceLabel label,
        OffsetDateTime createdAt
) {
}
