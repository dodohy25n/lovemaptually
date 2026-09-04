package com.lovemaptually.global.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean
    Executor recommendationExecutor() {
        return executor("rec-", 2);
    }

    @Bean
    Executor reportExecutor() {
        return executor("report-", 2);
    }

    private Executor executor(String prefix, int size) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(size);
        executor.setMaxPoolSize(size);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix(prefix);
        executor.initialize();
        return executor;
    }
}
