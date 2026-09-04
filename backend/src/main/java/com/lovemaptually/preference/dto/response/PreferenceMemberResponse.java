package com.lovemaptually.preference.dto.response;

public record PreferenceMemberResponse(
        Long userId,
        String nickname,
        String side,
        String sideLabel,
        int wantHighCount,
        int wantLowCount
) {
}
