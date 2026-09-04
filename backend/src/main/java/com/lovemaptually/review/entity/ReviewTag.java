package com.lovemaptually.review.entity;

import com.lovemaptually.tag.entity.AttrLevel;
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
@Table(name = "review_tags")
public class ReviewTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_tag_id")
    private Long id;

    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "fact_value", columnDefinition = "attr_level")
    private AttrLevel factValue;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "want_value", columnDefinition = "attr_level")
    private AttrLevel wantValue;

    @Column(name = "evidence_text", columnDefinition = "text")
    private String evidenceText;

    protected ReviewTag() {
    }

    public ReviewTag(Long reviewId, Long tagId, AttrLevel factValue, AttrLevel wantValue, String evidenceText) {
        this.reviewId = reviewId;
        this.tagId = tagId;
        this.factValue = factValue;
        this.wantValue = wantValue;
        this.evidenceText = evidenceText;
    }

    public Long getId() { return id; }
    public Long getReviewId() { return reviewId; }
    public Long getTagId() { return tagId; }
    public AttrLevel getFactValue() { return factValue; }
    public AttrLevel getWantValue() { return wantValue; }
    public String getEvidenceText() { return evidenceText; }
}
