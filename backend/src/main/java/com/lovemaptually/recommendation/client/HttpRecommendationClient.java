package com.lovemaptually.recommendation.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * FastAPI 추천 엔진 호출. 순위 계산이 행렬 연산이라 파이썬 프로세스로 분리했습니다(설계문서 9장).
 */
@Component
public class HttpRecommendationClient implements RecommendationClient {

    private final RestClient restClient;

    public HttpRecommendationClient(@Value("${app.recommender.base-url:http://localhost:8000}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public RecommendationResult recommend(RecommendationCommand command) {
        Map<String, Object> body = restClient.post()
                .uri("/recommendations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody(command))
                .retrieve()
                .body(Map.class);
        if (body == null) {
            throw new IllegalStateException("추천 엔진이 빈 응답을 돌려주었습니다");
        }
        List<RecommendationItem> items = new ArrayList<>();
        List<Map<String, Object>> raw = (List<Map<String, Object>>) body.getOrDefault("recommendations", List.of());
        for (Map<String, Object> item : raw) {
            items.add(new RecommendationItem(
                    ((Number) item.get("placeId")).longValue(),
                    (List<String>) item.getOrDefault("matchedTags", List.of()),
                    String.valueOf(item.getOrDefault("basis", "OTHERS")),
                    String.valueOf(item.getOrDefault("reason", "")),
                    ((Number) item.getOrDefault("displayOrder", items.size() + 1)).intValue()
            ));
        }
        return new RecommendationResult(
                ((Number) body.getOrDefault("candidateCount", 0)).intValue(),
                ((Number) body.getOrDefault("cfWeight", 0)).doubleValue(),
                false,
                (String) body.get("notice"),
                items);
    }

    private Map<String, Object> requestBody(RecommendationCommand command) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("groupId", command.groupId());
        body.put("memberIds", command.memberIds());
        body.put("region", command.region());
        body.put("count", command.count());
        body.put("budget", command.budget());
        return body;
    }

    @Override
    public String name() {
        return "fastapi";
    }
}
