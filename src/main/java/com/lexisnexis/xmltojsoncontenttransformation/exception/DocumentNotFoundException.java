package com.lexisnexis.xmltojsoncontenttransformation.exception;

public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(String contentId) {
        super("No processed document found for content id: " + contentId);
    }
}
