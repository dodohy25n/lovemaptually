package com.lovemaptually.place.dto.request;

import jakarta.validation.Valid;

public record AddGroupPlaceRequest(Long placeId, @Valid PlaceInput place) {
}
