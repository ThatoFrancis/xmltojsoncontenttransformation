package com.lexisnexis.xmltojsoncontenttransformation.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Pipeline pipeline = new Pipeline();
    private final Storage storage = new Storage();

    @Getter
    @Setter
    public static class Pipeline {
        private int concurrency = 4;
        private long maxDocumentSizeBytes = 10 * 1024 * 1024;
    }

    @Getter
    @Setter
    public static class Storage {
        private String outputDir = "./output";
    }
}
