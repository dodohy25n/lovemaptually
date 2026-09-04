package com.lovemaptually.entity;
import jakarta.persistence.*;import lombok.*;import org.hibernate.annotations.JdbcTypeCode;import org.hibernate.type.SqlTypes;import java.time.OffsetDateTime;import static com.lovemaptually.entity.Enums.*;
@Entity @Table(name="relation_groups") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RelationGroup {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long groupId;
 @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM) @Column(nullable=false,columnDefinition="group_type") private GroupType groupType;
 @Column(length=50) private String name;
 @org.hibernate.annotations.Generated(event=org.hibernate.generator.EventType.INSERT) @Column(nullable=false,insertable=false,updatable=false) private OffsetDateTime createdAt;
}
