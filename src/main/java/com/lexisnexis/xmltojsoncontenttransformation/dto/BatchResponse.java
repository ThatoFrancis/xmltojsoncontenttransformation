package com.lexisnexis.xmltojsoncontenttransformation.dto;

import com.lexisnexis.xmltojsoncontenttransformation.constant.BatchStatus;

import java.time.Instant;

public record BatchResponse(
        String batchId,
        BatchStatus status,
        String inputDir,
        int totalFiles,
        int published,
        int rejected,
        int duplicates,
        int failed,
        Instant submittedAt,
        Instant completedAt) {
}
