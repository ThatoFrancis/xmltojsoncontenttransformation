package com.lexisnexis.xmltojsoncontenttransformation.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiInfo() {
        return new OpenAPI().info(new Info()
                .title("XML-to-JSON Content Transformation Service")
                .description("Ingests legal XML judgments, validates against XSD, transforms to normalized JSON via XSLT 3.0 (Saxon-HE) and publishes artifacts for downstream search/RAG.")
                .version("1.0"));
    }
}
