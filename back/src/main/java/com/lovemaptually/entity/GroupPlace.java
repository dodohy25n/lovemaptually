package com.lovemaptually.entity;
import jakarta.persistence.*;import lombok.*;import org.hibernate.annotations.JdbcTypeCode;import org.hibernate.type.SqlTypes;import java.time.OffsetDateTime;import static com.lovemaptually.entity.Enums.*;
@Entity @Table(name="group_places",uniqueConstraints=@UniqueConstraint(name="uq_group_places_pair",columnNames={"group_id","place_id"})) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GroupPlace {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long groupPlaceId;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="group_id") private RelationGroup group;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="place_id") private Place place;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="added_by_user_id") private User addedBy;
 @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM) @Column(columnDefinition="place_label") private PlaceLabel label;
 @Column(nullable=false) private Integer reviewedCount; @Column(nullable=false) private Integer likedCount;
 private OffsetDateTime labelUpdatedAt;
 @org.hibernate.annotations.Generated(event=org.hibernate.generator.EventType.INSERT) @Column(nullable=false,insertable=false,updatable=false) private OffsetDateTime createdAt;
}
