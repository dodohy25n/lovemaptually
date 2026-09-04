package com.lovemaptually.ai;

/**
 * AI-1이 돌려주는 한 태그. 경계에서는 사람 말(사전 라벨)이고 저장할 때 서버가 코드로 바꿉니다(D-29).
 * factLabel과 wantLabel은 각각 따로 판단하며 근거가 없으면 null입니다(D-10, D-27).
 */
public record TagCandidate(String tagName, String factLabel, String wantLabel, String evidence) {
}
