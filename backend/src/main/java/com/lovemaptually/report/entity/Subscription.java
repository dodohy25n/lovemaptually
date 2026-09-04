package com.lovemaptually.report.entity;

import com.lovemaptually.group.entity.Plan;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_id")
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Plan plan;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "payment_ref", length = 64)
    private String paymentRef;

    protected Subscription() {
    }

    public Subscription(Long groupId, Plan plan, OffsetDateTime startedAt, String paymentRef) {
        this.groupId = groupId;
        this.plan = plan;
        this.startedAt = startedAt;
        this.paymentRef = paymentRef;
    }

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public Plan getPlan() { return plan; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getEndedAt() { return endedAt; }
    public String getPaymentRef() { return paymentRef; }
}
