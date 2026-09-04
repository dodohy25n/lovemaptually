package com.lovemaptually.review.dto.response;

import com.lovemaptually.review.entity.TagStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record ReviewResponse(
        Long reviewId,
        Long placeId,
        Long userId,
        String nickname,
        LocalDate visitedOn,
        Integer rating,
        String content,
        TagStatus tagStatus,
        List<ReviewTagResponse> tags,
        PlaceLabelResponse placeLabel,
        OffsetDateTime createdAt
) {
}
