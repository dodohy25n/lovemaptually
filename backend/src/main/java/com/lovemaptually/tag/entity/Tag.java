package com.lovemaptually.tag.entity;

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
@Table(name = "tags")
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tag_id")
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "tag_axis")
    private TagAxis axis;

    @Column(name = "high_label", nullable = false, length = 20)
    private String highLabel;

    @Column(name = "low_label", nullable = false, length = 20)
    private String lowLabel;

    protected Tag() {
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public TagAxis getAxis() { return axis; }
    public String getHighLabel() { return highLabel; }
    public String getLowLabel() { return lowLabel; }

    public String labelOf(AttrLevel level) {
        if (level == null) {
            return null;
        }
        return level == AttrLevel.HIGH ? highLabel : lowLabel;
    }

    public AttrLevel levelOf(String label) {
        if (label == null) {
            return null;
        }
        if (highLabel.equals(label)) {
            return AttrLevel.HIGH;
        }
        if (lowLabel.equals(label)) {
            return AttrLevel.LOW;
        }
        return null;
    }
}
