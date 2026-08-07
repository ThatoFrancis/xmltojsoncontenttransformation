package com.lexisnexis.xmltojsoncontenttransformation.config;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.IOException;

@Configuration
public class XmlConfig {

    @Bean
    public Processor saxonProcessor() {
        return new Processor(false);
    }

    // compiled once at startup, XsltExecutable is thread-safe and reusable
    @Bean
    public XsltExecutable judgmentToJsonStylesheet(Processor saxonProcessor, AppProperties properties)
            throws SaxonApiException, IOException {
        XsltCompiler compiler = saxonProcessor.newXsltCompiler();
        return compiler.compile(new StreamSource(
                new ClassPathResource(properties.getTransform().getStylesheet()).getInputStream()));
    }

    @Bean
    public Schema judgmentSchema(AppProperties properties) throws SAXException, IOException {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newSchema(new StreamSource(
                new ClassPathResource(properties.getTransform().getSchema()).getInputStream()));
    }
}
