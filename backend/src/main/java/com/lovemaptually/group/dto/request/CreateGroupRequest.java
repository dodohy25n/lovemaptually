package com.lovemaptually.group.dto.request;

import com.lovemaptually.group.entity.GroupType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateGroupRequest(
        @NotNull(message = "그룹 유형을 선택해 주세요") GroupType groupType,
        @Size(max = 50, message = "그룹 이름은 50자 이하여야 합니다") String name
) {
}
