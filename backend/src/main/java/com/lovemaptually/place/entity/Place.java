package com.lovemaptually.place.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "places")
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_id")
    private Long id;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_place_id", nullable = false, length = 64)
    private String providerPlaceId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 300)
    private String address;

    @Column(nullable = false, length = 50)
    private String region;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "price_band")
    private Short priceBand;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Place() {
    }

    public Place(String provider, String providerPlaceId, String name, String address, String region,
                 String category, Short priceBand, BigDecimal latitude, BigDecimal longitude,
                 OffsetDateTime createdAt) {
        this.provider = provider;
        this.providerPlaceId = providerPlaceId;
        this.name = name;
        this.address = address;
        this.region = region;
        this.category = category;
        this.priceBand = priceBand;
        this.latitude = latitude;
        this.longitude = longitude;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getProvider() { return provider; }
    public String getProviderPlaceId() { return providerPlaceId; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getRegion() { return region; }
    public String getCategory() { return category; }
    public Short getPriceBand() { return priceBand; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
