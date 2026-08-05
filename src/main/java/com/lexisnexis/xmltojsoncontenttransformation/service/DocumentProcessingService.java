package com.lexisnexis.xmltojsoncontenttransformation.service;

import com.lexisnexis.xmltojsoncontenttransformation.config.AppProperties;
import com.lexisnexis.xmltojsoncontenttransformation.constant.ArtifactType;
import com.lexisnexis.xmltojsoncontenttransformation.constant.ProcessingStatus;
import com.lexisnexis.xmltojsoncontenttransformation.dto.DiagnosticDto;
import com.lexisnexis.xmltojsoncontenttransformation.entity.ProcessingRecord;
import com.lexisnexis.xmltojsoncontenttransformation.exception.DocumentTooLargeException;
import com.lexisnexis.xmltojsoncontenttransformation.repository.ArtifactRepository;
import com.lexisnexis.xmltojsoncontenttransformation.repository.ProcessingRecordRepository;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class DocumentProcessingService {

    private final XmlValidationService validationService;
    private final XmlTransformationService transformationService;
    private final ContentIdExtractor contentIdExtractor;
    private final ProcessingRecordRepository recordRepository;
    private final ArtifactRepository artifactRepository;
    private final AppProperties properties;

    public record ProcessingResult(ProcessingRecord record, boolean duplicate) {
    }

    public ProcessingResult process(String xml) {
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
            return new ProcessingResult(existing, true);
        }

        Instant receivedAt = Instant.now();
        List<DiagnosticDto> diagnostics = validationService.validate(xml);
        boolean hasErrors = diagnostics.stream()
                .anyMatch(d -> !"WARNING".equals(d.severity()));

        if (hasErrors) {
            log.warn("Rejected {} with {} diagnostic(s)", contentId, diagnostics.size());
            return new ProcessingResult(recordRepository.save(ProcessingRecord.builder()
                    .contentId(contentId)
                    .status(ProcessingStatus.REJECTED)
                    .contentHash(contentHash)
                    .receivedAt(receivedAt)
                    .diagnostics(diagnostics)
                    .build()), false);
        }

        var result = transformationService.transform(xml);
        artifactRepository.store(contentId, ArtifactType.NORMALIZED_JSON, result.normalizedJson());
        artifactRepository.store(contentId, ArtifactType.FULL_TEXT, result.fullText());

        log.info("Published {}", contentId);
        return new ProcessingResult(recordRepository.save(ProcessingRecord.builder()
                .contentId(contentId)
                .status(ProcessingStatus.PUBLISHED)
                .contentHash(contentHash)
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
                .flatMap(r -> artifactRepository.retrieve(contentId, type));
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
