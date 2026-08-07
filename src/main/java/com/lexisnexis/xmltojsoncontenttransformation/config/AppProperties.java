package com.lexisnexis.xmltojsoncontenttransformation.config;

import com.lexisnexis.xmltojsoncontenttransformation.constant.ArtifactType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Pipeline pipeline = new Pipeline();
    private final Transform transform = new Transform();
    private final Storage storage = new Storage();
    private final Registry registry = new Registry();
    private final Security security = new Security();
    private final Metrics metrics = new Metrics();
    private final Docs docs = new Docs();

    @Getter
    @Setter
    public static class Pipeline {
        private int concurrency;
        private long maxDocumentSizeBytes;
        /** Collection (artifact sub-folder) that batch-ingested documents are filed under. */
        private String batchCollection;
    }

    @Getter
    @Setter
    public static class Transform {
        private String stylesheet;
        private String schema;
    }

    @Getter
    @Setter
    public static class Storage {
        private String type;
        private String outputDir;
        private String bucket;
        private String normalizedJsonFileName;
        private String fullTextFileName;

        public String fileNameFor(ArtifactType type) {
            return type == ArtifactType.NORMALIZED_JSON ? normalizedJsonFileName : fullTextFileName;
        }
    }

    @Getter
    @Setter
    public static class Registry {
        private String type;
        private String table;
    }

    @Getter
    @Setter
    public static class Security {
        /** When set, requests under the protected path prefix must carry this value in the configured header. */
        private String apiKey;
        private String apiKeyHeader;
        private String protectedPathPrefix;
    }

    @Getter
    @Setter
    public static class Metrics {
        private boolean cloudwatchEnabled;
        private String cloudwatchNamespace;
        private Duration cloudwatchStep;
    }

    @Getter
    @Setter
    public static class Docs {
        private String title;
        private String description;
        private String version;
        private String securitySchemeName;
    }
}
