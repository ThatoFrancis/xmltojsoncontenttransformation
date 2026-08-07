package com.lexisnexis.xmltojsoncontenttransformation.config;

import io.micrometer.cloudwatch2.CloudWatchConfig;
import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;

import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "app.metrics.cloudwatch-enabled", havingValue = "true")
public class CloudWatchMetricsConfig {

    @Bean
    public CloudWatchMeterRegistry cloudWatchMeterRegistry(AppProperties properties) {
        Map<String, String> settings = Map.of(
                "cloudwatch.namespace", properties.getMetrics().getCloudwatchNamespace(),
                "cloudwatch.step", properties.getMetrics().getCloudwatchStep().toString());
        CloudWatchConfig config = key -> settings.get(key);

        CloudWatchMeterRegistry registry =
                new CloudWatchMeterRegistry(config, Clock.SYSTEM, CloudWatchAsyncClient.create());
        // publish only the pipeline counters to stay within the free custom-metric quota
        registry.config().meterFilter(MeterFilter.denyUnless(
                id -> id.getType() == Meter.Type.COUNTER && id.getName().startsWith("documents.")));
        return registry;
    }
}
