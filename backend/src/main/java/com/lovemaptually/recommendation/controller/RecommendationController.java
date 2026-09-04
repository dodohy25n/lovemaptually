package com.lovemaptually.recommendation.controller;

import com.lovemaptually.global.common.response.ApiResponse;
import com.lovemaptually.global.security.AuthenticatedUser;
import com.lovemaptually.recommendation.dto.request.CreateRecommendationRequest;
import com.lovemaptually.recommendation.dto.response.RecommendationAcceptedResponse;
import com.lovemaptually.recommendation.dto.response.RecommendationResultResponse;
import com.lovemaptually.recommendation.service.RecommendationService;
import com.lovemaptually.recommendation.service.RecommendationWorker;
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

@Tag(name = "7. 추천", description = "요청 접수와 결과 조회의 두 단계")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final RecommendationWorker recommendationWorker;

    public RecommendationController(RecommendationService recommendationService,
                                    RecommendationWorker recommendationWorker) {
        this.recommendationService = recommendationService;
        this.recommendationWorker = recommendationWorker;
    }

    @Operation(summary = "추천 요청", description = "접수만 하고 순위 계산은 워커가 합니다. 지역을 못 읽으면 422입니다")
    @PostMapping("/groups/{groupId}/recommendation-requests")
    public ResponseEntity<ApiResponse<RecommendationAcceptedResponse>> accept(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long groupId,
            @Valid @RequestBody CreateRecommendationRequest request
    ) {
        RecommendationAcceptedResponse accepted =
                recommendationService.accept(AuthenticatedUser.id(jwt), groupId, request);
        recommendationWorker.process(accepted.requestId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.of(
                HttpStatus.ACCEPTED.value(), "추천을 준비하고 있습니다", accepted));
    }

    @Operation(summary = "추천 결과 조회")
    @GetMapping("/recommendation-requests/{requestId}")
    public ApiResponse<RecommendationResultResponse> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long requestId
    ) {
        return ApiResponse.of(200, "조회했습니다",
                recommendationService.get(requestId, AuthenticatedUser.id(jwt)));
    }
}
