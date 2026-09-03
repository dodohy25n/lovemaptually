package com.lovemaptually.invite.dto.response;

import com.lovemaptually.group.entity.GroupType;
import java.time.OffsetDateTime;

public record InvitePreviewResponse(
        Long groupId,
        GroupType groupType,
        String name,
        int memberCount,
        boolean available,
        OffsetDateTime expiresAt
) {
}
