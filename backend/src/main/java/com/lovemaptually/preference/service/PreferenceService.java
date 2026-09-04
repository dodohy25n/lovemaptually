package com.lovemaptually.preference.service;

import com.lovemaptually.group.entity.GroupMember;
import com.lovemaptually.group.service.GroupAccess;
import com.lovemaptually.preference.dto.response.GroupPreferencesResponse;
import com.lovemaptually.preference.dto.response.PreferenceItemResponse;
import com.lovemaptually.preference.dto.response.PreferenceMemberResponse;
import com.lovemaptually.tag.entity.AttrLevel;
import com.lovemaptually.tag.entity.Tag;
import com.lovemaptually.tag.service.TagCatalog;
import com.lovemaptually.user.entity.User;
import com.lovemaptually.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 우리 취향(UC-05). 방향 판정은 2회 임계이고, 분모는 그 태그에서 판정이 난 구성원 수입니다.
 */
@Service
public class PreferenceService {

    private static final int JUDGE_THRESHOLD = 2;

    private final GroupAccess groupAccess;
    private final UserRepository userRepository;
    private final TagCatalog tagCatalog;
    private final EntityManager entityManager;

    public PreferenceService(GroupAccess groupAccess, UserRepository userRepository,
                             TagCatalog tagCatalog, EntityManager entityManager) {
        this.groupAccess = groupAccess;
        this.userRepository = userRepository;
        this.tagCatalog = tagCatalog;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public GroupPreferencesResponse of(Long groupId, Long userId) {
        groupAccess.requireMember(groupId, userId);
        List<GroupMember> members = groupAccess.activeMembers(groupId);
        List<Long> memberIds = members.stream().map(GroupMember::getUserId).toList();
        Map<Long, String> nicknames = new LinkedHashMap<>();
        userRepository.findAllById(memberIds).forEach(user -> nicknames.put(user.getId(), user.getNickname()));

        Map<Long, Map<Long, int[]>> counts = wantCounts(memberIds);
        Map<Long, Tag> tags = tagCatalog.byId();

        List<PreferenceItemResponse> preferences = new ArrayList<>();
        for (Map.Entry<Long, Map<Long, int[]>> entry : counts.entrySet()) {
            Tag tag = tags.get(entry.getKey());
            if (tag == null) {
                continue;
            }
            List<PreferenceMemberResponse> memberViews = new ArrayList<>();
            List<AttrLevel> judged = new ArrayList<>();
            for (Long memberId : memberIds) {
                int[] value = entry.getValue().getOrDefault(memberId, new int[]{0, 0});
                AttrLevel side = sideOf(value[0], value[1]);
                if (side != null) {
                    judged.add(side);
                }
                memberViews.add(new PreferenceMemberResponse(memberId, nicknames.get(memberId),
                        side == null ? null : side.name(), tag.labelOf(side), value[0], value[1]));
            }
            if (judged.isEmpty()) {
                continue;
            }
            String label = labelOf(judged);
            AttrLevel groupSide = "SPLIT".equals(label) ? null : judged.get(0);
            preferences.add(new PreferenceItemResponse(tag.getId(), tag.getName(), tag.getAxis().name(), label,
                    groupSide == null ? null : groupSide.name(), tag.labelOf(groupSide), judged.size(), memberViews));
        }
        preferences.sort((left, right) -> Integer.compare(right.judgedMemberCount(), left.judgedMemberCount()));
        return new GroupPreferencesResponse(groupId, preferences);
    }

    private String labelOf(List<AttrLevel> judged) {
        if (judged.size() == 1) {
            return "ONE_SIDED";
        }
        return judged.stream().distinct().count() == 1 ? "ALL_SAME" : "SPLIT";
    }

    /**
     * 한 번 쓴 말과 반복해서 쓰는 말을 가릅니다. 2회 미만이면 판정하지 않고 분모에서 뺍니다.
     */
    private AttrLevel sideOf(int wantHigh, int wantLow) {
        if (wantHigh >= JUDGE_THRESHOLD && wantHigh > wantLow) {
            return AttrLevel.HIGH;
        }
        if (wantLow >= JUDGE_THRESHOLD && wantLow > wantHigh) {
            return AttrLevel.LOW;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Map<Long, int[]>> wantCounts(List<Long> memberIds) {
        Map<Long, Map<Long, int[]>> counts = new LinkedHashMap<>();
        if (memberIds.isEmpty()) {
            return counts;
        }
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT tag_id, user_id, want_high_count, want_low_count
                FROM user_tags WHERE user_id IN (:memberIds)
                ORDER BY tag_id
                """)
                .setParameter("memberIds", memberIds)
                .getResultList();
        for (Object[] row : rows) {
            Long tagId = ((Number) row[0]).longValue();
            Long memberId = ((Number) row[1]).longValue();
            counts.computeIfAbsent(tagId, key -> new LinkedHashMap<>())
                    .put(memberId, new int[]{((Number) row[2]).intValue(), ((Number) row[3]).intValue()});
        }
        return counts;
    }
}
