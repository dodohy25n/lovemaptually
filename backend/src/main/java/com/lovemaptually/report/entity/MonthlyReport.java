package com.lovemaptually.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "monthly_reports")
public class MonthlyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "report_month", nullable = false)
    private LocalDate reportMonth;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "report_status")
    private ReportStatus status;

    @Column(length = 50)
    private String model;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> content;

    @Column(name = "requested_by_user_id", nullable = false)
    private Long requestedByUserId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    protected MonthlyReport() {
    }

    public MonthlyReport(Long groupId, LocalDate reportMonth, Long requestedByUserId, OffsetDateTime createdAt) {
        this.groupId = groupId;
        this.reportMonth = reportMonth;
        this.status = ReportStatus.PENDING;
        this.requestedByUserId = requestedByUserId;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public LocalDate getReportMonth() { return reportMonth; }
    public ReportStatus getStatus() { return status; }
    public String getModel() { return model; }
    public Integer getPromptTokens() { return promptTokens; }
    public Integer getCompletionTokens() { return completionTokens; }
    public Map<String, Object> getContent() { return content; }
    public Long getRequestedByUserId() { return requestedByUserId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }

    public void retry(Long requestedByUserId, OffsetDateTime at) {
        this.status = ReportStatus.PENDING;
        this.content = null;
        this.model = null;
        this.promptTokens = null;
        this.completionTokens = null;
        this.completedAt = null;
        this.requestedByUserId = requestedByUserId;
        this.createdAt = at;
    }

    public void complete(String model, Integer promptTokens, Integer completionTokens,
                         Map<String, Object> content, OffsetDateTime at) {
        this.status = ReportStatus.COMPLETED;
        this.model = model;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.content = content;
        this.completedAt = at;
    }

    public void fail(String model, Map<String, Object> content) {
        this.status = ReportStatus.FAILED;
        this.model = model;
        this.content = content;
    }
}
