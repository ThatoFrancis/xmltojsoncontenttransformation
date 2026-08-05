package com.lexisnexis.xmltojsoncontenttransformation.entity;

import com.lexisnexis.xmltojsoncontenttransformation.constant.ProcessingStatus;
import com.lexisnexis.xmltojsoncontenttransformation.dto.DiagnosticDto;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class ProcessingRecord {

    private String contentId;
    private ProcessingStatus status;
    private String contentHash;
    private Instant receivedAt;
    private Instant publishedAt;
    private List<DiagnosticDto> diagnostics;
}
