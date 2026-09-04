package com.lovemaptually.ai;

/**
 * AI에게 넘기는 사전 한 줄. AI는 HIGH/LOW가 아니라 이 라벨로 답하고 서버가 코드로 되돌립니다(D-29).
 */
public record TagDefinition(String name, String highLabel, String lowLabel) {
}
