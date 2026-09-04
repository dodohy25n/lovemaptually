package com.lovemaptually.report.dto.response;

import com.lovemaptually.report.entity.ReportStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

public record ReportResponse(
        Long reportId,
        Long groupId,
        LocalDate reportMonth,
        ReportStatus status,
        Long requestedByUserId,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        Map<String, Object> content
) {
}
