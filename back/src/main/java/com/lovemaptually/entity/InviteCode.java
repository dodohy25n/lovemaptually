package com.lovemaptually.entity;
import jakarta.persistence.*;import lombok.*;import org.hibernate.annotations.JdbcTypeCode;import org.hibernate.type.SqlTypes;import java.time.OffsetDateTime;import static com.lovemaptually.entity.Enums.*;
@Entity @Table(name="invite_codes") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InviteCode {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long inviteCodeId;
 @Column(nullable=false,unique=true,length=16) private String code;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="group_id") private RelationGroup group;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="inviter_user_id") private User inviter;
 @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM) @Column(nullable=false,columnDefinition="invite_status") private InviteStatus status;
 @Column(nullable=false) private Integer maxUses; @Column(nullable=false) private Integer useCount;
 @Column(nullable=false) private OffsetDateTime expiresAt;
 @org.hibernate.annotations.Generated(event=org.hibernate.generator.EventType.INSERT) @Column(nullable=false,insertable=false,updatable=false) private OffsetDateTime createdAt;
}
