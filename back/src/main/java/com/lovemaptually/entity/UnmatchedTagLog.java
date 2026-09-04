package com.lovemaptually.entity;
import jakarta.persistence.*;import lombok.*;import java.time.OffsetDateTime;
@Entity @Table(name="unmatched_tag_logs") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UnmatchedTagLog {@Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long logId;@ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="review_id") private Review review;@Column(nullable=false,length=50) private String rawTag;@org.hibernate.annotations.Generated(event=org.hibernate.generator.EventType.INSERT) @Column(nullable=false,insertable=false,updatable=false) private OffsetDateTime createdAt;}
