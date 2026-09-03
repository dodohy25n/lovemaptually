package com.lovemaptually.invite.controller;

import com.lovemaptually.global.common.response.ApiResponse;
import com.lovemaptually.group.service.GroupUseCase;
import com.lovemaptually.invite.dto.response.InvitePreviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "3. 초대 코드", description = "인증 전에 그룹 정보를 확인하는 공개 Mock API")
@RestController
@RequestMapping("/api/invites")
public class InviteController {

    private final GroupUseCase groupUseCase;

    public InviteController(GroupUseCase groupUseCase) {
        this.groupUseCase = groupUseCase;
    }

    @Operation(summary = "초대 코드 확인", description = "참여할 그룹과 코드 사용 가능 여부를 인증 없이 확인합니다. [Mock]")
    @GetMapping("/{code}")
    public ApiResponse<InvitePreviewResponse> preview(@PathVariable String code) {
        return ApiResponse.of(200, "조회했습니다", groupUseCase.previewInvite(code));
    }
}
