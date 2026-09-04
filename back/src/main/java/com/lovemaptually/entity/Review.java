package com.lovemaptually.entity;
import jakarta.persistence.*;import lombok.*;import java.time.*;import static com.lovemaptually.entity.Enums.*;
@Entity @Table(name="reviews",uniqueConstraints=@UniqueConstraint(name="uq_reviews_visit",columnNames={"user_id","place_id","visited_on"})) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Review {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long reviewId;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id") private User user;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="place_id") private Place place;
 @Column(nullable=false) private LocalDate visitedOn;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="with_group_id") private RelationGroup withGroup;
 @Column(nullable=false) private Short rating; @Column(nullable=false,columnDefinition="text") private String content;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=10) private TagStatus tagStatus;
 @org.hibernate.annotations.Generated(event=org.hibernate.generator.EventType.INSERT) @Column(nullable=false,insertable=false,updatable=false) private OffsetDateTime createdAt;
 @org.hibernate.annotations.Generated(event=org.hibernate.generator.EventType.INSERT) @Column(nullable=false,insertable=false,updatable=false) private OffsetDateTime updatedAt;
}
