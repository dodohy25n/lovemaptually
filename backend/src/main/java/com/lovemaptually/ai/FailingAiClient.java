package com.lovemaptually.ai;

import java.util.List;

/**
 * AI가 죽어도 리뷰는 저장된다는 것을 시연하기 위한 구현입니다. app.ai.client=failing 일 때 쓰입니다.
 */
public class FailingAiClient implements AiClient {

    @Override
    public List<TagCandidate> extractTags(String content, List<TagDefinition> dictionary) {
        throw new AiExtractionException("태그 추출기가 응답하지 않습니다");
    }

    @Override
    public String name() {
        return "failing";
    }
}
