package com.lovemaptually.ai;

/**
 * AI-1이 돌려주는 한 태그. 경계에서는 사람 말(사전 라벨)이고 저장할 때 서버가 코드로 바꿉니다(D-29).
 * factLabel과 wantLabel은 각각 따로 판단하며 근거가 없으면 null입니다(D-10, D-27).
 * source는 매칭표에서 나왔는지 LLM에서 나왔는지를 적어 두는 자리이며 API 응답에는 나가지 않습니다.
 */
public record TagCandidate(String tagName, String factLabel, String wantLabel, String evidence, String source) {

    public static final String SOURCE_MATCHING = "matching";
    public static final String SOURCE_LLM = "llm";
}
