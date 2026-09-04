package com.lovemaptually.place.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PlaceInput(
        @NotBlank String provider,
        @NotBlank String providerPlaceId,
        @NotBlank String name,
        @NotBlank String address,
        @NotBlank String region,
        @NotBlank String category,
        Integer priceBand,
        @NotNull BigDecimal latitude,
        @NotNull BigDecimal longitude
) {
}
