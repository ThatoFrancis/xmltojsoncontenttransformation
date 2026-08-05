package com.lexisnexis.xmltojsoncontenttransformation.repository;

import com.lexisnexis.xmltojsoncontenttransformation.constant.ArtifactType;

import java.util.Optional;

public interface ArtifactRepository {

    void store(String contentId, ArtifactType type, String content);

    Optional<String> retrieve(String contentId, ArtifactType type);
}
