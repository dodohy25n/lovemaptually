package com.lovemaptually.recommendation.repository;

import com.lovemaptually.recommendation.entity.RecommendationRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationRequestRepository extends JpaRepository<RecommendationRequest, Long> {
}
