package com.lexisnexis.xmltojsoncontenttransformation.constant;

public enum ArtifactType {
    NORMALIZED_JSON("normalized.json"),
    FULL_TEXT("fulltext.txt");

    private final String fileName;

    ArtifactType(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }
}
