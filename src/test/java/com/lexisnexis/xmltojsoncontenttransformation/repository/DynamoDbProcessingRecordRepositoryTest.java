package com.lexisnexis.xmltojsoncontenttransformation.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexisnexis.xmltojsoncontenttransformation.config.AppProperties;
import com.lexisnexis.xmltojsoncontenttransformation.constant.ProcessingStatus;
import com.lexisnexis.xmltojsoncontenttransformation.dto.DiagnosticDto;
import com.lexisnexis.xmltojsoncontenttransformation.entity.ProcessingRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamoDbProcessingRecordRepositoryTest {

    private DynamoDbClient dynamoDb;
    private DynamoDbProcessingRecordRepository repository;

    @BeforeEach
    void setUp() {
        dynamoDb = mock(DynamoDbClient.class);
        AppProperties properties = new AppProperties();
        properties.getRegistry().setType("dynamodb");
        properties.getRegistry().setTable("test-table");
        repository = new DynamoDbProcessingRecordRepository(dynamoDb, properties, new ObjectMapper());
    }

    @Test
    void savePutsItemWithAllFields() {
        ProcessingRecord record = ProcessingRecord.builder()
                .contentId("FR-2024-CA-000123")
                .status(ProcessingStatus.PUBLISHED)
                .contentHash("abc123")
                .collection("bulk")
                .receivedAt(Instant.parse("2026-01-01T10:00:00Z"))
                .publishedAt(Instant.parse("2026-01-01T10:00:01Z"))
                .diagnostics(List.of(new DiagnosticDto("WARN", 3, 5, "minor issue")))
                .build();

        repository.save(record);

        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDb).putItem(captor.capture());
        Map<String, AttributeValue> item = captor.getValue().item();
        assertThat(captor.getValue().tableName()).isEqualTo("test-table");
        assertThat(item.get("content_id").s()).isEqualTo("FR-2024-CA-000123");
        assertThat(item.get("status").s()).isEqualTo("PUBLISHED");
        assertThat(item.get("content_hash").s()).isEqualTo("abc123");
        assertThat(item.get("collection").s()).isEqualTo("bulk");
        assertThat(item.get("diagnostics").s()).contains("minor issue");
    }

    @Test
    void saveOmitsNullOptionalFields() {
        ProcessingRecord record = ProcessingRecord.builder()
                .contentId("FR-2024-CA-000124")
                .status(ProcessingStatus.RECEIVED)
                .receivedAt(Instant.parse("2026-01-01T10:00:00Z"))
                .build();

        repository.save(record);

        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDb).putItem(captor.capture());
        Map<String, AttributeValue> item = captor.getValue().item();
        assertThat(item).doesNotContainKeys("content_hash", "collection", "published_at", "diagnostics");
    }

    @Test
    void findByContentIdMapsItemBackToRecord() {
        Map<String, AttributeValue> item = Map.of(
                "content_id", AttributeValue.fromS("FR-2024-CA-000123"),
                "status", AttributeValue.fromS("REJECTED"),
                "received_at", AttributeValue.fromS("2026-01-01T10:00:00Z"),
                "diagnostics", AttributeValue.fromS("[{\"severity\":\"ERROR\",\"line\":2,\"column\":4,\"message\":\"bad element\"}]"));
        when(dynamoDb.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder().item(item).build());

        Optional<ProcessingRecord> found = repository.findByContentId("FR-2024-CA-000123");

        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(ProcessingStatus.REJECTED);
        assertThat(found.get().getReceivedAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
        assertThat(found.get().getDiagnostics()).hasSize(1);
        assertThat(found.get().getDiagnostics().get(0).message()).isEqualTo("bad element");
    }

    @Test
    void findByContentIdReturnsEmptyWhenMissing() {
        when(dynamoDb.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder().build());

        assertThat(repository.findByContentId("missing")).isEmpty();
    }
}