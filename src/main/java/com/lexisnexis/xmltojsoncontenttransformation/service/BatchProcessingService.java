package com.lexisnexis.xmltojsoncontenttransformation.service;

import com.lexisnexis.xmltojsoncontenttransformation.constant.BatchStatus;
import com.lexisnexis.xmltojsoncontenttransformation.constant.ProcessingStatus;
import com.lexisnexis.xmltojsoncontenttransformation.entity.BatchJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchProcessingService {

    private final DocumentProcessingService processingService;
    private final ExecutorService pipelineExecutor;

    private final ConcurrentMap<String, BatchJob> jobs = new ConcurrentHashMap<>();

    public BatchJob submit(String inputDir) {
        Path dir = Path.of(inputDir);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Not a readable directory: " + inputDir);
        }

        BatchJob job = new BatchJob(UUID.randomUUID().toString(), inputDir);
        jobs.put(job.getBatchId(), job);

        List<Path> files = listXmlFiles(dir);
        job.setTotalFiles(files.size());
        log.info("Batch {} submitted with {} file(s) from {}", job.getBatchId(), files.size(), inputDir);

        if (files.isEmpty()) {
            job.setStatus(BatchStatus.COMPLETED);
            job.setCompletedAt(Instant.now());
            return job;
        }

        // each file is read inside its worker, so we never hold the whole batch in memory
        List<CompletableFuture<Void>> futures = files.stream()
                .map(file -> CompletableFuture.runAsync(() -> processFile(job, file), pipelineExecutor))
                .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .whenComplete((v, t) -> {
                    job.setStatus(BatchStatus.COMPLETED);
                    job.setCompletedAt(Instant.now());
                    log.info("Batch {} completed: {} published, {} rejected, {} duplicates, {} failed",
                            job.getBatchId(), job.getPublished().get(), job.getRejected().get(),
                            job.getDuplicates().get(), job.getFailed().get());
                });
        return job;
    }

    public Optional<BatchJob> findById(String batchId) {
        return Optional.ofNullable(jobs.get(batchId));
    }

    public Collection<BatchJob> findAll() {
        return jobs.values();
    }

    private void processFile(BatchJob job, Path file) {
        try {
            String xml = Files.readString(file, StandardCharsets.UTF_8);
            var result = processingService.process(xml, "bulk");
            if (result.duplicate()) {
                job.getDuplicates().incrementAndGet();
            } else if (result.record().getStatus() == ProcessingStatus.PUBLISHED) {
                job.getPublished().incrementAndGet();
            } else {
                job.getRejected().incrementAndGet();
            }
        } catch (Exception e) {
            log.error("Batch {}: failed to process {}: {}", job.getBatchId(), file, e.getMessage());
            job.getFailed().incrementAndGet();
        }
    }

    private List<Path> listXmlFiles(Path dir) {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.xml")) {
            stream.forEach(files::add);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read directory " + dir + ": " + e.getMessage());
        }
        return files;
    }
}
