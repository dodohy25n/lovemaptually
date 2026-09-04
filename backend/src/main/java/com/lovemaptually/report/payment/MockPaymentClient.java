package com.lovemaptually.report.payment;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class MockPaymentClient implements PaymentClient {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AtomicLong sequence = new AtomicLong();

    @Override
    public String approve(Long groupId, String plan) {
        return "MOCK-%s-%06d".formatted(LocalDate.now().format(DAY), sequence.incrementAndGet());
    }
}
