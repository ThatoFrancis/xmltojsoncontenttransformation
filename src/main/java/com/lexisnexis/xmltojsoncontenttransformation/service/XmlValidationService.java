package com.lexisnexis.xmltojsoncontenttransformation.service;

import com.lexisnexis.xmltojsoncontenttransformation.dto.DiagnosticDto;
import org.springframework.stereotype.Service;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

@Service
public class XmlValidationService {

    private final Schema judgmentSchema;

    public XmlValidationService(Schema judgmentSchema) {
        this.judgmentSchema = judgmentSchema;
    }

    /**
     * Validates the document and collects every diagnostic instead of failing fast,
     * so a rejected document reports all its problems in one pass.
     */
    public List<DiagnosticDto> validate(String xml) {
        List<DiagnosticDto> diagnostics = new ArrayList<>();
        Validator validator = judgmentSchema.newValidator();
        try {
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            validator.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException e) {
                    diagnostics.add(toDiagnostic("WARNING", e));
                }

                @Override
                public void error(SAXParseException e) {
                    diagnostics.add(toDiagnostic("ERROR", e));
                }

                @Override
                public void fatalError(SAXParseException e) {
                    diagnostics.add(toDiagnostic("FATAL", e));
                }
            });
            validator.validate(new StreamSource(new StringReader(xml)));
        } catch (SAXException | IOException e) {
            // fatal parse errors abort validation; make sure they are captured
            if (diagnostics.isEmpty()) {
                diagnostics.add(new DiagnosticDto("FATAL", -1, -1, e.getMessage()));
            }
        }
        return diagnostics;
    }

    private DiagnosticDto toDiagnostic(String severity, SAXParseException e) {
        return new DiagnosticDto(severity, e.getLineNumber(), e.getColumnNumber(), e.getMessage());
    }
}
