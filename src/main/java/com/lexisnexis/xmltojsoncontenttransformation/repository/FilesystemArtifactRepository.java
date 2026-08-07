package com.lexisnexis.xmltojsoncontenttransformation.repository;

import com.lexisnexis.xmltojsoncontenttransformation.config.AppProperties;
import com.lexisnexis.xmltojsoncontenttransformation.constant.ArtifactType;
import com.lexisnexis.xmltojsoncontenttransformation.exception.ArtifactStorageException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "app.storage.type", havingValue = "filesystem", matchIfMissing = true)
public class FilesystemArtifactRepository implements ArtifactRepository {

    private final Path outputDir;
    private final AppProperties.Storage storage;

    public FilesystemArtifactRepository(AppProperties properties) {
        this.storage = properties.getStorage();
        this.outputDir = Path.of(storage.getOutputDir());
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            throw new ArtifactStorageException("Could not create output directory " + outputDir, e);
        }
    }

    @Override
    public void store(String contentId, ArtifactType type, String content, String collection) {
        try {
            Path dir = resolveContentDir(contentId, collection);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(storage.fileNameFor(type)), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ArtifactStorageException("Failed to store artifact for " + contentId, e);
        }
    }

    @Override
    public Optional<String> retrieve(String contentId, ArtifactType type, String collection) {
        Path file = resolveContentDir(contentId, collection).resolve(storage.fileNameFor(type));
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ArtifactStorageException("Failed to read artifact for " + contentId, e);
        }
    }

    // guard against path traversal via a malicious content_id or collection name
    private Path resolveContentDir(String contentId, String collection) {
        Path base = collection == null || collection.isBlank() ? outputDir : outputDir.resolve(collection);
        Path dir = base.resolve(contentId).normalize();
        if (!dir.startsWith(outputDir.normalize())) {
            throw new ArtifactStorageException("Invalid content id: " + contentId, null);
        }
        return dir;
    }
}
