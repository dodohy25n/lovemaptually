package com.lovemaptually.recommendation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "recommendations")
public class Recommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommendation_id")
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "matched_tags", columnDefinition = "text[]")
    private String[] matchedTags;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "rec_basis")
    private RecBasis basis;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "display_order", nullable = false)
    private Short displayOrder;

    protected Recommendation() {
    }

    public Recommendation(Long requestId, Long placeId, String[] matchedTags, RecBasis basis,
                          String reason, short displayOrder) {
        this.requestId = requestId;
        this.placeId = placeId;
        this.matchedTags = matchedTags;
        this.basis = basis;
        this.reason = reason;
        this.displayOrder = displayOrder;
    }

    public Long getId() { return id; }
    public Long getRequestId() { return requestId; }
    public Long getPlaceId() { return placeId; }
    public String[] getMatchedTags() { return matchedTags; }
    public RecBasis getBasis() { return basis; }
    public String getReason() { return reason; }
    public Short getDisplayOrder() { return displayOrder; }
}
