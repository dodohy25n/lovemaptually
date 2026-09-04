package com.lovemaptually.report.controller;

import com.lovemaptually.global.common.response.ApiResponse;
import com.lovemaptually.global.security.AuthenticatedUser;
import com.lovemaptually.report.dto.request.CreateReportRequest;
import com.lovemaptually.report.dto.request.CreateSubscriptionRequest;
import com.lovemaptually.report.dto.response.ReportAcceptedResponse;
import com.lovemaptually.report.dto.response.ReportListResponse;
import com.lovemaptually.report.dto.response.ReportResponse;
import com.lovemaptually.report.dto.response.SubscriptionResponse;
import com.lovemaptually.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "8. 월간 리포트", description = "유료 기능. 숫자는 SQL이 세고 문장은 LLM이 씁니다")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "프리미엄 전환", description = "Mock 결제로 즉시 승인하고 승인 번호를 남깁니다")
    @PostMapping("/groups/{groupId}/subscriptions")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> subscribe(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long groupId,
            @Valid @RequestBody CreateSubscriptionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(
                HttpStatus.CREATED.value(), "프리미엄으로 전환했습니다",
                reportService.subscribe(AuthenticatedUser.id(jwt), groupId, request)));
    }

    @Operation(summary = "리포트 생성 요청", description = "FREE 그룹은 402이고 그 달 기록이 없으면 422입니다")
    @PostMapping("/groups/{groupId}/reports")
    public ResponseEntity<ApiResponse<ReportAcceptedResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long groupId,
            @Valid @RequestBody CreateReportRequest request
    ) {
        ReportAcceptedResponse accepted = reportService.accept(AuthenticatedUser.id(jwt), groupId, request);
        reportService.process(accepted.reportId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.of(
                HttpStatus.ACCEPTED.value(), "리포트를 쓰고 있습니다", accepted));
    }

    @Operation(summary = "리포트 목록")
    @GetMapping("/groups/{groupId}/reports")
    public ApiResponse<ReportListResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable Long groupId) {
        return ApiResponse.of(200, "조회했습니다", reportService.list(AuthenticatedUser.id(jwt), groupId));
    }

    @Operation(summary = "리포트 조회", description = "구독을 취소해도 이미 만든 리포트는 열립니다")
    @GetMapping("/reports/{reportId}")
    public ApiResponse<ReportResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long reportId) {
        return ApiResponse.of(200, "조회했습니다", reportService.get(AuthenticatedUser.id(jwt), reportId));
    }
}
