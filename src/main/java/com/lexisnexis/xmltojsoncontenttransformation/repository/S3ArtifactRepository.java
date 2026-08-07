package com.lexisnexis.xmltojsoncontenttransformation.repository;

import com.lexisnexis.xmltojsoncontenttransformation.config.AppProperties;
import com.lexisnexis.xmltojsoncontenttransformation.constant.ArtifactType;
import com.lexisnexis.xmltojsoncontenttransformation.exception.ArtifactStorageException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3ArtifactRepository implements ArtifactRepository {

    private final S3Client s3;
    private final String bucket;
    private final AppProperties.Storage storage;

    public S3ArtifactRepository(S3Client s3, AppProperties properties) {
        this.s3 = s3;
        this.storage = properties.getStorage();
        this.bucket = storage.getBucket();
    }

    @Override
    public void store(String contentId, ArtifactType type, String content, String collection) {
        String key = objectKey(contentId, type, collection);
        try {
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(),
                    RequestBody.fromString(content, StandardCharsets.UTF_8));
        } catch (SdkException e) {
            throw new ArtifactStorageException("Failed to store artifact for " + contentId, e);
        }
    }

    @Override
    public Optional<String> retrieve(String contentId, ArtifactType type, String collection) {
        String key = objectKey(contentId, type, collection);
        try {
            return Optional.of(s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build())
                    .asString(StandardCharsets.UTF_8));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (SdkException e) {
            throw new ArtifactStorageException("Failed to read artifact for " + contentId, e);
        }
    }

    // same layout as the filesystem store: [collection/]contentId/fileName
    private String objectKey(String contentId, ArtifactType type, String collection) {
        if (contentId.contains("/") || contentId.contains("..")) {
            throw new ArtifactStorageException("Invalid content id: " + contentId, null);
        }
        String prefix = collection == null || collection.isBlank() ? "" : collection + "/";
        return prefix + contentId + "/" + storage.fileNameFor(type);
    }
}
