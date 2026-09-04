package com.lovemaptually.review.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateReviewRequest(
        @NotNull(message = "장소를 선택해 주세요") Long placeId,
        Long withGroupId,
        @NotNull(message = "방문 날짜를 입력해 주세요") LocalDate visitedOn,
        @NotNull(message = "별점을 입력해 주세요") Integer rating,
        @NotBlank(message = "리뷰 내용을 입력해 주세요") String content
) {
}
