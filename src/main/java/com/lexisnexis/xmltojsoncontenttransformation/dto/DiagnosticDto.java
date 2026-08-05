package com.lexisnexis.xmltojsoncontenttransformation.dto;

public record DiagnosticDto(String severity, int line, int column, String message) {
}
