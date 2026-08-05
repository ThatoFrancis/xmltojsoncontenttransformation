package com.lexisnexis.xmltojsoncontenttransformation.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentIdExtractorTest {

    private final ContentIdExtractor extractor = new ContentIdExtractor();

    @Test
    void extractsContentIdFromNamespacedDocument() {
        String xml = """
                <judgment xmlns="urn:lex:content:1">
                  <header><content_id>FR-2024-CA-000123</content_id></header>
                </judgment>
                """;

        assertThat(extractor.extract(xml)).contains("FR-2024-CA-000123");
    }

    @Test
    void trimsSurroundingWhitespace() {
        String xml = "<judgment><header><content_id>  ABC-1  </content_id></header></judgment>";

        assertThat(extractor.extract(xml)).contains("ABC-1");
    }

    @Test
    void emptyWhenElementIsMissing() {
        String xml = "<judgment><header><title>no id here</title></header></judgment>";

        assertThat(extractor.extract(xml)).isEmpty();
    }

    @Test
    void emptyForMalformedXml() {
        assertThat(extractor.extract("<judgment><content_id>oops")).isEmpty();
    }
}
