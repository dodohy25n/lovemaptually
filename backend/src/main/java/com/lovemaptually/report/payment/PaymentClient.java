package com.lovemaptually.report.payment;

/**
 * 결제 자리입니다. 3일 범위 구현체는 즉시 승인하는 Mock이고 실제 PG 연동은 구현체 교체입니다(D-40).
 */
public interface PaymentClient {

    String approve(Long groupId, String plan);
}
