package com.lovemaptually.dto.request;
import jakarta.validation.constraints.*;import java.math.BigDecimal;
public record PlaceInput(@NotBlank String provider,@NotBlank String providerPlaceId,@NotBlank String name,@NotBlank String address,@NotBlank String region,@NotBlank String category,@Min(1) @Max(4) Integer priceBand,@NotNull BigDecimal latitude,@NotNull BigDecimal longitude) {}
