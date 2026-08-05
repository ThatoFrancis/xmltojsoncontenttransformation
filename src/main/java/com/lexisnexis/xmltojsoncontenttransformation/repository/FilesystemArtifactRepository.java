package com.lexisnexis.xmltojsoncontenttransformation.repository;

import com.lexisnexis.xmltojsoncontenttransformation.config.AppProperties;
import com.lexisnexis.xmltojsoncontenttransformation.constant.ArtifactType;
import com.lexisnexis.xmltojsoncontenttransformation.exception.ArtifactStorageException;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Repository
public class FilesystemArtifactRepository implements ArtifactRepository {

    private final Path outputDir;

    public FilesystemArtifactRepository(AppProperties properties) {
        this.outputDir = Path.of(properties.getStorage().getOutputDir());
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            throw new ArtifactStorageException("Could not create output directory " + outputDir, e);
        }
    }

    @Override
    public void store(String contentId, ArtifactType type, String content) {
        try {
            Path dir = resolveContentDir(contentId);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(type.getFileName()), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ArtifactStorageException("Failed to store artifact for " + contentId, e);
        }
    }

    @Override
    public Optional<String> retrieve(String contentId, ArtifactType type) {
        Path file = resolveContentDir(contentId).resolve(type.getFileName());
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ArtifactStorageException("Failed to read artifact for " + contentId, e);
        }
    }

    // guard against path traversal via a malicious content_id
    private Path resolveContentDir(String contentId) {
        Path dir = outputDir.resolve(contentId).normalize();
        if (!dir.startsWith(outputDir.normalize())) {
            throw new ArtifactStorageException("Invalid content id: " + contentId, null);
        }
        return dir;
    }
}
