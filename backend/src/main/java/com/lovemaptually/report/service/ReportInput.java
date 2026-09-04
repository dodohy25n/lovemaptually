package com.lovemaptually.report.service;

import java.util.List;
import java.util.Map;

/**
 * LLM에 넘기는 집계 결과입니다. 숫자는 전부 SQL이 셌고 LLM은 문장만 씁니다(D-34).
 * 구성원은 A와 B로 익명화하고 좌표와 주소는 넣지 않습니다(D-37).
 */
public record ReportInput(
        String month,
        int visitCount,
        int previousMonthVisitCount,
        List<VisitedPlace> places,
        List<TagShift> tagShifts,
        List<SplitTag> splitTags,
        List<Candidate> candidates,
        Map<String, String> memberAliases
) {

    public record VisitedPlace(
            Long placeId,
            String name,
            String category,
            String label,
            int reviewedCount,
            int likedCount,
            int visitCount,
            List<MemberReview> reviews
    ) {
    }

    public record MemberReview(String member, int rating, String content, List<String> tags) {
    }

    public record TagShift(String tag, String direction, int count) {
    }

    public record SplitTag(String tag, String memberA, String memberB) {
    }

    public record Candidate(Long placeId, String name, String category, List<String> tags) {
    }
}
