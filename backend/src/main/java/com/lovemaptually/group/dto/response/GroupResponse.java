package com.lovemaptually.group.dto.response;

import com.lovemaptually.group.entity.GroupType;
import java.time.OffsetDateTime;
import java.util.List;

public record GroupResponse(
        Long groupId,
        GroupType groupType,
        String name,
        OffsetDateTime createdAt,
        List<MemberResponse> members
) {
    public record MemberResponse(Long userId, String nickname, String role, OffsetDateTime joinedAt) {
    }
}
