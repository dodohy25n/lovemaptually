package com.lovemaptually.entity;
import jakarta.persistence.*;import lombok.*;import java.math.BigDecimal;import java.time.OffsetDateTime;
@Entity @Table(name="places",uniqueConstraints=@UniqueConstraint(name="uq_places_provider",columnNames={"provider","provider_place_id"})) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Place {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long placeId;
 @Column(nullable=false,length=20) private String provider; @Column(nullable=false,length=64) private String providerPlaceId;
 @Column(nullable=false,length=200) private String name; @Column(nullable=false,length=300) private String address;
 @Column(nullable=false,length=50) private String region; @Column(nullable=false,length=50) private String category;
 private Short priceBand; @Column(nullable=false,precision=10,scale=7) private BigDecimal latitude; @Column(nullable=false,precision=10,scale=7) private BigDecimal longitude;
 @org.hibernate.annotations.Generated(event=org.hibernate.generator.EventType.INSERT) @Column(nullable=false,insertable=false,updatable=false) private OffsetDateTime createdAt;
}
