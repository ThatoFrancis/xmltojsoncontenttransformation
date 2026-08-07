package com.lexisnexis.xmltojsoncontenttransformation.service;

import com.lexisnexis.xmltojsoncontenttransformation.config.AppProperties;
import com.lexisnexis.xmltojsoncontenttransformation.constant.BatchStatus;
import com.lexisnexis.xmltojsoncontenttransformation.constant.ProcessingStatus;
import com.lexisnexis.xmltojsoncontenttransformation.entity.BatchJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;

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
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchProcessingService {

    private final DocumentProcessingService processingService;
    private final ExecutorService pipelineExecutor;
    private final ObjectProvider<S3Client> s3ClientProvider;
    private final AppProperties properties;

    private final ConcurrentMap<String, BatchJob> jobs = new ConcurrentHashMap<>();

    public BatchJob submit(String inputDir) {
        List<BatchItem> items = inputDir.startsWith("s3://") ? listS3Objects(inputDir) : listLocalFiles(inputDir);

        BatchJob job = new BatchJob(UUID.randomUUID().toString(), inputDir);
        jobs.put(job.getBatchId(), job);

        job.setTotalFiles(items.size());
        log.info("Batch {} submitted with {} file(s) from {}", job.getBatchId(), items.size(), inputDir);

        if (items.isEmpty()) {
            job.setStatus(BatchStatus.COMPLETED);
            job.setCompletedAt(Instant.now());
            return job;
        }

        // each file is read inside its worker, so we never hold the whole batch in memory
        List<CompletableFuture<Void>> futures = items.stream()
                .map(item -> CompletableFuture.runAsync(() -> processItem(job, item), pipelineExecutor))
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

    private void processItem(BatchJob job, BatchItem item) {
        try {
            String xml = item.content().get();
            var result = processingService.process(xml, properties.getPipeline().getBatchCollection());
            if (result.duplicate()) {
                job.getDuplicates().incrementAndGet();
            } else if (result.record().getStatus() == ProcessingStatus.PUBLISHED) {
                job.getPublished().incrementAndGet();
            } else {
                job.getRejected().incrementAndGet();
            }
        } catch (Exception e) {
            log.error("Batch {}: failed to process {}: {}", job.getBatchId(), item.name(), e.getMessage());
            job.getFailed().incrementAndGet();
        }
    }

    private List<BatchItem> listLocalFiles(String inputDir) {
        Path dir = Path.of(inputDir);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Not a readable directory: " + inputDir);
        }
        List<BatchItem> items = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.xml")) {
            stream.forEach(file -> items.add(new BatchItem(file.toString(), () -> readFile(file))));
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read directory " + dir + ": " + e.getMessage());
        }
        return items;
    }

    private List<BatchItem> listS3Objects(String uri) {
        S3Client s3 = s3ClientProvider.getIfAvailable();
        if (s3 == null) {
            throw new IllegalArgumentException("S3 input requires the service to run with s3 storage enabled");
        }
        String path = uri.substring("s3://".length());
        int slash = path.indexOf('/');
        String bucket = slash < 0 ? path : path.substring(0, slash);
        String prefix = slash < 0 ? "" : path.substring(slash + 1);

        List<BatchItem> items = new ArrayList<>();
        s3.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build())
                .contents().stream()
                .filter(o -> o.key().endsWith(".xml"))
                .forEach(o -> items.add(new BatchItem("s3://" + bucket + "/" + o.key(),
                        () -> readS3Object(s3, bucket, o.key()))));
        return items;
    }

    private String readFile(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + file + ": " + e.getMessage(), e);
        }
    }

    private String readS3Object(S3Client s3, String bucket, String key) {
        return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .asString(StandardCharsets.UTF_8);
    }

    private record BatchItem(String name, Supplier<String> content) {
    }
}
