package com.lovemaptually.report.dto.response;

import com.lovemaptually.group.entity.Plan;
import java.util.List;

public record ReportListResponse(Plan plan, List<ReportSummaryResponse> reports) {
}
