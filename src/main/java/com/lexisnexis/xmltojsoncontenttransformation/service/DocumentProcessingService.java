package com.lexisnexis.xmltojsoncontenttransformation.service;

import com.lexisnexis.xmltojsoncontenttransformation.config.AppProperties;
import com.lexisnexis.xmltojsoncontenttransformation.constant.ArtifactType;
import com.lexisnexis.xmltojsoncontenttransformation.constant.ProcessingStatus;
import com.lexisnexis.xmltojsoncontenttransformation.dto.DiagnosticDto;
import com.lexisnexis.xmltojsoncontenttransformation.entity.ProcessingRecord;
import com.lexisnexis.xmltojsoncontenttransformation.exception.DocumentTooLargeException;
import com.lexisnexis.xmltojsoncontenttransformation.repository.ArtifactRepository;
import com.lexisnexis.xmltojsoncontenttransformation.repository.ProcessingRecordRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class DocumentProcessingService {

    private final XmlValidationService validationService;
    private final XmlTransformationService transformationService;
    private final ContentIdExtractor contentIdExtractor;
    private final ProcessingRecordRepository recordRepository;
    private final ArtifactRepository artifactRepository;
    private final AppProperties properties;

    private final Counter receivedCounter;
    private final Counter publishedCounter;
    private final Counter rejectedCounter;
    private final Counter duplicateCounter;
    private final Timer validationTimer;
    private final Timer transformationTimer;

    public DocumentProcessingService(XmlValidationService validationService,
                                     XmlTransformationService transformationService,
                                     ContentIdExtractor contentIdExtractor,
                                     ProcessingRecordRepository recordRepository,
                                     ArtifactRepository artifactRepository,
                                     AppProperties properties,
                                     MeterRegistry meterRegistry) {
        this.validationService = validationService;
        this.transformationService = transformationService;
        this.contentIdExtractor = contentIdExtractor;
        this.recordRepository = recordRepository;
        this.artifactRepository = artifactRepository;
        this.properties = properties;
        this.receivedCounter = meterRegistry.counter("documents.received");
        this.publishedCounter = meterRegistry.counter("documents.published");
        this.rejectedCounter = meterRegistry.counter("documents.rejected");
        this.duplicateCounter = meterRegistry.counter("documents.duplicate");
        this.validationTimer = meterRegistry.timer("documents.validation.duration");
        this.transformationTimer = meterRegistry.timer("documents.transformation.duration");
    }

    public record ProcessingResult(ProcessingRecord record, boolean duplicate) {
    }

    public ProcessingResult process(String xml) {
        return process(xml, null);
    }

    public ProcessingResult process(String xml, String collection) {
        receivedCounter.increment();
        long size = xml.getBytes(StandardCharsets.UTF_8).length;
        long limit = properties.getPipeline().getMaxDocumentSizeBytes();
        if (size > limit) {
            throw new DocumentTooLargeException(size, limit);
        }

        String contentHash = sha256(xml);
        String contentId = contentIdExtractor.extract(xml)
                .filter(id -> !id.isBlank())
                .orElse("unidentified-" + contentHash.substring(0, 12));

        // repeated submission of identical content -> return the existing record untouched
        ProcessingRecord existing = recordRepository.findByContentId(contentId).orElse(null);
        if (existing != null && contentHash.equals(existing.getContentHash())
                && existing.getStatus() == ProcessingStatus.PUBLISHED) {
            log.info("Duplicate submission for {}, skipping republish", contentId);
            duplicateCounter.increment();
            return new ProcessingResult(existing, true);
        }

        Instant receivedAt = Instant.now();
        List<DiagnosticDto> diagnostics = validationTimer.record(() -> validationService.validate(xml));
        boolean hasErrors = diagnostics.stream()
                .anyMatch(d -> !"WARNING".equals(d.severity()));

        if (hasErrors) {
            log.warn("Rejected {} with {} diagnostic(s)", contentId, diagnostics.size());
            rejectedCounter.increment();
            return new ProcessingResult(recordRepository.save(ProcessingRecord.builder()
                    .contentId(contentId)
                    .status(ProcessingStatus.REJECTED)
                    .contentHash(contentHash)
                    .collection(collection)
                    .receivedAt(receivedAt)
                    .diagnostics(diagnostics)
                    .build()), false);
        }

        var result = transformationTimer.record(() -> transformationService.transform(xml));
        artifactRepository.store(contentId, ArtifactType.NORMALIZED_JSON, result.normalizedJson(), collection);
        artifactRepository.store(contentId, ArtifactType.FULL_TEXT, result.fullText(), collection);

        log.info("Published {}", contentId);
        publishedCounter.increment();
        return new ProcessingResult(recordRepository.save(ProcessingRecord.builder()
                .contentId(contentId)
                .status(ProcessingStatus.PUBLISHED)
                .contentHash(contentHash)
                .collection(collection)
                .receivedAt(receivedAt)
                .publishedAt(Instant.now())
                .diagnostics(diagnostics)
                .build()), false);
    }

    public Optional<ProcessingRecord> findByContentId(String contentId) {
        return recordRepository.findByContentId(contentId);
    }

    public Collection<ProcessingRecord> findAll() {
        return recordRepository.findAll();
    }

    public Optional<String> retrievePublishedArtifact(String contentId, ArtifactType type) {
        return recordRepository.findByContentId(contentId)
                .filter(r -> r.getStatus() == ProcessingStatus.PUBLISHED)
                .flatMap(r -> artifactRepository.retrieve(contentId, type, r.getCollection()));
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
