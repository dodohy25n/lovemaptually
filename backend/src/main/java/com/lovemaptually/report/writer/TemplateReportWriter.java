package com.lovemaptually.report.writer;

import com.lovemaptually.report.service.ReportInput;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 개발과 테스트용 라이터입니다. 사용자에게 나가는 폴백이 아닙니다(D-39).
 * 유료 기능이라 실패는 FAILED로 남기고 템플릿을 완성본으로 주지 않습니다.
 */
public class TemplateReportWriter implements ReportWriter {

    @Override
    public ReportDraft write(ReportInput input) {
        List<Map<String, Object>> highlights = new ArrayList<>();
        input.places().stream().limit(2).forEach(place -> highlights.add(new LinkedHashMap<>(Map.of(
                "placeId", place.placeId(),
                "name", place.name(),
                "why", "%s에서 %d명이 평가했고 그중 %d명이 좋아했습니다."
                        .formatted(place.name(), place.reviewedCount(), place.likedCount())))));

        List<Map<String, Object>> tasteShift = new ArrayList<>();
        input.tagShifts().forEach(shift -> tasteShift.add(new LinkedHashMap<>(Map.of(
                "tag", shift.tag(),
                "direction", shift.direction(),
                "evidence", "이달 리뷰 %d건에서 %s 쪽을 원한다고 적었습니다.".formatted(shift.count(), shift.direction())))));

        List<Map<String, Object>> splitTags = new ArrayList<>();
        input.splitTags().forEach(split -> splitTags.add(new LinkedHashMap<>(Map.of(
                "tag", split.tag(), "memberA", split.memberA(), "memberB", split.memberB()))));

        List<Map<String, Object>> nextMonth = new ArrayList<>();
        input.candidates().stream().limit(1).forEach(candidate -> nextMonth.add(new LinkedHashMap<>(Map.of(
                "placeId", candidate.placeId(),
                "name", candidate.name(),
                "reason", "아직 안 가 본 곳이고 이달에 자주 나온 태그와 맞습니다."))));

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("title", "%s의 기록".formatted(input.month()));
        content.put("summary", "%s에는 %d곳을 함께 다녀왔고 지난달에는 %d곳이었습니다."
                .formatted(input.month(), input.places().size(), input.previousMonthVisitCount()));
        content.put("highlights", highlights);
        content.put("tasteShift", tasteShift);
        content.put("splitTags", splitTags);
        content.put("nextMonth", nextMonth);
        content.put("closingLine", "다음 달에도 기록이 쌓이면 더 또렷해집니다.");
        return new ReportDraft("TEMPLATE", null, null, content);
    }

    @Override
    public String name() {
        return "template";
    }
}
