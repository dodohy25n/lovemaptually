package com.lovemaptually.invite.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CreateInviteRequest(
        @Min(value = 1, message = "최대 사용 횟수는 1 이상이어야 합니다")
        @Max(value = 100, message = "최대 사용 횟수는 100 이하여야 합니다")
        Integer maxUses,

        @Min(value = 1, message = "유효 시간은 1시간 이상이어야 합니다")
        @Max(value = 720, message = "유효 시간은 720시간 이하여야 합니다")
        Integer expiresInHours
) {
}
