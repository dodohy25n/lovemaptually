package com.lovemaptually.report.dto.request;

import jakarta.validation.constraints.Pattern;

public record CreateReportRequest(
        @Pattern(regexp = "^[0-9]{4}-[0-9]{2}$", message = "월은 YYYY-MM 형식입니다") String month
) {
}
