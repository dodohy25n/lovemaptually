package com.lovemaptually.group.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "invite_codes")
public class InviteCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "invite_code_id")
    private Long id;

    @Column(nullable = false, unique = true, length = 16)
    private String code;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "inviter_user_id", nullable = false)
    private Long inviterUserId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "invite_status")
    private InviteStatus status;

    @Column(name = "max_uses", nullable = false)
    private Integer maxUses;

    @Column(name = "use_count", nullable = false)
    private Integer useCount;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected InviteCode() {
    }

    public InviteCode(String code, Long groupId, Long inviterUserId, int maxUses,
                      OffsetDateTime expiresAt, OffsetDateTime createdAt) {
        this.code = code;
        this.groupId = groupId;
        this.inviterUserId = inviterUserId;
        this.status = InviteStatus.ACTIVE;
        this.maxUses = maxUses;
        this.useCount = 0;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public Long getGroupId() { return groupId; }
    public Long getInviterUserId() { return inviterUserId; }
    public InviteStatus getStatus() { return status; }
    public Integer getMaxUses() { return maxUses; }
    public Integer getUseCount() { return useCount; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public boolean isAvailable(OffsetDateTime now) {
        return status == InviteStatus.ACTIVE && now.isBefore(expiresAt) && useCount < maxUses;
    }

    public void consume() {
        useCount++;
        if (useCount >= maxUses) {
            status = InviteStatus.EXPIRED;
        }
    }
}
