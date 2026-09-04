package com.lovemaptually.preference.dto.response;

import java.util.List;

public record GroupPreferencesResponse(Long groupId, List<PreferenceItemResponse> preferences) {
}
