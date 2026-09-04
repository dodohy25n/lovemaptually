package com.lovemaptually.place.dto.response;

import java.util.List;

public record PlaceDetailResponse(PlaceResponse place, List<PlaceTagResponse> tags) {
}
