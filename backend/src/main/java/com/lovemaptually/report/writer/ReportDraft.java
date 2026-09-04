package com.lovemaptually.report.writer;

import java.util.Map;

/**
 * 라이터가 돌려주는 초안입니다. 검증은 서비스가 하고 라이터는 문장만 책임집니다.
 */
public record ReportDraft(String model, Integer promptTokens, Integer completionTokens, Map<String, Object> content) {
}
