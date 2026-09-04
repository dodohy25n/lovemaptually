package com.lovemaptually.entity;
import jakarta.persistence.*;import lombok.*;import java.time.OffsetDateTime;
@Entity @Table(name="users") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long userId;
 @Column(nullable=false,unique=true,length=255) private String email;
 @Column(nullable=false,length=255) private String passwordHash;
 @Column(nullable=false,length=50) private String nickname;
 @org.hibernate.annotations.Generated(event=org.hibernate.generator.EventType.INSERT) @Column(nullable=false,insertable=false,updatable=false) private OffsetDateTime createdAt;
 @org.hibernate.annotations.Generated(event=org.hibernate.generator.EventType.INSERT) @Column(nullable=false,insertable=false,updatable=false) private OffsetDateTime updatedAt;
}
