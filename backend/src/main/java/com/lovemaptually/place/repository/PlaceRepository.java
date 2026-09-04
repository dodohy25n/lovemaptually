package com.lovemaptually.place.repository;

import com.lovemaptually.place.entity.Place;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    Optional<Place> findByProviderAndProviderPlaceId(String provider, String providerPlaceId);

    @Query("""
            select p from Place p
            where (lower(p.name) like lower(concat('%', :query, '%')) or p.address like concat('%', :query, '%'))
              and (:region is null or p.region = :region)
            order by p.name asc
            """)
    Page<Place> search(String query, String region, Pageable pageable);
}
