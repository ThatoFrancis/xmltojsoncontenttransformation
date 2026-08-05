package com.lexisnexis.xmltojsoncontenttransformation.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.util.Optional;

/**
 * Pulls the content_id out of a document with StAX so we can key rejected
 * documents too, without building a full DOM.
 */
@Slf4j
@Service
public class ContentIdExtractor {

    private final XMLInputFactory inputFactory;

    public ContentIdExtractor() {
        this.inputFactory = XMLInputFactory.newFactory();
        inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    }

    public Optional<String> extract(String xml) {
        try {
            XMLStreamReader reader = inputFactory.createXMLStreamReader(new StringReader(xml));
            try {
                while (reader.hasNext()) {
                    if (reader.next() == XMLStreamConstants.START_ELEMENT
                            && "content_id".equals(reader.getLocalName())) {
                        return Optional.ofNullable(reader.getElementText()).map(String::trim);
                    }
                }
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            log.debug("Could not extract content_id: {}", e.getMessage());
        }
        return Optional.empty();
    }
}
