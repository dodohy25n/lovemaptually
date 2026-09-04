package com.lovemaptually.dto.response;import java.time.LocalDate;public record ReviewSummaryResponse(Long reviewId,Long userId,String nickname,Short rating,String content,LocalDate visitedOn){}
