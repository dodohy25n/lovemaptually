package com.lovemaptually.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovemaptually.ai.AiClient;
import com.lovemaptually.ai.FailingAiClient;
import com.lovemaptually.ai.HybridAiClient;
import com.lovemaptually.ai.MatchingTableAiClient;
import com.lovemaptually.ai.OpenAiTagExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 모델명과 온도와 토큰 상한은 코드가 아니라 설정입니다(D-41).
 * 기본값은 matching이라 테스트와 CI는 외부 호출을 하지 않습니다.
 */
@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Bean
    AiClient matchingTableAiClient(ObjectMapper objectMapper) {
        return new MatchingTableAiClient(objectMapper);
    }

    @Bean
    AiClient failingAiClient() {
        return new FailingAiClient();
    }

    /**
     * 키가 없으면 LLM 가지를 달지 않고 매칭표만 남깁니다. 키 없이 hybrid를 고른 것은
     * 설정 실수일 가능성이 커서 기동 시점에 경고로 알립니다.
     */
    @Bean
    AiClient hybridAiClient(
            ObjectMapper objectMapper,
            AiClient matchingTableAiClient,
            @Value("${app.ai.openai.api-key:}") String apiKey,
            @Value("${app.ai.openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${app.ai.openai.model:gpt-4o-mini}") String model,
            @Value("${app.ai.openai.temperature:0.0}") double temperature,
            @Value("${app.ai.openai.max-tokens:800}") int maxTokens,
            @Value("${app.ai.openai.timeout-seconds:20}") int timeoutSeconds,
            @Value("${app.ai.client:matching}") String configured
    ) {
        if (apiKey.isBlank()) {
            if ("hybrid".equals(configured)) {
                log.warn("app.ai.client=hybrid 인데 app.ai.openai.api-key 가 비어 매칭표만 씁니다");
            }
            return matchingTableAiClient;
        }
        return new HybridAiClient(matchingTableAiClient, new OpenAiTagExtractor(
                objectMapper, apiKey, baseUrl, model, temperature, maxTokens, timeoutSeconds));
    }

    /**
     * 데모에서 재기동 없이 AI를 끄기 위해 구현을 모두 올려 두고 이름으로 고릅니다.
     */
    @Bean
    AiClientSelector aiClientSelector(
            AiClient matchingTableAiClient,
            AiClient hybridAiClient,
            AiClient failingAiClient,
            @Value("${app.ai.client:matching}") String configured
    ) {
        return new AiClientSelector(matchingTableAiClient, hybridAiClient, failingAiClient, configured);
    }

    public static class AiClientSelector {

        private final AiClient matching;
        private final AiClient hybrid;
        private final AiClient failing;
        private volatile String selected;

        public AiClientSelector(AiClient matching, AiClient hybrid, AiClient failing, String selected) {
            this.matching = matching;
            this.hybrid = hybrid;
            this.failing = failing;
            this.selected = normalize(selected);
        }

        public AiClient current() {
            return switch (selected) {
                case "failing" -> failing;
                case "hybrid" -> hybrid;
                default -> matching;
            };
        }

        public String selected() {
            return selected;
        }

        public void select(String value) {
            this.selected = normalize(value);
        }

        private static String normalize(String value) {
            if ("failing".equals(value) || "hybrid".equals(value)) {
                return value;
            }
            return "matching";
        }
    }
}
