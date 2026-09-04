package com.lovemaptually.recommendation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 워커를 별도 빈으로 둡니다. 같은 클래스 안에서 부르면 프록시를 지나지 않아 트랜잭션이 걸리지 않습니다.
 */
@Component
public class RecommendationWorker {

    private static final Logger log = LoggerFactory.getLogger(RecommendationWorker.class);

    private final RecommendationService recommendationService;

    public RecommendationWorker(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @Async("recommendationExecutor")
    public void process(Long requestId) {
        try {
            recommendationService.run(requestId);
        } catch (Exception exception) {
            log.error("추천 처리에 실패했습니다 requestId={}", requestId, exception);
            recommendationService.markFailed(requestId);
        }
    }
}
