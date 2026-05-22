package com.hify.modules.chat.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class ThreadPoolConfig {

    @Bean("llmStreamExecutor")
    public ThreadPoolExecutor llmStreamExecutor() {
        return new ThreadPoolExecutor(
                30, 80,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(50),
                r -> {
                    Thread t = new Thread(r, "llm-stream-" + counter.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    private final AtomicInteger counter = new AtomicInteger(0);
}
