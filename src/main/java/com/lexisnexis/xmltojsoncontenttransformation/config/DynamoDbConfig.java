package com.lexisnexis.xmltojsoncontenttransformation.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
@ConditionalOnProperty(name = "app.registry.type", havingValue = "dynamodb")
public class DynamoDbConfig {

    // region and credentials come from the SDK default chain (env, instance profile, aws config)
    @Bean
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.create();
    }
}