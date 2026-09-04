package com.lovemaptually.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovemaptually.ai.AiClient;
import com.lovemaptually.ai.FailingAiClient;
import com.lovemaptually.ai.MatchingTableAiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    AiClient matchingTableAiClient(ObjectMapper objectMapper) {
        return new MatchingTableAiClient(objectMapper);
    }

    @Bean
    AiClient failingAiClient() {
        return new FailingAiClient();
    }

    /**
     * 데모에서 재기동 없이 AI를 끄기 위해 두 구현을 모두 올려 두고 이름으로 고릅니다.
     */
    @Bean
    AiClientSelector aiClientSelector(
            AiClient matchingTableAiClient,
            AiClient failingAiClient,
            @Value("${app.ai.client:matching}") String configured
    ) {
        return new AiClientSelector(matchingTableAiClient, failingAiClient, configured);
    }

    public static class AiClientSelector {

        private final AiClient matching;
        private final AiClient failing;
        private volatile String selected;

        public AiClientSelector(AiClient matching, AiClient failing, String selected) {
            this.matching = matching;
            this.failing = failing;
            this.selected = selected;
        }

        public AiClient current() {
            return "failing".equals(selected) ? failing : matching;
        }

        public String selected() {
            return selected;
        }

        public void select(String value) {
            this.selected = "failing".equals(value) ? "failing" : "matching";
        }
    }
}
