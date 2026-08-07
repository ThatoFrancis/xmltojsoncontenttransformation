package com.lexisnexis.xmltojsoncontenttransformation.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexisnexis.xmltojsoncontenttransformation.constant.ProcessingStatus;
import com.lexisnexis.xmltojsoncontenttransformation.dto.DiagnosticDto;
import com.lexisnexis.xmltojsoncontenttransformation.entity.ProcessingRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "app.registry.type", havingValue = "dynamodb")
public class DynamoDbProcessingRecordRepository implements ProcessingRecordRepository {

    private final DynamoDbClient dynamoDb;
    private final String tableName;
    private final ObjectMapper objectMapper;

    public DynamoDbProcessingRecordRepository(DynamoDbClient dynamoDb,
                                              com.lexisnexis.xmltojsoncontenttransformation.config.AppProperties properties,
                                              ObjectMapper objectMapper) {
        this.dynamoDb = dynamoDb;
        this.tableName = properties.getRegistry().getTable();
        this.objectMapper = objectMapper;
    }

    @Override
    public ProcessingRecord save(ProcessingRecord record) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("content_id", AttributeValue.fromS(record.getContentId()));
        item.put("status", AttributeValue.fromS(record.getStatus().name()));
        if (record.getContentHash() != null) {
            item.put("content_hash", AttributeValue.fromS(record.getContentHash()));
        }
        if (record.getCollection() != null) {
            item.put("collection", AttributeValue.fromS(record.getCollection()));
        }
        if (record.getReceivedAt() != null) {
            item.put("received_at", AttributeValue.fromS(record.getReceivedAt().toString()));
        }
        if (record.getPublishedAt() != null) {
            item.put("published_at", AttributeValue.fromS(record.getPublishedAt().toString()));
        }
        if (record.getDiagnostics() != null && !record.getDiagnostics().isEmpty()) {
            item.put("diagnostics", AttributeValue.fromS(writeDiagnostics(record.getDiagnostics())));
        }
        dynamoDb.putItem(PutItemRequest.builder().tableName(tableName).item(item).build());
        return record;
    }

    @Override
    public Optional<ProcessingRecord> findByContentId(String contentId) {
        Map<String, AttributeValue> item = dynamoDb.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("content_id", AttributeValue.fromS(contentId)))
                .consistentRead(true)
                .build()).item();
        return item == null || item.isEmpty() ? Optional.empty() : Optional.of(toRecord(item));
    }

    @Override
    public Collection<ProcessingRecord> findAll() {
        return dynamoDb.scanPaginator(ScanRequest.builder().tableName(tableName).build())
                .items().stream()
                .map(this::toRecord)
                .toList();
    }

    private ProcessingRecord toRecord(Map<String, AttributeValue> item) {
        return ProcessingRecord.builder()
                .contentId(item.get("content_id").s())
                .status(ProcessingStatus.valueOf(item.get("status").s()))
                .contentHash(stringOrNull(item.get("content_hash")))
                .collection(stringOrNull(item.get("collection")))
                .receivedAt(instantOrNull(item.get("received_at")))
                .publishedAt(instantOrNull(item.get("published_at")))
                .diagnostics(readDiagnostics(item.get("diagnostics")))
                .build();
    }

    private String stringOrNull(AttributeValue value) {
        return value == null ? null : value.s();
    }

    private Instant instantOrNull(AttributeValue value) {
        return value == null ? null : Instant.parse(value.s());
    }

    private String writeDiagnostics(List<DiagnosticDto> diagnostics) {
        try {
            return objectMapper.writeValueAsString(diagnostics);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize diagnostics", e);
        }
    }

    private List<DiagnosticDto> readDiagnostics(AttributeValue value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value.s(), new TypeReference<List<DiagnosticDto>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not deserialize diagnostics", e);
        }
    }
}