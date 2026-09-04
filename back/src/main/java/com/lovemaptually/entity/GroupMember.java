package com.lovemaptually.entity;
import jakarta.persistence.*;import lombok.*;import org.hibernate.annotations.JdbcTypeCode;import org.hibernate.type.SqlTypes;import java.time.OffsetDateTime;import static com.lovemaptually.entity.Enums.*;
@Entity @Table(name="group_members",uniqueConstraints=@UniqueConstraint(name="uq_group_members_pair",columnNames={"group_id","user_id"})) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GroupMember {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long groupMemberId;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="group_id") private RelationGroup group;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id") private User user;
 @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM) @Column(nullable=false,columnDefinition="member_role") private MemberRole role;
 @org.hibernate.annotations.Generated(event=org.hibernate.generator.EventType.INSERT) @Column(nullable=false,insertable=false,updatable=false) private OffsetDateTime joinedAt;
 private OffsetDateTime leftAt;
}
