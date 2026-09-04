package com.lovemaptually.recommendation.client;

/**
 * 순위를 정하는 자리입니다. 기본 구현은 FastAPI 추천 엔진이고, 엔진이 응답하지 않으면 규칙 폴백이 받습니다.
 */
public interface RecommendationClient {

    RecommendationResult recommend(RecommendationCommand command);

    String name();
}
