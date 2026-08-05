package com.lexisnexis.xmltojsoncontenttransformation.mapper;

import com.lexisnexis.xmltojsoncontenttransformation.dto.DocumentResponse;
import com.lexisnexis.xmltojsoncontenttransformation.entity.ProcessingRecord;
import org.springframework.stereotype.Component;

@Component
public class ProcessingRecordMapper {

    public DocumentResponse toResponse(ProcessingRecord record, boolean duplicate) {
        return new DocumentResponse(
                record.getContentId(),
                record.getStatus(),
                record.getContentHash(),
                record.getReceivedAt(),
                record.getPublishedAt(),
                duplicate,
                record.getDiagnostics() == null || record.getDiagnostics().isEmpty() ? null : record.getDiagnostics());
    }

    public DocumentResponse toResponse(ProcessingRecord record) {
        return toResponse(record, false);
    }
}
