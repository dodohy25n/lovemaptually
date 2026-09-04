package com.lovemaptually.review.repository;

import com.lovemaptually.review.entity.Review;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByWithGroupIdAndPlaceIdOrderByVisitedOnDescIdDesc(Long groupId, Long placeId);

    List<Review> findByWithGroupIdAndVisitedOnBetweenOrderByVisitedOnAscIdAsc(Long groupId, LocalDate from, LocalDate to);

    long countByWithGroupIdAndVisitedOnBetween(Long groupId, LocalDate from, LocalDate to);

    long countByUserId(Long userId);

    @Query("select r.placeId from Review r where r.withGroupId = :groupId")
    List<Long> findPlaceIdsReviewedWithGroup(Long groupId);
}
