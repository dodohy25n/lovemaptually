package com.lovemaptually.ai;

import java.util.List;

/**
 * AI-1이 들어올 자리입니다. 구현체를 바꿔도 리뷰 저장 경로와 검증은 그대로입니다.
 */
public interface AiClient {

    List<TagCandidate> extractTags(String content, List<TagDefinition> dictionary);

    String name();
}
