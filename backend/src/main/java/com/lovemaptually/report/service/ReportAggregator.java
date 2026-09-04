package com.lovemaptually.report.service;

import com.lovemaptually.group.entity.GroupMember;
import com.lovemaptually.group.service.GroupAccess;
import com.lovemaptually.report.service.ReportInput.Candidate;
import com.lovemaptually.report.service.ReportInput.MemberReview;
import com.lovemaptually.report.service.ReportInput.SplitTag;
import com.lovemaptually.report.service.ReportInput.TagShift;
import com.lovemaptually.report.service.ReportInput.VisitedPlace;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리포트가 쓸 숫자를 전부 SQL로 셉니다. 월 경계는 Asia/Seoul 기준 1일부터 말일까지이고 visited_on으로 판정합니다.
 */
@Component
public class ReportAggregator {

    private static final int JUDGE_THRESHOLD = 2;

    private final EntityManager entityManager;
    private final GroupAccess groupAccess;

    public ReportAggregator(EntityManager entityManager, GroupAccess groupAccess) {
        this.entityManager = entityManager;
        this.groupAccess = groupAccess;
    }

    @Transactional(readOnly = true)
    public long countVisits(Long groupId, LocalDate month) {
        return ((Number) entityManager.createNativeQuery("""
                SELECT count(*) FROM reviews
                WHERE with_group_id = :groupId AND visited_on >= :from AND visited_on < :to
                """)
                .setParameter("groupId", groupId)
                .setParameter("from", month)
                .setParameter("to", month.plusMonths(1))
                .getSingleResult()).longValue();
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public ReportInput aggregate(Long groupId, LocalDate month) {
        Map<Long, String> aliases = aliases(groupId);

        List<Object[]> reviewRows = entityManager.createNativeQuery("""
                SELECT p.place_id, p.name, p.category, gp.label, gp.reviewed_count, gp.liked_count,
                       r.user_id, r.rating, r.content, r.review_id, r.visited_on
                FROM reviews r
                JOIN places p ON p.place_id = r.place_id
                LEFT JOIN group_places gp ON gp.group_id = :groupId AND gp.place_id = r.place_id
                WHERE r.with_group_id = :groupId AND r.visited_on >= :from AND r.visited_on < :to
                ORDER BY p.place_id, r.visited_on
                """)
                .setParameter("groupId", groupId)
                .setParameter("from", month)
                .setParameter("to", month.plusMonths(1))
                .getResultList();

        Map<Long, List<String>> tagsByReview = tagsByReview(reviewRows.stream()
                .map(row -> ((Number) row[9]).longValue()).toList());

        Map<Long, VisitedPlaceBuilder> builders = new LinkedHashMap<>();
        for (Object[] row : reviewRows) {
            Long placeId = ((Number) row[0]).longValue();
            VisitedPlaceBuilder builder = builders.computeIfAbsent(placeId, key -> new VisitedPlaceBuilder(
                    placeId, (String) row[1], (String) row[2],
                    row[3] == null ? null : String.valueOf(row[3]),
                    row[4] == null ? 0 : ((Number) row[4]).intValue(),
                    row[5] == null ? 0 : ((Number) row[5]).intValue()));
            Long reviewId = ((Number) row[9]).longValue();
            builder.reviews().add(new MemberReview(
                    aliases.getOrDefault(((Number) row[6]).longValue(), "A"),
                    ((Number) row[7]).intValue(),
                    (String) row[8],
                    tagsByReview.getOrDefault(reviewId, List.of())));
            builder.visits().add(String.valueOf(row[10]));
        }

        List<VisitedPlace> places = builders.values().stream()
                .map(builder -> new VisitedPlace(builder.placeId(), builder.name(), builder.category(),
                        builder.label(), builder.reviewedCount(), builder.likedCount(),
                        (int) builder.visits().stream().distinct().count(), builder.reviews()))
                .toList();

        return new ReportInput(month.toString().substring(0, 7), (int) countVisits(groupId, month),
                (int) countVisits(groupId, month.minusMonths(1)), places,
                tagShifts(groupId, month), splitTags(groupId, aliases),
                candidates(groupId, places), Map.copyOf(aliases.values().stream()
                .collect(LinkedHashMap::new, (map, alias) -> map.put(alias, alias), Map::putAll)));
    }

    /**
     * 이메일과 닉네임과 userId는 LLM에 보내지 않습니다. 구성원은 합류 순서대로 A와 B가 됩니다.
     */
    private Map<Long, String> aliases(Long groupId) {
        Map<Long, String> aliases = new LinkedHashMap<>();
        List<GroupMember> members = groupAccess.activeMembers(groupId);
        for (int index = 0; index < members.size(); index++) {
            aliases.put(members.get(index).getUserId(), String.valueOf((char) ('A' + index)));
        }
        return aliases;
    }

    @SuppressWarnings("unchecked")
    private Map<Long, List<String>> tagsByReview(List<Long> reviewIds) {
        Map<Long, List<String>> tags = new LinkedHashMap<>();
        if (reviewIds.isEmpty()) {
            return tags;
        }
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT rt.review_id, t.name,
                       CASE WHEN rt.fact_value = 'HIGH' THEN t.high_label
                            WHEN rt.fact_value = 'LOW' THEN t.low_label END,
                       CASE WHEN rt.want_value = 'HIGH' THEN t.high_label
                            WHEN rt.want_value = 'LOW' THEN t.low_label END
                FROM review_tags rt JOIN tags t ON t.tag_id = rt.tag_id
                WHERE rt.review_id IN (:reviewIds)
                ORDER BY rt.review_tag_id
                """)
                .setParameter("reviewIds", reviewIds)
                .getResultList();
        for (Object[] row : rows) {
            String fact = row[2] == null ? "없음" : (String) row[2];
            String want = row[3] == null ? "없음" : (String) row[3];
            tags.computeIfAbsent(((Number) row[0]).longValue(), key -> new ArrayList<>())
                    .add("%s(가게 %s, 원함 %s)".formatted(row[1], fact, want));
        }
        return tags;
    }

    @SuppressWarnings("unchecked")
    private List<TagShift> tagShifts(Long groupId, LocalDate month) {
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT t.name,
                       CASE WHEN rt.want_value = 'HIGH' THEN t.high_label ELSE t.low_label END,
                       count(*)
                FROM review_tags rt
                JOIN reviews r ON r.review_id = rt.review_id
                JOIN tags t ON t.tag_id = rt.tag_id
                WHERE r.with_group_id = :groupId AND r.visited_on >= :from AND r.visited_on < :to
                  AND rt.want_value IS NOT NULL
                GROUP BY t.name, rt.want_value, t.high_label, t.low_label
                HAVING count(*) >= 2
                ORDER BY count(*) DESC
                LIMIT 5
                """)
                .setParameter("groupId", groupId)
                .setParameter("from", month)
                .setParameter("to", month.plusMonths(1))
                .getResultList();
        List<TagShift> shifts = new ArrayList<>();
        for (Object[] row : rows) {
            shifts.add(new TagShift((String) row[0], (String) row[1], ((Number) row[2]).intValue()));
        }
        return shifts;
    }

    @SuppressWarnings("unchecked")
    private List<SplitTag> splitTags(Long groupId, Map<Long, String> aliases) {
        if (aliases.size() < 2) {
            return List.of();
        }
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT t.name, ut.user_id,
                       CASE WHEN ut.want_high_count >= :threshold AND ut.want_high_count > ut.want_low_count
                            THEN t.high_label
                            WHEN ut.want_low_count >= :threshold AND ut.want_low_count > ut.want_high_count
                            THEN t.low_label END AS side
                FROM user_tags ut JOIN tags t ON t.tag_id = ut.tag_id
                WHERE ut.user_id IN (:memberIds)
                ORDER BY t.name
                """)
                .setParameter("threshold", JUDGE_THRESHOLD)
                .setParameter("memberIds", aliases.keySet())
                .getResultList();
        Map<String, Map<String, String>> byTag = new LinkedHashMap<>();
        for (Object[] row : rows) {
            if (row[2] == null) {
                continue;
            }
            byTag.computeIfAbsent((String) row[0], key -> new LinkedHashMap<>())
                    .put(aliases.get(((Number) row[1]).longValue()), (String) row[2]);
        }
        List<SplitTag> splits = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : byTag.entrySet()) {
            Map<String, String> sides = entry.getValue();
            if (sides.size() >= 2 && sides.values().stream().distinct().count() > 1) {
                // A와 B는 합류 순서로 고정된 별칭이라 SQL 행 순서가 아니라 별칭 순서로 짝지어야 합니다.
                List<String> members = new ArrayList<>(sides.keySet());
                members.sort(String::compareTo);
                splits.add(new SplitTag(entry.getKey(), sides.get(members.get(0)), sides.get(members.get(1))));
            }
        }
        return splits;
    }

    /**
     * 다음 달 제안 후보는 추천 후보 수집과 같은 규칙입니다. LLM은 이 집합 밖의 장소를 말할 수 없습니다.
     */
    @SuppressWarnings("unchecked")
    private List<Candidate> candidates(Long groupId, List<VisitedPlace> visited) {
        List<String> regions = entityManager.createNativeQuery("""
                SELECT DISTINCT p.region FROM group_places gp JOIN places p ON p.place_id = gp.place_id
                WHERE gp.group_id = :groupId
                """)
                .setParameter("groupId", groupId)
                .getResultList();
        if (regions.isEmpty()) {
            return List.of();
        }
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT p.place_id, p.name, p.category, string_agg(DISTINCT t.name, ',')
                FROM places p
                JOIN place_tags pt ON pt.place_id = p.place_id
                JOIN tags t ON t.tag_id = pt.tag_id
                LEFT JOIN group_places gp ON gp.place_id = p.place_id AND gp.group_id = :groupId
                WHERE p.region IN (:regions)
                  AND (gp.label IS NULL OR gp.label <> 'ON_HOLD')
                  AND NOT EXISTS (
                      SELECT 1 FROM reviews r WHERE r.place_id = p.place_id AND r.with_group_id = :groupId
                  )
                GROUP BY p.place_id, p.name, p.category
                ORDER BY p.place_id
                LIMIT 10
                """)
                .setParameter("groupId", groupId)
                .setParameter("regions", regions)
                .getResultList();
        List<Candidate> candidates = new ArrayList<>();
        for (Object[] row : rows) {
            candidates.add(new Candidate(((Number) row[0]).longValue(), (String) row[1], (String) row[2],
                    row[3] == null ? List.of() : List.of(String.valueOf(row[3]).split(","))));
        }
        return candidates;
    }

    private record VisitedPlaceBuilder(Long placeId, String name, String category, String label,
                                       int reviewedCount, int likedCount,
                                       List<MemberReview> reviews, List<String> visits) {
        VisitedPlaceBuilder(Long placeId, String name, String category, String label,
                            int reviewedCount, int likedCount) {
            this(placeId, name, category, label, reviewedCount, likedCount, new ArrayList<>(), new ArrayList<>());
        }
    }
}
