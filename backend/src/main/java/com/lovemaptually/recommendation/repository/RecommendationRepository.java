package com.lovemaptually.recommendation.repository;

import com.lovemaptually.recommendation.entity.Recommendation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    List<Recommendation> findByRequestIdOrderByDisplayOrderAsc(Long requestId);
}
