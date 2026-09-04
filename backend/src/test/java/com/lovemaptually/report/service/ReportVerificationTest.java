package com.lovemaptually.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lovemaptually.report.service.ReportInput.Candidate;
import com.lovemaptually.report.service.ReportInput.VisitedPlace;
import com.lovemaptually.report.service.ReportService.Verified;
import com.lovemaptually.report.writer.TemplateReportWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 모델이 없는 장소를 지어내도 프롬프트가 아니라 서버가 막습니다.
 * verify는 입력 집합과 대조해 밖을 가리키는 항목을 버리고 meta.discarded에 개수를 남깁니다.
 * ReportService.verify와 Verified가 패키지 전용이라 같은 패키지에서 직접 부릅니다.
 */
class ReportVerificationTest {

    private static final long VISITED_PLACE_ID = 412L;
    private static final long CANDIDATE_PLACE_ID = 433L;

    private final ReportService reportService =
            new ReportService(null, null, null, null, null, new TemplateReportWriter(), null);

    private final ReportInput input = new ReportInput(
            "2026-08", 3, 2,
            List.of(new VisitedPlace(VISITED_PLACE_ID, "○○찻집", "카페", "ALL_LIKED", 2, 2, 1, List.of())),
            List.of(),
            List.of(),
            List.of(new Candidate(CANDIDATE_PLACE_ID, "□□카페", "카페", List.of("조용함"))),
            Map.of("A", "A"));

    @Test
    void serverDiscardsAHighlightPointingAtAPlaceTheGroupNeverVisited() {
        Map<String, Object> content = draft(
                List.of(highlight(VISITED_PLACE_ID, "○○찻집"), highlight(999L, "지어낸 가게")),
                List.of());

        Verified verified = reportService.verify(content, input);

        assertThat(verified.discarded()).isEqualTo(1);
        assertThat(highlights(verified)).hasSize(1);
        assertThat(highlights(verified).get(0).get("placeId")).isEqualTo(VISITED_PLACE_ID);
        assertThat(verified.content().get("meta")).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) verified.content().get("meta")).get("discarded")).isEqualTo(1);
    }

    @Test
    void serverDiscardsANextMonthEntryOutsideTheCandidateSet() {
        Map<String, Object> content = draft(
                List.of(),
                List.of(nextMonth(CANDIDATE_PLACE_ID, "□□카페"), nextMonth(777L, "지어낸 카페")));

        Verified verified = reportService.verify(content, input);

        assertThat(verified.discarded()).isEqualTo(1);
        assertThat(nextMonths(verified)).hasSize(1);
        assertThat(nextMonths(verified).get(0).get("placeId")).isEqualTo(CANDIDATE_PLACE_ID);
    }

    @Test
    void entriesInsideTheInputSurviveVerification() {
        Map<String, Object> content = draft(
                List.of(highlight(VISITED_PLACE_ID, "○○찻집")),
                List.of(nextMonth(CANDIDATE_PLACE_ID, "□□카페")));

        Verified verified = reportService.verify(content, input);

        assertThat(highlights(verified)).hasSize(1);
        assertThat(nextMonths(verified)).hasSize(1);
        assertThat(verified.content().get("title")).isEqualTo("제목");
        assertThat(verified.content().get("summary")).isEqualTo("요약");
    }

    @Test
    void discardedIsZeroWhenTheModelStaysInsideTheInput() {
        Map<String, Object> content = draft(
                List.of(highlight(VISITED_PLACE_ID, "○○찻집")),
                List.of(nextMonth(CANDIDATE_PLACE_ID, "□□카페")));

        Verified verified = reportService.verify(content, input);

        assertThat(verified.discarded()).isZero();
        assertThat(((Map<?, ?>) verified.content().get("meta")).get("discarded")).isEqualTo(0);
        assertThat(((Map<?, ?>) verified.content().get("meta")).get("model")).isEqualTo("template");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> highlights(Verified verified) {
        return (List<Map<String, Object>>) verified.content().get("highlights");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> nextMonths(Verified verified) {
        return (List<Map<String, Object>>) verified.content().get("nextMonth");
    }

    private Map<String, Object> draft(List<?> highlights, List<?> nextMonth) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("title", "제목");
        content.put("summary", "요약");
        content.put("highlights", highlights);
        content.put("tasteShift", List.of());
        content.put("splitTags", List.of());
        content.put("nextMonth", nextMonth);
        content.put("closingLine", "마무리");
        return content;
    }

    private Map<String, Object> highlight(long placeId, String name) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("placeId", placeId);
        item.put("name", name);
        item.put("why", "이유");
        return item;
    }

    private Map<String, Object> nextMonth(long placeId, String name) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("placeId", placeId);
        item.put("name", name);
        item.put("reason", "이유");
        return item;
    }
}
