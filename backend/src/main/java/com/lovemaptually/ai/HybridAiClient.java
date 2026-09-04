package com.lovemaptually.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 흐름 1 STEP 2를 설계대로 잇습니다. 매칭표가 자주 나오는 표현을 호출 없이 처리하고,
 * 거기서 남은 조각만 LLM으로 넘깁니다.
 *
 * 매칭표가 먼저이고 충돌하면 매칭표가 이깁니다. 매칭표는 같은 문장에 같은 답을 주는 결정적 경로라
 * 데모와 재현에서 기준점이 되어야 하기 때문입니다.
 */
public class HybridAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(HybridAiClient.class);

    private static final int MAX_TAGS_PER_REVIEW = 5;
    private static final int MIN_LEFTOVER_KOREAN = 6;

    private final AiClient matching;
    private final AiClient llm;

    public HybridAiClient(AiClient matching, AiClient llm) {
        this.matching = matching;
        this.llm = llm;
    }

    @Override
    public List<TagCandidate> extractTags(String content, List<TagDefinition> dictionary) {
        List<TagCandidate> matched = matching.extractTags(content, dictionary);
        if (matched.size() >= MAX_TAGS_PER_REVIEW) {
            return matched;
        }

        String leftover = leftoverOf(content, matched);
        if (koreanLength(leftover) < MIN_LEFTOVER_KOREAN) {
            return matched;
        }

        Set<String> settled = new LinkedHashSet<>();
        matched.forEach(candidate -> settled.add(candidate.tagName()));
        List<TagDefinition> remaining = dictionary.stream()
                .filter(definition -> !settled.contains(definition.name()))
                .toList();
        if (remaining.isEmpty()) {
            return matched;
        }

        List<TagCandidate> llmTags;
        try {
            llmTags = llm.extractTags(leftover, remaining);
        } catch (RuntimeException exception) {
            // 부분 답이 무응답보다 낫습니다. 매칭표 결과는 이미 나왔으므로 여기서 실패를 올리지 않습니다.
            log.warn("LLM 태그 추출에 실패해 매칭표 결과만 씁니다 reason={}", exception.getMessage());
            return matched;
        }
        return merge(matched, llmTags);
    }

    @Override
    public String name() {
        return "hybrid";
    }

    /**
     * 매칭표가 이미 근거로 쓴 조각을 지운 나머지가 LLM에게 물어볼 거리입니다.
     * 남은 글자가 거의 없으면 물어볼 것이 없다는 뜻이라 호출 자체를 건너뜁니다.
     */
    private String leftoverOf(String content, List<TagCandidate> matched) {
        String leftover = content == null ? "" : content;
        for (TagCandidate candidate : matched) {
            String evidence = candidate.evidence();
            if (evidence == null || evidence.isEmpty()) {
                continue;
            }
            int at = leftover.indexOf(evidence);
            if (at >= 0) {
                leftover = leftover.substring(0, at) + " " + leftover.substring(at + evidence.length());
            }
        }
        return leftover;
    }

    private int koreanLength(String text) {
        int count = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character >= '가' && character <= '힣') {
                count++;
            }
        }
        return count;
    }

    private List<TagCandidate> merge(List<TagCandidate> matched, List<TagCandidate> llmTags) {
        Map<String, TagCandidate> merged = new LinkedHashMap<>();
        for (TagCandidate candidate : matched) {
            merged.put(candidate.tagName(), candidate);
        }
        for (TagCandidate candidate : llmTags) {
            if (merged.size() >= MAX_TAGS_PER_REVIEW) {
                break;
            }
            merged.putIfAbsent(candidate.tagName(), candidate);
        }
        return List.copyOf(new ArrayList<>(merged.values()));
    }
}
