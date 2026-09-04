package com.lovemaptually.review.service;

import com.lovemaptually.place.entity.GroupPlace;
import com.lovemaptually.place.entity.PlaceLabel;
import com.lovemaptually.place.repository.GroupPlaceRepository;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 집계 캐시 3종을 리뷰 저장 트랜잭션 안에서 다시 셉니다(D-08, D-25).
 * place_tags는 1인 1표로 각 사람의 최신 리뷰만 세고, user_tags는 리뷰마다 1표입니다(D-28).
 */
@Component
public class AggregateRecalculator {

    private final EntityManager entityManager;
    private final GroupPlaceRepository groupPlaceRepository;

    public AggregateRecalculator(EntityManager entityManager, GroupPlaceRepository groupPlaceRepository) {
        this.entityManager = entityManager;
        this.groupPlaceRepository = groupPlaceRepository;
    }

    /**
     * 그 장소의 그 태그들을 통째로 다시 셉니다. 증분이 아니라 재계산이라 수정과 삭제에도 어긋나지 않습니다.
     */
    public void recomputePlaceTags(Long placeId, Collection<Long> tagIds) {
        if (tagIds.isEmpty()) {
            return;
        }
        entityManager.createNativeQuery("""
                DELETE FROM place_tags WHERE place_id = :placeId AND tag_id IN (:tagIds)
                """)
                .setParameter("placeId", placeId)
                .setParameter("tagIds", tagIds)
                .executeUpdate();
        entityManager.createNativeQuery("""
                INSERT INTO place_tags (place_id, tag_id, fact_high_count, fact_low_count, recomputed_at)
                SELECT :placeId, latest.tag_id,
                       count(*) FILTER (WHERE latest.fact_value = 'HIGH'),
                       count(*) FILTER (WHERE latest.fact_value = 'LOW'),
                       now()
                FROM (
                    SELECT DISTINCT ON (r.user_id, rt.tag_id) r.user_id, rt.tag_id, rt.fact_value
                    FROM review_tags rt
                    JOIN reviews r ON r.review_id = rt.review_id
                    WHERE r.place_id = :placeId AND rt.tag_id IN (:tagIds) AND rt.fact_value IS NOT NULL
                    ORDER BY r.user_id, rt.tag_id, r.visited_on DESC, r.review_id DESC
                ) latest
                GROUP BY latest.tag_id
                HAVING count(*) > 0
                """)
                .setParameter("placeId", placeId)
                .setParameter("tagIds", tagIds)
                .executeUpdate();
    }

    /**
     * 그 사람의 그 태그들을 review_tags에서 다시 셉니다. want는 rating과 무관합니다(D-28).
     */
    public void recomputeUserTags(Long userId, Collection<Long> tagIds) {
        if (tagIds.isEmpty()) {
            return;
        }
        entityManager.createNativeQuery("""
                DELETE FROM user_tags WHERE user_id = :userId AND tag_id IN (:tagIds)
                """)
                .setParameter("userId", userId)
                .setParameter("tagIds", tagIds)
                .executeUpdate();
        entityManager.createNativeQuery("""
                INSERT INTO user_tags (user_id, tag_id, want_high_count, want_low_count, updated_at)
                SELECT :userId, rt.tag_id,
                       count(*) FILTER (WHERE rt.want_value = 'HIGH'),
                       count(*) FILTER (WHERE rt.want_value = 'LOW'),
                       now()
                FROM review_tags rt
                JOIN reviews r ON r.review_id = rt.review_id
                WHERE r.user_id = :userId AND rt.tag_id IN (:tagIds) AND rt.want_value IS NOT NULL
                GROUP BY rt.tag_id
                """)
                .setParameter("userId", userId)
                .setParameter("tagIds", tagIds)
                .executeUpdate();
    }

    /**
     * 라벨의 분모는 그룹 인원이 아니라 리뷰를 쓴 구성원 수입니다(D-05).
     * 한 사람이 여러 번 갔으면 최신 방문의 별점만 셉니다.
     */
    @SuppressWarnings("unchecked")
    public LabelSnapshot recomputeGroupPlace(Long groupId, Long placeId, OffsetDateTime at) {
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT count(*), count(*) FILTER (WHERE latest.rating >= 4)
                FROM (
                    SELECT DISTINCT ON (r.user_id) r.user_id, r.rating
                    FROM reviews r
                    WHERE r.with_group_id = :groupId AND r.place_id = :placeId
                    ORDER BY r.user_id, r.visited_on DESC, r.review_id DESC
                ) latest
                """)
                .setParameter("groupId", groupId)
                .setParameter("placeId", placeId)
                .getResultList();
        Object[] row = rows.get(0);
        int reviewedCount = ((Number) row[0]).intValue();
        int likedCount = ((Number) row[1]).intValue();
        PlaceLabel label = labelOf(reviewedCount, likedCount);

        Optional<GroupPlace> groupPlace = groupPlaceRepository.findByGroupIdAndPlaceId(groupId, placeId);
        groupPlace.ifPresent(place -> place.relabel(label, reviewedCount, likedCount, at));
        return new LabelSnapshot(label, reviewedCount, likedCount);
    }

    private PlaceLabel labelOf(int reviewedCount, int likedCount) {
        if (reviewedCount == 0) {
            return null;
        }
        if (likedCount == reviewedCount) {
            return PlaceLabel.ALL_LIKED;
        }
        if (likedCount == 0) {
            return PlaceLabel.ON_HOLD;
        }
        return PlaceLabel.MIXED;
    }

    public record LabelSnapshot(PlaceLabel label, int reviewedCount, int likedCount) {
    }
}
