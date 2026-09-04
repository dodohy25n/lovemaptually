package com.lovemaptually.review.repository;

import com.lovemaptually.review.entity.ReviewTag;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewTagRepository extends JpaRepository<ReviewTag, Long> {

    List<ReviewTag> findByReviewIdOrderByIdAsc(Long reviewId);

    List<ReviewTag> findByReviewIdIn(Collection<Long> reviewIds);
}
