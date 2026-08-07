package com.lexisnexis.xmltojsoncontenttransformation.controller;

import com.lexisnexis.xmltojsoncontenttransformation.constant.ArtifactType;
import com.lexisnexis.xmltojsoncontenttransformation.dto.DocumentResponse;
import com.lexisnexis.xmltojsoncontenttransformation.exception.DocumentNotFoundException;
import com.lexisnexis.xmltojsoncontenttransformation.mapper.ProcessingRecordMapper;
import com.lexisnexis.xmltojsoncontenttransformation.service.DocumentProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Ingest, validate, transform and retrieve legal XML documents")
public class DocumentController {

    private final DocumentProcessingService processingService;
    private final ProcessingRecordMapper mapper;

    @Operation(summary = "Submit a single XML document for processing")
    @PostMapping(consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DocumentResponse> submit(@RequestBody String xml) {
        return process(xml);
    }

    @Operation(summary = "Submit a single XML document as a file upload")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DocumentResponse> upload(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        try {
            return process(new String(file.getBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read uploaded file", e);
        }
    }

    private ResponseEntity<DocumentResponse> process(String xml) {
        var result = processingService.process(xml);
        HttpStatus status = switch (result.record().getStatus()) {
            case REJECTED -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED;
        };
        return ResponseEntity.status(status).body(mapper.toResponse(result.record(), result.duplicate()));
    }

    @Operation(summary = "List processing status of all submitted documents")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<DocumentResponse> list() {
        return processingService.findAll().stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Operation(summary = "Get processing status and diagnostics for a document")
    @GetMapping(value = "/{contentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DocumentResponse status(@PathVariable String contentId) {
        return processingService.findByContentId(contentId)
                .map(mapper::toResponse)
                .orElseThrow(() -> new DocumentNotFoundException(contentId));
    }

    @Operation(summary = "Retrieve the published normalized JSON artifact")
    @GetMapping(value = "/{contentId}/json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> normalizedJson(@PathVariable String contentId) {
        return processingService.retrievePublishedArtifact(contentId, ArtifactType.NORMALIZED_JSON)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new DocumentNotFoundException(contentId));
    }

    @Operation(summary = "Retrieve the published plain-text artifact for AI/RAG")
    @GetMapping(value = "/{contentId}/text", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> fullText(@PathVariable String contentId) {
        return processingService.retrievePublishedArtifact(contentId, ArtifactType.FULL_TEXT)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new DocumentNotFoundException(contentId));
    }
}
