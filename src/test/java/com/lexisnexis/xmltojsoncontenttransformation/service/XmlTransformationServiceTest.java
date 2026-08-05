package com.lexisnexis.xmltojsoncontenttransformation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexisnexis.xmltojsoncontenttransformation.config.XmlConfig;
import net.sf.saxon.s9api.Processor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class XmlTransformationServiceTest {

    private static XmlTransformationService service;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void setUp() throws Exception {
        XmlConfig config = new XmlConfig();
        Processor processor = config.saxonProcessor();
        service = new XmlTransformationService(processor, config.judgmentToJsonStylesheet(processor), objectMapper);
    }

    @Test
    void producesNormalizedJsonMatchingTargetShape() throws IOException {
        var result = service.transform(readResource("/valid-judgment.xml"));
        JsonNode json = objectMapper.readTree(result.normalizedJson());

        assertThat(json.get("content_id").asText()).isEqualTo("FR-2024-CA-000123");
        assertThat(json.get("court").asText()).isEqualTo("Cour d'appel de Paris");
        assertThat(json.get("jurisdiction").asText()).isEqualTo("FR");
        assertThat(json.get("decision_date").asText()).isEqualTo("2024-03-12");
        assertThat(json.get("citations")).hasSize(2);
        assertThat(json.get("citations").get(0).get("type").asText()).isEqualTo("ECLI");
        assertThat(json.get("parties")).hasSize(2);
        assertThat(json.get("paragraphs")).hasSize(4);
        assertThat(json.get("paragraphs").get(0).get("section").asText()).isEqualTo("facts");
        assertThat(json.get("paragraphs").get(0).get("id").asText()).isEqualTo("p1");
    }

    @Test
    void fullTextConcatenatesParagraphsInDocumentOrder() throws IOException {
        var result = service.transform(readResource("/valid-judgment.xml"));

        assertThat(result.fullText())
                .isEqualTo("Le litige porte sur... Considérant que... Attendu que... Par ces motifs...");
    }

    private String readResource(String name) throws IOException {
        return new String(getClass().getResourceAsStream(name).readAllBytes(), StandardCharsets.UTF_8);
    }
}
