package com.lovemaptually.recommendation.client;

import com.lovemaptually.tag.entity.Tag;
import com.lovemaptually.tag.service.TagCatalog;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 추천 엔진이 죽었을 때 태그 방향 일치율만으로 순위를 냅니다.
 * 협업 필터링은 빠지므로 응답에 degraded를 실어 사용자에게 밝힙니다.
 */
@Component
public class RuleFallbackRecommendationClient implements RecommendationClient {

    private static final int JUDGE_THRESHOLD = 2;

    private final EntityManager entityManager;
    private final TagCatalog tagCatalog;

    public RuleFallbackRecommendationClient(EntityManager entityManager, TagCatalog tagCatalog) {
        this.entityManager = entityManager;
        this.tagCatalog = tagCatalog;
    }

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public RecommendationResult recommend(RecommendationCommand command) {
        List<Number> candidateRows = entityManager.createNativeQuery("""
                SELECT DISTINCT p.place_id
                FROM places p
                JOIN place_tags pt ON pt.place_id = p.place_id
                LEFT JOIN group_places gp ON gp.place_id = p.place_id AND gp.group_id = :groupId
                WHERE p.region = :region
                  AND (gp.label IS NULL OR gp.label <> 'ON_HOLD')
                  AND (cast(:budget as integer) IS NULL OR p.price_band IS NULL OR p.price_band <= cast(:budget as integer))
                  AND NOT EXISTS (
                      SELECT 1 FROM reviews r WHERE r.place_id = p.place_id AND r.user_id IN (:memberIds)
                  )
                """)
                .setParameter("groupId", command.groupId())
                .setParameter("region", command.region())
                .setParameter("budget", command.budget())
                .setParameter("memberIds", command.memberIds())
                .getResultList();
        List<Long> candidates = candidateRows.stream().map(Number::longValue).toList();
        if (candidates.isEmpty()) {
            return new RecommendationResult(0, 0.0, true, "이 지역에서 아직 추천할 곳을 찾지 못했습니다", List.of());
        }

        Map<Long, Map<Long, String>> placeSides = placeSides(candidates);
        Map<Long, Map<Long, String>> memberSides = memberSides(command.memberIds());
        Map<Long, Tag> tags = tagCatalog.byId();

        List<Scored> scored = new ArrayList<>();
        for (Long placeId : candidates) {
            Map<Long, String> place = placeSides.getOrDefault(placeId, Map.of());
            double minimum = Double.MAX_VALUE;
            double sum = 0;
            Set<String> matched = new LinkedHashSet<>();
            for (Long memberId : command.memberIds()) {
                Map<Long, String> member = memberSides.getOrDefault(memberId, Map.of());
                int both = 0;
                int agreed = 0;
                for (Map.Entry<Long, String> entry : place.entrySet()) {
                    String memberSide = member.get(entry.getKey());
                    if (memberSide == null) {
                        continue;
                    }
                    both++;
                    if (memberSide.equals(entry.getValue())) {
                        agreed++;
                        Tag tag = tags.get(entry.getKey());
                        if (tag != null) {
                            matched.add(tag.getName());
                        }
                    }
                }
                double ratio = both == 0 ? 0.5 : (double) agreed / both;
                double predicted = 1 + 4 * ratio;
                minimum = Math.min(minimum, predicted);
                sum += predicted;
            }
            scored.add(new Scored(placeId, minimum, sum / command.memberIds().size(), List.copyOf(matched)));
        }

        List<Scored> passed = scored.stream().filter(item -> item.minimum() >= 2.5).toList();
        List<Scored> ranked = (passed.isEmpty() ? scored : passed).stream()
                .sorted((left, right) -> Double.compare(right.average(), left.average()))
                .limit(command.count())
                .toList();

        List<RecommendationItem> items = new ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            Scored item = ranked.get(index);
            items.add(new RecommendationItem(item.placeId(), item.matchedTags(), "OTHERS",
                    reasonOf(item.matchedTags()), index + 1));
        }
        return new RecommendationResult(candidates.size(), 0.0, true,
                "추천 엔진에 연결하지 못해 취향 태그만으로 골랐습니다", items);
    }

    @Override
    public String name() {
        return "rule-fallback";
    }

    private String reasonOf(List<String> matchedTags) {
        if (matchedTags.isEmpty()) {
            return "이 지역에서 기록이 남아 있는 곳이라 골랐습니다.";
        }
        return String.join(", ", matchedTags) + " 쪽이 두 분 취향과 맞아 골랐습니다.";
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Map<Long, String>> placeSides(List<Long> placeIds) {
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT place_id, tag_id, fact_high_count, fact_low_count
                FROM place_tags WHERE place_id IN (:placeIds)
                """)
                .setParameter("placeIds", placeIds)
                .getResultList();
        Map<Long, Map<Long, String>> sides = new LinkedHashMap<>();
        for (Object[] row : rows) {
            int high = ((Number) row[2]).intValue();
            int low = ((Number) row[3]).intValue();
            if (high == low) {
                continue;
            }
            sides.computeIfAbsent(((Number) row[0]).longValue(), key -> new LinkedHashMap<>())
                    .put(((Number) row[1]).longValue(), high > low ? "HIGH" : "LOW");
        }
        return sides;
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Map<Long, String>> memberSides(List<Long> memberIds) {
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT user_id, tag_id, want_high_count, want_low_count
                FROM user_tags WHERE user_id IN (:memberIds)
                """)
                .setParameter("memberIds", memberIds)
                .getResultList();
        Map<Long, Map<Long, String>> sides = new LinkedHashMap<>();
        for (Object[] row : rows) {
            int high = ((Number) row[2]).intValue();
            int low = ((Number) row[3]).intValue();
            String side = null;
            if (high >= JUDGE_THRESHOLD && high > low) {
                side = "HIGH";
            } else if (low >= JUDGE_THRESHOLD && low > high) {
                side = "LOW";
            }
            if (side == null) {
                continue;
            }
            sides.computeIfAbsent(((Number) row[0]).longValue(), key -> new LinkedHashMap<>())
                    .put(((Number) row[1]).longValue(), side);
        }
        return sides;
    }

    private record Scored(Long placeId, double minimum, double average, List<String> matchedTags) {
    }
}
