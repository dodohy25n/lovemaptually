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
@Table(name = "relation_groups")
public class RelationGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "group_type", nullable = false, columnDefinition = "group_type")
    private GroupType groupType;

    @Column(length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Plan plan;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected RelationGroup() {
    }

    public RelationGroup(GroupType groupType, String name, OffsetDateTime createdAt) {
        this.groupType = groupType;
        this.name = name;
        this.plan = Plan.FREE;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public GroupType getGroupType() { return groupType; }
    public String getName() { return name; }
    public Plan getPlan() { return plan; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void upgradeTo(Plan plan) {
        this.plan = plan;
    }
}
