package com.lovemaptually.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovemaptually.report.writer.OpenAiReportWriter;
import com.lovemaptually.report.writer.ReportWriter;
import com.lovemaptually.report.writer.TemplateReportWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 모델명과 온도와 토큰 상한은 코드가 아니라 설정입니다(D-41). 키가 없으면 템플릿 라이터로 내려갑니다.
 */
@Configuration
public class ReportConfig {

    @Bean
    ReportWriter reportWriter(
            ObjectMapper objectMapper,
            @Value("${app.report.writer:template}") String writer,
            @Value("${app.report.openai.api-key:}") String apiKey,
            @Value("${app.report.openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${app.report.openai.model:gpt-4o-mini}") String model,
            @Value("${app.report.openai.temperature:0.3}") double temperature,
            @Value("${app.report.openai.max-tokens:1500}") int maxTokens,
            @Value("${app.report.openai.timeout-seconds:30}") int timeoutSeconds
    ) {
        if ("openai".equals(writer) && !apiKey.isBlank()) {
            return new OpenAiReportWriter(objectMapper, apiKey, baseUrl, model, temperature, maxTokens, timeoutSeconds);
        }
        return new TemplateReportWriter();
    }
}
