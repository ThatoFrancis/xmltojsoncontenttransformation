package com.lexisnexis.xmltojsoncontenttransformation.exception;

public class DocumentTooLargeException extends RuntimeException {

    public DocumentTooLargeException(long size, long limit) {
        super("Document size " + size + " bytes exceeds the configured limit of " + limit + " bytes");
    }
}
