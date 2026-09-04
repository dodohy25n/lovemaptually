package com.lovemaptually.report.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 워커를 별도 빈으로 둡니다. 같은 클래스 안에서 부르면 프록시를 지나지 않아 트랜잭션이 걸리지 않습니다.
 */
@Component
public class ReportWorker {

    private static final Logger log = LoggerFactory.getLogger(ReportWorker.class);

    private final ReportService reportService;

    public ReportWorker(ReportService reportService) {
        this.reportService = reportService;
    }

    @Async("reportExecutor")
    public void process(Long reportId) {
        try {
            reportService.run(reportId);
        } catch (Exception exception) {
            log.error("리포트 생성에 실패했습니다 reportId={}", reportId, exception);
            reportService.markFailed(reportId, exception.getMessage());
        }
    }
}
