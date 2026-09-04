package com.lovemaptually.review.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "visited_on", nullable = false)
    private LocalDate visitedOn;

    @Column(name = "with_group_id")
    private Long withGroupId;

    @Column(nullable = false)
    private Short rating;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "tag_status", nullable = false, columnDefinition = "tag_status")
    private TagStatus tagStatus;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    protected Review() {
    }

    public Review(Long userId, Long placeId, LocalDate visitedOn, Long withGroupId, short rating,
                  String content, OffsetDateTime createdAt) {
        this.userId = userId;
        this.placeId = placeId;
        this.visitedOn = visitedOn;
        this.withGroupId = withGroupId;
        this.rating = rating;
        this.content = content;
        this.tagStatus = TagStatus.PENDING;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getPlaceId() { return placeId; }
    public LocalDate getVisitedOn() { return visitedOn; }
    public Long getWithGroupId() { return withGroupId; }
    public Short getRating() { return rating; }
    public String getContent() { return content; }
    public TagStatus getTagStatus() { return tagStatus; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void markTagStatus(TagStatus status) {
        this.tagStatus = status;
    }
}
