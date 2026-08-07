package com.lexisnexis.xmltojsoncontenttransformation.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3Config {

    // region and credentials come from the SDK default chain (env, instance profile, aws config)
    @Bean
    public S3Client s3Client() {
        return S3Client.create();
    }
}
