package com.lexisnexis.xmltojsoncontenttransformation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ExecutorConfig {

    // bounded worker pool; size comes from configuration so operators can tune
    // concurrency per environment without touching code
    @Bean(destroyMethod = "shutdown")
    public ExecutorService pipelineExecutor(AppProperties properties) {
        return Executors.newFixedThreadPool(properties.getPipeline().getConcurrency());
    }
}
