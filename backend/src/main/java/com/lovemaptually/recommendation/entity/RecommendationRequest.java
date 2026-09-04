package com.lovemaptually.recommendation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "recommendation_requests")
public class RecommendationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "requested_by_user_id", nullable = false)
    private Long requestedByUserId;

    @Column(nullable = false, columnDefinition = "text")
    private String query;

    @Column(name = "intent_region", length = 50)
    private String intentRegion;

    @Column(name = "intent_count")
    private Short intentCount;

    @Column(name = "intent_budget")
    private Short intentBudget;

    @Column(name = "candidate_count")
    private Integer candidateCount;

    @Column(name = "cf_weight", precision = 3, scale = 2)
    private BigDecimal cfWeight;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "rec_status")
    private RecStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    protected RecommendationRequest() {
    }

    public RecommendationRequest(Long groupId, Long requestedByUserId, String query, String intentRegion,
                                 Short intentCount, Short intentBudget, OffsetDateTime createdAt) {
        this.groupId = groupId;
        this.requestedByUserId = requestedByUserId;
        this.query = query;
        this.intentRegion = intentRegion;
        this.intentCount = intentCount;
        this.intentBudget = intentBudget;
        this.status = RecStatus.PENDING;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public Long getRequestedByUserId() { return requestedByUserId; }
    public String getQuery() { return query; }
    public String getIntentRegion() { return intentRegion; }
    public Short getIntentCount() { return intentCount; }
    public Short getIntentBudget() { return intentBudget; }
    public Integer getCandidateCount() { return candidateCount; }
    public BigDecimal getCfWeight() { return cfWeight; }
    public RecStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }

    public void complete(int candidateCount, BigDecimal cfWeight, OffsetDateTime at) {
        this.candidateCount = candidateCount;
        this.cfWeight = cfWeight;
        this.status = RecStatus.COMPLETED;
        this.completedAt = at;
    }

    public void fail(OffsetDateTime at) {
        this.status = RecStatus.FAILED;
        this.completedAt = at;
    }
}
