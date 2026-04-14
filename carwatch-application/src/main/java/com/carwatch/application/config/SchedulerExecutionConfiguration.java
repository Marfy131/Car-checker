package com.carwatch.application.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SchedulerExecutionConfiguration {

    @Bean(name = "scheduleExecutionExecutor", destroyMethod = "shutdown")
    ExecutorService scheduleExecutionExecutor(
        @Value("${carwatch.scheduler.execution-parallelism:2}") int executionParallelism
    ) {
        int normalizedParallelism = Math.max(1, executionParallelism);
        AtomicInteger threadCounter = new AtomicInteger();
        return Executors.newFixedThreadPool(normalizedParallelism, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("carwatch-schedule-" + threadCounter.incrementAndGet());
            return thread;
        });
    }
}
