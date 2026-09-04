package com.lovemaptually.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;

/**
 * 자주 나오는 표현을 정규식 매칭표로 처리하는 AI-1 구현입니다(흐름 1 STEP 2 분기 A).
 * 호출이 없고 결정적이라 데모가 외부 서비스에 매달리지 않습니다.
 *
 * fact와 want를 따로 판정합니다. fact는 표현이 가리키는 축의 방향이고,
 * want는 그 표현 뒤에 붙은 감정 신호로만 정합니다. 신호가 없으면 want는 null입니다(D-27).
 */
public class MatchingTableAiClient implements AiClient {

    private static final int MAX_TAGS_PER_REVIEW = 5;
    private static final int CUE_WINDOW = 25;

    private final List<TagRule> rules;
    private final List<String> positiveCues;
    private final List<String> negativeCues;

    public MatchingTableAiClient(ObjectMapper objectMapper) {
        try (InputStream stream = new ClassPathResource("ai/matching-table.json").getInputStream()) {
            JsonNode root = objectMapper.readTree(stream);
            this.positiveCues = texts(root.path("positiveCues"));
            this.negativeCues = texts(root.path("negativeCues"));
            List<TagRule> parsed = new ArrayList<>();
            for (JsonNode tag : root.path("tags")) {
                parsed.add(new TagRule(
                        tag.path("name").asText(),
                        compile(tag.path("high")),
                        compile(tag.path("low"))
                ));
            }
            this.rules = List.copyOf(parsed);
        } catch (IOException exception) {
            throw new IllegalStateException("매칭표를 읽지 못했습니다", exception);
        }
    }

    @Override
    public List<TagCandidate> extractTags(String content, List<TagDefinition> dictionary) {
        Map<String, TagDefinition> byName = new LinkedHashMap<>();
        dictionary.forEach(definition -> byName.put(definition.name(), definition));
        Map<String, TagCandidate> found = new LinkedHashMap<>();
        for (TagRule rule : rules) {
            TagDefinition definition = byName.get(rule.name());
            if (definition == null) {
                continue;
            }
            Hit high = firstHit(content, rule.high(), "HIGH");
            Hit low = firstHit(content, rule.low(), "LOW");
            Hit hit = pick(high, low);
            if (hit == null) {
                continue;
            }
            String factLabel = labelOf(definition, hit.level());
            String wantLabel = wantOf(content, hit, definition);
            found.put(rule.name(), new TagCandidate(rule.name(), factLabel, wantLabel, hit.evidence()));
            if (found.size() >= MAX_TAGS_PER_REVIEW) {
                break;
            }
        }
        return List.copyOf(found.values());
    }

    @Override
    public String name() {
        return "matching-table";
    }

    /**
     * 표현 뒤에 붙은 감정 신호로 want를 정합니다.
     * 만족이면 가게가 있는 쪽을 원하는 것이고, 불만이면 반대쪽을 원하는 것입니다.
     */
    private String labelOf(TagDefinition definition, String level) {
        return "HIGH".equals(level) ? definition.highLabel() : definition.lowLabel();
    }

    private String wantOf(String content, Hit hit, TagDefinition definition) {
        int to = Math.min(content.length(), hit.end() + CUE_WINDOW);
        String window = content.substring(hit.start(), to);
        int positiveAt = firstIndexOf(window, positiveCues);
        int negativeAt = firstIndexOf(window, negativeCues);
        if (positiveAt < 0 && negativeAt < 0) {
            return null;
        }
        boolean positive = negativeAt < 0 || (positiveAt >= 0 && positiveAt < negativeAt);
        boolean factIsHigh = "HIGH".equals(hit.level());
        boolean wantHigh = positive == factIsHigh;
        return wantHigh ? definition.highLabel() : definition.lowLabel();
    }

    /**
     * 표현에 가장 가까운 감정 신호 하나만 봅니다. 한 문장에 만족과 불만이 함께 나오면 앞선 쪽이 그 태그의 것입니다.
     */
    private int firstIndexOf(String window, List<String> cues) {
        int best = -1;
        for (String cue : cues) {
            int at = window.indexOf(cue);
            if (at >= 0 && (best < 0 || at < best)) {
                best = at;
            }
        }
        return best;
    }

    private Hit pick(Hit high, Hit low) {
        if (high == null) {
            return low;
        }
        if (low == null) {
            return high;
        }
        return low.start() <= high.start() ? low : high;
    }

    private Hit firstHit(String content, List<Pattern> patterns, String level) {
        Hit best = null;
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(content);
            if (matcher.find() && (best == null || matcher.start() < best.start())) {
                best = new Hit(matcher.start(), matcher.end(), matcher.group(), level);
            }
        }
        return best;
    }

    private List<Pattern> compile(JsonNode node) {
        List<Pattern> patterns = new ArrayList<>();
        for (JsonNode expression : node) {
            patterns.add(Pattern.compile(expression.asText()));
        }
        return List.copyOf(patterns);
    }

    private List<String> texts(JsonNode node) {
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            values.add(item.asText());
        }
        return List.copyOf(values);
    }

    private record TagRule(String name, List<Pattern> high, List<Pattern> low) {
    }

    private record Hit(int start, int end, String evidence, String level) {
    }
}
