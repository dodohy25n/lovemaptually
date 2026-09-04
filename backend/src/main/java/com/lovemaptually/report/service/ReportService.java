package com.lovemaptually.report.service;

import com.lovemaptually.global.common.response.ApiErrorResponse.ErrorDetail;
import com.lovemaptually.global.exception.AppException;
import com.lovemaptually.group.entity.Plan;
import com.lovemaptually.group.entity.RelationGroup;
import com.lovemaptually.group.repository.RelationGroupRepository;
import com.lovemaptually.group.service.GroupAccess;
import com.lovemaptually.report.dto.request.CreateReportRequest;
import com.lovemaptually.report.dto.request.CreateSubscriptionRequest;
import com.lovemaptually.report.dto.response.ReportAcceptedResponse;
import com.lovemaptually.report.dto.response.ReportListResponse;
import com.lovemaptually.report.dto.response.ReportResponse;
import com.lovemaptually.report.dto.response.ReportSummaryResponse;
import com.lovemaptually.report.dto.response.SubscriptionResponse;
import com.lovemaptually.report.entity.MonthlyReport;
import com.lovemaptually.report.entity.ReportStatus;
import com.lovemaptually.report.entity.Subscription;
import com.lovemaptually.report.payment.PaymentClient;
import com.lovemaptually.report.repository.MonthlyReportRepository;
import com.lovemaptually.report.repository.SubscriptionRepository;
import com.lovemaptually.report.writer.ReportDraft;
import com.lovemaptually.report.writer.ReportWriter;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-08 월간 리포트. 플랜 검사는 여기 한 곳이고 생성만 검사합니다. 열람은 플랜과 무관합니다.
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final MonthlyReportRepository reportRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final RelationGroupRepository groupRepository;
    private final GroupAccess groupAccess;
    private final ReportAggregator aggregator;
    private final ReportWriter reportWriter;
    private final PaymentClient paymentClient;

    public ReportService(MonthlyReportRepository reportRepository, SubscriptionRepository subscriptionRepository,
                         RelationGroupRepository groupRepository, GroupAccess groupAccess,
                         ReportAggregator aggregator, ReportWriter reportWriter, PaymentClient paymentClient) {
        this.reportRepository = reportRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.groupRepository = groupRepository;
        this.groupAccess = groupAccess;
        this.aggregator = aggregator;
        this.reportWriter = reportWriter;
        this.paymentClient = paymentClient;
    }

    @Transactional
    public SubscriptionResponse subscribe(Long userId, Long groupId, CreateSubscriptionRequest request) {
        RelationGroup group = groupAccess.requireMember(groupId, userId);
        if (!"PREMIUM".equalsIgnoreCase(request.plan())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_PLAN", "PREMIUM만 전환할 수 있습니다");
        }
        if (group.getPlan() == Plan.PREMIUM) {
            throw new AppException(HttpStatus.CONFLICT, "ALREADY_PREMIUM", "이미 프리미엄 그룹입니다");
        }
        String paymentRef = paymentClient.approve(groupId, "PREMIUM");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Subscription subscription = subscriptionRepository.save(
                new Subscription(groupId, Plan.PREMIUM, now, paymentRef));
        group.upgradeTo(Plan.PREMIUM);
        return new SubscriptionResponse(subscription.getId(), groupId, Plan.PREMIUM, now, paymentRef);
    }

    @Transactional
    public ReportAcceptedResponse accept(Long userId, Long groupId, CreateReportRequest request) {
        RelationGroup group = groupAccess.requireMember(groupId, userId);
        LocalDate month = parseMonth(request.month());
        if (group.getPlan() != Plan.PREMIUM) {
            throw new AppException(HttpStatus.PAYMENT_REQUIRED, "PLAN_REQUIRED",
                    "월간 리포트는 프리미엄 그룹에서 만들 수 있습니다",
                    List.of(new ErrorDetail("plan", "현재 FREE, 필요 PREMIUM")));
        }
        if (aggregator.countVisits(groupId, month) == 0) {
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_VISITS_IN_MONTH",
                    "그 달에 함께 다녀온 기록이 없습니다");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        MonthlyReport report = reportRepository.findByGroupIdAndReportMonth(groupId, month).orElse(null);
        if (report == null) {
            report = reportRepository.saveAndFlush(new MonthlyReport(groupId, month, userId, now));
        } else if (report.getStatus() == ReportStatus.FAILED) {
            report.retry(userId, now);
        } else {
            throw new AppException(HttpStatus.CONFLICT, "REPORT_ALREADY_EXISTS",
                    "%s 리포트가 이미 있습니다".formatted(request.month()),
                    List.of(new ErrorDetail("month", "reportId=" + report.getId())));
        }
        return new ReportAcceptedResponse(report.getId(), groupId, month, report.getStatus(), report.getCreatedAt());
    }

    @Transactional
    public void run(Long reportId) {
        MonthlyReport report = reportRepository.findById(reportId).orElseThrow();
        ReportInput input = aggregator.aggregate(report.getGroupId(), report.getReportMonth());
        ReportDraft draft = reportWriter.write(input);
        Verified verified = verify(draft.content(), input, draft.model());
        report.complete(draft.model(), draft.promptTokens(), draft.completionTokens(),
                verified.content(), OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional
    public void markFailed(Long reportId, String message) {
        reportRepository.findById(reportId).ifPresent(report -> report.fail(reportWriter.name(),
                Map.of("meta", Map.of("error", message == null ? "알 수 없는 오류" : message))));
    }

    /**
     * LLM이 입력 밖 장소를 가리키면 그 항목을 버리고 개수를 meta.discarded에 남깁니다.
     * 추천에서 후보 밖을 막은 것과 같은 원칙입니다.
     */
    @SuppressWarnings("unchecked")
    Verified verify(Map<String, Object> content, ReportInput input) {
        return verify(content, input, null);
    }

    @SuppressWarnings("unchecked")
    Verified verify(Map<String, Object> content, ReportInput input, String model) {
        Set<Long> visited = input.places().stream().map(ReportInput.VisitedPlace::placeId)
                .collect(Collectors.toSet());
        Set<Long> candidates = input.candidates().stream().map(ReportInput.Candidate::placeId)
                .collect(Collectors.toSet());

        Map<String, Object> cleaned = new LinkedHashMap<>(content);
        int discarded = 0;
        List<Object> highlights = new ArrayList<>();
        for (Object item : asList(content.get("highlights"))) {
            if (item instanceof Map<?, ?> map && visited.contains(placeIdOf(map))) {
                highlights.add(item);
            } else {
                discarded++;
            }
        }
        List<Object> nextMonth = new ArrayList<>();
        for (Object item : asList(content.get("nextMonth"))) {
            if (item instanceof Map<?, ?> map && candidates.contains(placeIdOf(map))) {
                nextMonth.add(item);
            } else {
                discarded++;
            }
        }
        cleaned.put("highlights", highlights);
        cleaned.put("nextMonth", nextMonth);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("model", model == null ? reportWriter.name() : model);
        meta.put("discarded", discarded);
        cleaned.put("meta", meta);
        return new Verified(cleaned, discarded);
    }

    @Transactional(readOnly = true)
    public ReportListResponse list(Long userId, Long groupId) {
        RelationGroup group = groupAccess.requireMember(groupId, userId);
        List<ReportSummaryResponse> reports = reportRepository.findByGroupIdOrderByReportMonthDesc(groupId).stream()
                .map(report -> new ReportSummaryResponse(report.getId(), report.getReportMonth(), report.getStatus(),
                        text(report, "title"), text(report, "summary"), report.getCreatedAt(),
                        report.getCompletedAt()))
                .toList();
        return new ReportListResponse(group.getPlan(), reports);
    }

    @Transactional(readOnly = true)
    public ReportResponse get(Long userId, Long reportId) {
        MonthlyReport report = reportRepository.findById(reportId).orElseThrow(() -> new AppException(
                HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "리포트를 찾을 수 없습니다"));
        groupAccess.requireMember(report.getGroupId(), userId);
        return new ReportResponse(report.getId(), report.getGroupId(), report.getReportMonth(), report.getStatus(),
                report.getRequestedByUserId(), report.getCreatedAt(), report.getCompletedAt(), report.getContent());
    }

    private String text(MonthlyReport report, String key) {
        Map<String, Object> content = report.getContent();
        if (content == null) {
            return null;
        }
        Object value = content.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Long placeIdOf(Map<?, ?> map) {
        Object value = map.get("placeId");
        return value instanceof Number number ? number.longValue() : null;
    }

    @SuppressWarnings("unchecked")
    private List<Object> asList(Object value) {
        return value instanceof List<?> list ? (List<Object>) list : List.of();
    }

    private LocalDate parseMonth(String month) {
        if (month == null || month.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_MONTH", "월은 YYYY-MM 형식입니다");
        }
        try {
            return LocalDate.parse(month.trim() + "-01");
        } catch (DateTimeParseException exception) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_MONTH", "월은 YYYY-MM 형식입니다");
        }
    }

    record Verified(Map<String, Object> content, int discarded) {
    }
}
