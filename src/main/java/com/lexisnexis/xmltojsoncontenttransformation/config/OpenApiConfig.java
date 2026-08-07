package com.lexisnexis.xmltojsoncontenttransformation.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiInfo(AppProperties properties) {
        AppProperties.Security security = properties.getSecurity();
        AppProperties.Docs docs = properties.getDocs();
        return new OpenAPI()
                .info(new Info()
                        .title(docs.getTitle())
                        .description(docs.getDescription())
                        .version(docs.getVersion()))
                .components(new Components().addSecuritySchemes(docs.getSecuritySchemeName(),
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(security.getApiKeyHeader())
                                .description("Required for " + security.getProtectedPathPrefix()
                                        + "** when the service runs with an API key configured")))
                .addSecurityItem(new SecurityRequirement().addList(docs.getSecuritySchemeName()));
    }
}
