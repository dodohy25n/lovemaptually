package com.lovemaptually.place.dto.response;

import com.lovemaptually.place.entity.Place;
import java.math.BigDecimal;

public record PlaceResponse(
        Long placeId,
        String provider,
        String providerPlaceId,
        String name,
        String region,
        String address,
        String category,
        Integer priceBand,
        BigDecimal latitude,
        BigDecimal longitude
) {
    public static PlaceResponse from(Place place) {
        return new PlaceResponse(place.getId(), place.getProvider(), place.getProviderPlaceId(), place.getName(),
                place.getRegion(), place.getAddress(), place.getCategory(),
                place.getPriceBand() == null ? null : place.getPriceBand().intValue(),
                place.getLatitude(), place.getLongitude());
    }
}
