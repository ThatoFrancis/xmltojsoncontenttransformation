package com.lexisnexis.xmltojsoncontenttransformation.service;

import com.lexisnexis.xmltojsoncontenttransformation.config.XmlConfig;
import com.lexisnexis.xmltojsoncontenttransformation.dto.DiagnosticDto;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class XmlValidationServiceTest {

    private static XmlValidationService service;

    @BeforeAll
    static void setUp() throws Exception {
        service = new XmlValidationService(new XmlConfig().judgmentSchema());
    }

    @Test
    void validDocumentProducesNoDiagnostics() throws IOException {
        List<DiagnosticDto> diagnostics = service.validate(readResource("/valid-judgment.xml"));

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void invalidDocumentCollectsAllErrors() throws IOException {
        List<DiagnosticDto> diagnostics = service.validate(readResource("/invalid-judgment.xml"));

        assertThat(diagnostics).hasSizeGreaterThanOrEqualTo(2);
        assertThat(diagnostics).anyMatch(d -> d.message().contains("decision_date"));
        assertThat(diagnostics).anyMatch(d -> d.message().contains("'id' must appear"));
        assertThat(diagnostics).allMatch(d -> d.line() > 0);
    }

    @Test
    void malformedXmlIsCapturedAsFatal() {
        List<DiagnosticDto> diagnostics = service.validate("<judgment>not even closed");

        assertThat(diagnostics).isNotEmpty();
        assertThat(diagnostics).anyMatch(d -> "FATAL".equals(d.severity()));
    }

    @Test
    void wrongNamespaceIsRejected() {
        String xml = "<judgment xmlns=\"urn:wrong:ns\"><header/></judgment>";

        assertThat(service.validate(xml)).isNotEmpty();
    }

    private String readResource(String name) throws IOException {
        return new String(getClass().getResourceAsStream(name).readAllBytes(), StandardCharsets.UTF_8);
    }
}
