package com.lexisnexis.xmltojsoncontenttransformation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexisnexis.xmltojsoncontenttransformation.exception.TransformationException;
import lombok.RequiredArgsConstructor;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.XsltTransformer;
import org.springframework.stereotype.Service;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;

@Service
@RequiredArgsConstructor
public class XmlTransformationService {

    private final Processor saxonProcessor;
    private final XsltExecutable judgmentToJsonStylesheet;
    private final ObjectMapper objectMapper;

    public record TransformationResult(String normalizedJson, String fullText) {
    }

    public TransformationResult transform(String xml) {
        try {
            XsltTransformer transformer = judgmentToJsonStylesheet.load();
            StringWriter writer = new StringWriter();
            Serializer serializer = saxonProcessor.newSerializer(writer);
            transformer.setSource(new StreamSource(new StringReader(xml)));
            transformer.setDestination(serializer);
            transformer.transform();

            String json = writer.toString();
            JsonNode root = objectMapper.readTree(json);
            String fullText = root.path("full_text").asText("");
            return new TransformationResult(json, fullText);
        } catch (Exception e) {
            throw new TransformationException("XSLT transformation failed: " + e.getMessage(), e);
        }
    }
}
