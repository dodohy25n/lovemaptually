package com.lovemaptually.place.dto.response;

import java.time.LocalDate;

public record VisitSummaryResponse(LocalDate visitedOn, int reviewCount) {
}
