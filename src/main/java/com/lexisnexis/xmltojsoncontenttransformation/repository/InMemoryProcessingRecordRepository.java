package com.lexisnexis.xmltojsoncontenttransformation.repository;

import com.lexisnexis.xmltojsoncontenttransformation.entity.ProcessingRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
@ConditionalOnProperty(name = "app.registry.type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryProcessingRecordRepository implements ProcessingRecordRepository {

    private final ConcurrentMap<String, ProcessingRecord> records = new ConcurrentHashMap<>();

    @Override
    public ProcessingRecord save(ProcessingRecord record) {
        records.put(record.getContentId(), record);
        return record;
    }

    @Override
    public Optional<ProcessingRecord> findByContentId(String contentId) {
        return Optional.ofNullable(records.get(contentId));
    }

    @Override
    public Collection<ProcessingRecord> findAll() {
        return records.values();
    }
}
