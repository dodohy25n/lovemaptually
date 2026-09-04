package com.lovemaptually.report.writer;

import com.lovemaptually.report.service.ReportInput;

/**
 * AI-3이 들어오는 자리입니다. R2에서 프라이빗 모델 구현체로 바꿔도 검증 경로는 그대로입니다(D-37).
 */
public interface ReportWriter {

    ReportDraft write(ReportInput input);

    String name();
}
