package com.lovemaptually.report.dto.response;

import com.lovemaptually.report.entity.ReportStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ReportSummaryResponse(
        Long reportId,
        LocalDate reportMonth,
        ReportStatus status,
        String title,
        String summary,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt
) {
}
