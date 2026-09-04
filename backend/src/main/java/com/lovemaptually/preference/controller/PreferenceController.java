package com.lovemaptually.preference.controller;

import com.lovemaptually.global.common.response.ApiResponse;
import com.lovemaptually.global.security.AuthenticatedUser;
import com.lovemaptually.preference.dto.response.GroupPreferencesResponse;
import com.lovemaptually.preference.service.PreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "6. 우리 취향", description = "구성원 취향의 겹침과 갈림")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/groups")
public class PreferenceController {

    private final PreferenceService preferenceService;

    public PreferenceController(PreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @Operation(summary = "그룹 취향 조회", description = "판정이 난 구성원 수를 분모로 ALL_SAME, ONE_SIDED, SPLIT을 가릅니다")
    @GetMapping("/{groupId}/preferences")
    public ApiResponse<GroupPreferencesResponse> preferences(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long groupId
    ) {
        return ApiResponse.of(200, "조회했습니다", preferenceService.of(groupId, AuthenticatedUser.id(jwt)));
    }
}
