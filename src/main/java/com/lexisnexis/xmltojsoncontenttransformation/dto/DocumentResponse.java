package com.lexisnexis.xmltojsoncontenttransformation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lexisnexis.xmltojsoncontenttransformation.constant.ProcessingStatus;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocumentResponse(
        String contentId,
        ProcessingStatus status,
        String contentHash,
        Instant receivedAt,
        Instant publishedAt,
        boolean duplicate,
        List<DiagnosticDto> diagnostics) {
}
