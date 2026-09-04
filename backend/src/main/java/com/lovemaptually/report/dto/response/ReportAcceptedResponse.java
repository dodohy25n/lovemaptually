package com.lovemaptually.report.dto.response;

import com.lovemaptually.report.entity.ReportStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ReportAcceptedResponse(
        Long reportId,
        Long groupId,
        LocalDate reportMonth,
        ReportStatus status,
        OffsetDateTime createdAt
) {
}
