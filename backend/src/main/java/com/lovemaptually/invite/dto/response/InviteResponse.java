package com.lovemaptually.invite.dto.response;

import java.time.OffsetDateTime;

public record InviteResponse(
        Long inviteCodeId,
        String code,
        int maxUses,
        int useCount,
        String status,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt
) {
}
