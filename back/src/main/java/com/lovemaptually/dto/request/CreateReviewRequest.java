package com.lovemaptually.dto.request;
import jakarta.validation.constraints.*;import java.time.LocalDate;
public record CreateReviewRequest(@NotNull Long placeId,Long withGroupId,@NotNull @PastOrPresent LocalDate visitedOn,@NotNull Integer rating,@NotBlank String content) {}
