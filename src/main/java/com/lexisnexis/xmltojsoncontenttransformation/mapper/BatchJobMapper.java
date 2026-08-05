package com.lexisnexis.xmltojsoncontenttransformation.mapper;

import com.lexisnexis.xmltojsoncontenttransformation.dto.BatchResponse;
import com.lexisnexis.xmltojsoncontenttransformation.entity.BatchJob;
import org.springframework.stereotype.Component;

@Component
public class BatchJobMapper {

    public BatchResponse toResponse(BatchJob job) {
        return new BatchResponse(
                job.getBatchId(),
                job.getStatus(),
                job.getInputDir(),
                job.getTotalFiles(),
                job.getPublished().get(),
                job.getRejected().get(),
                job.getDuplicates().get(),
                job.getFailed().get(),
                job.getSubmittedAt(),
                job.getCompletedAt());
    }
}
