package com.lovemaptually.place.controller;

import com.lovemaptually.global.common.response.ApiResponse;
import com.lovemaptually.global.security.AuthenticatedUser;
import com.lovemaptually.place.dto.request.AddGroupPlaceRequest;
import com.lovemaptually.place.dto.response.GroupMapResponse;
import com.lovemaptually.place.dto.response.GroupPlaceResponse;
import com.lovemaptually.place.dto.response.PinDetailResponse;
import com.lovemaptually.place.dto.response.PlaceDetailResponse;
import com.lovemaptually.place.dto.response.PlaceSearchResponse;
import com.lovemaptually.place.service.PlaceService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "5. 장소와 우리 지도", description = "장소 검색과 그룹 지도")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api")
public class PlaceController {

    private final PlaceService placeService;

    public PlaceController(PlaceService placeService) {
        this.placeService = placeService;
    }

    @Operation(summary = "장소 검색")
    @GetMapping("/places")
    public ApiResponse<PlaceSearchResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String region,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.of(200, "조회했습니다", placeService.search(query, region, page, size));
    }

    @Operation(summary = "장소 상세")
    @GetMapping("/places/{placeId}")
    public ApiResponse<PlaceDetailResponse> detail(@PathVariable Long placeId) {
        return ApiResponse.of(200, "조회했습니다", placeService.detail(placeId));
    }

    @Operation(summary = "우리 지도에 담기", description = "담기만 하면 라벨이 null이고 리뷰가 붙어야 라벨이 생깁니다")
    @PostMapping("/groups/{groupId}/places")
    public ResponseEntity<ApiResponse<GroupPlaceResponse>> add(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long groupId,
            @Valid @RequestBody AddGroupPlaceRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(
                HttpStatus.CREATED.value(), "우리 지도에 담았습니다",
                placeService.addToGroupMap(AuthenticatedUser.id(jwt), groupId, request)));
    }

    @Operation(summary = "우리 지도 조회")
    @GetMapping("/groups/{groupId}/places")
    public ApiResponse<GroupMapResponse> map(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long groupId,
            @RequestParam(required = false) String label
    ) {
        return ApiResponse.of(200, "조회했습니다",
                placeService.map(AuthenticatedUser.id(jwt), groupId, label));
    }

    @Operation(summary = "핀 상세")
    @GetMapping("/groups/{groupId}/places/{placeId}")
    public ApiResponse<PinDetailResponse> pin(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long groupId,
            @PathVariable Long placeId
    ) {
        return ApiResponse.of(200, "조회했습니다",
                placeService.pin(AuthenticatedUser.id(jwt), groupId, placeId));
    }
}
