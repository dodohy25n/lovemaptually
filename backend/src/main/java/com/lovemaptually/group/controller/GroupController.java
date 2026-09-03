package com.lovemaptually.group.controller;

import com.lovemaptually.global.common.response.ApiResponse;
import com.lovemaptually.global.security.AuthenticatedUser;
import com.lovemaptually.group.dto.request.CreateGroupRequest;
import com.lovemaptually.group.dto.request.JoinGroupRequest;
import com.lovemaptually.group.dto.response.GroupResponse;
import com.lovemaptually.group.dto.response.MyGroupsResponse;
import com.lovemaptually.group.service.GroupUseCase;
import com.lovemaptually.invite.dto.request.CreateInviteRequest;
import com.lovemaptually.invite.dto.response.InviteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "2. 그룹", description = "재현 가능한 인메모리 Mock 그룹 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupUseCase groupUseCase;

    public GroupController(GroupUseCase groupUseCase) {
        this.groupUseCase = groupUseCase;
    }

    @Operation(summary = "그룹 생성", description = "그룹과 OWNER 구성원을 생성합니다. [Mock]")
    @PostMapping
    public ResponseEntity<ApiResponse<GroupResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateGroupRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(
                HttpStatus.CREATED.value(), "그룹을 만들었습니다",
                groupUseCase.createGroup(AuthenticatedUser.id(jwt), request)
        ));
    }

    @Operation(summary = "내 그룹 목록", description = "현재 사용자가 참여 중인 그룹을 조회합니다. [Mock]")
    @GetMapping("/me")
    public ApiResponse<MyGroupsResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(200, "조회했습니다", groupUseCase.getMyGroups(AuthenticatedUser.id(jwt)));
    }

    @Operation(summary = "초대 코드 발급", description = "그룹 OWNER가 초대 코드를 발급합니다. [Mock]")
    @PostMapping("/{groupId}/invites")
    public ResponseEntity<ApiResponse<InviteResponse>> createInvite(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long groupId,
            @Valid @RequestBody(required = false) CreateInviteRequest request
    ) {
        CreateInviteRequest actualRequest = request == null ? new CreateInviteRequest(null, null) : request;
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(
                HttpStatus.CREATED.value(), "초대 코드를 발급했습니다",
                groupUseCase.createInvite(AuthenticatedUser.id(jwt), groupId, actualRequest)
        ));
    }

    @Operation(summary = "그룹 참여", description = "초대 코드로 그룹 구성원이 됩니다. [Mock]")
    @PostMapping("/members")
    public ResponseEntity<ApiResponse<GroupResponse>> join(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody JoinGroupRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(
                HttpStatus.CREATED.value(), "그룹에 참여했습니다",
                groupUseCase.joinGroup(AuthenticatedUser.id(jwt), request.inviteCode())
        ));
    }
}
