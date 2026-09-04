package com.lovemaptually.review.controller;

import com.lovemaptually.global.common.response.ApiResponse;
import com.lovemaptually.global.security.AuthenticatedUser;
import com.lovemaptually.review.dto.request.CreateReviewRequest;
import com.lovemaptually.review.dto.response.GroupReviewsResponse;
import com.lovemaptually.review.dto.response.ReviewResponse;
import com.lovemaptually.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "4. 리뷰", description = "리뷰 저장과 조회")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(summary = "리뷰 저장", description = "리뷰를 저장하고 태그 추출과 집계 캐시 갱신을 같은 트랜잭션에서 끝냅니다")
    @PostMapping("/reviews")
    public ResponseEntity<ApiResponse<ReviewResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateReviewRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(
                HttpStatus.CREATED.value(), "리뷰를 저장했습니다",
                reviewService.create(AuthenticatedUser.id(jwt), request)));
    }

    @Operation(summary = "리뷰 조회", description = "내가 쓴 리뷰 한 건을 조회합니다")
    @GetMapping("/reviews/{reviewId}")
    public ApiResponse<ReviewResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long reviewId) {
        return ApiResponse.of(200, "조회했습니다", reviewService.get(reviewId, AuthenticatedUser.id(jwt)));
    }

    @Operation(summary = "장소의 그룹 리뷰", description = "내가 아직 쓰지 않았으면 다른 구성원의 리뷰는 잠깁니다")
    @GetMapping("/groups/{groupId}/places/{placeId}/reviews")
    public ApiResponse<GroupReviewsResponse> groupReviews(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long groupId,
            @PathVariable Long placeId
    ) {
        return ApiResponse.of(200, "조회했습니다",
                reviewService.groupReviews(groupId, placeId, AuthenticatedUser.id(jwt)));
    }
}
