package com.lovemaptually.place.entity;

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
@Table(name = "group_places")
public class GroupPlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_place_id")
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "added_by_user_id", nullable = false)
    private Long addedByUserId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "place_label")
    private PlaceLabel label;

    @Column(name = "reviewed_count", nullable = false)
    private Integer reviewedCount;

    @Column(name = "liked_count", nullable = false)
    private Integer likedCount;

    @Column(name = "label_updated_at")
    private OffsetDateTime labelUpdatedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected GroupPlace() {
    }

    public GroupPlace(Long groupId, Long placeId, Long addedByUserId, OffsetDateTime createdAt) {
        this.groupId = groupId;
        this.placeId = placeId;
        this.addedByUserId = addedByUserId;
        this.reviewedCount = 0;
        this.likedCount = 0;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public Long getPlaceId() { return placeId; }
    public Long getAddedByUserId() { return addedByUserId; }
    public PlaceLabel getLabel() { return label; }
    public Integer getReviewedCount() { return reviewedCount; }
    public Integer getLikedCount() { return likedCount; }
    public OffsetDateTime getLabelUpdatedAt() { return labelUpdatedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void relabel(PlaceLabel label, int reviewedCount, int likedCount, OffsetDateTime at) {
        this.label = label;
        this.reviewedCount = reviewedCount;
        this.likedCount = likedCount;
        this.labelUpdatedAt = at;
    }
}
