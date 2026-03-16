package com.artfetch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

    private final AppProperties appProperties;

    public AsyncConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Bean(name = "taskExecutor", destroyMethod = "shutdownNow")
    public ExecutorService taskExecutor() {
        return Executors.newFixedThreadPool(appProperties.getTask().getThreadPoolSize());
    }
}
