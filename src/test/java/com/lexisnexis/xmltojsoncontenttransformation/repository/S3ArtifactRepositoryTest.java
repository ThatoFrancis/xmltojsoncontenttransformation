package com.lexisnexis.xmltojsoncontenttransformation.repository;

import com.lexisnexis.xmltojsoncontenttransformation.config.AppProperties;
import com.lexisnexis.xmltojsoncontenttransformation.constant.ArtifactType;
import com.lexisnexis.xmltojsoncontenttransformation.exception.ArtifactStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3ArtifactRepositoryTest {

    private S3Client s3;
    private S3ArtifactRepository repository;

    @BeforeEach
    void setUp() {
        s3 = mock(S3Client.class);
        AppProperties properties = new AppProperties();
        properties.getStorage().setType("s3");
        properties.getStorage().setBucket("test-bucket");
        properties.getStorage().setNormalizedJsonFileName("normalized.json");
        properties.getStorage().setFullTextFileName("fulltext.txt");
        repository = new S3ArtifactRepository(s3, properties);
    }

    @Test
    void storesArtifactUnderContentIdKey() {
        repository.store("FR-2024-CA-000123", ArtifactType.NORMALIZED_JSON, "{}", null);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().key()).isEqualTo("FR-2024-CA-000123/normalized.json");
    }

    @Test
    void storesBatchArtifactUnderCollectionPrefix() {
        repository.store("FR-2024-CA-000123", ArtifactType.FULL_TEXT, "text", "bulk");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().key()).isEqualTo("bulk/FR-2024-CA-000123/fulltext.txt");
    }

    @Test
    void retrieveReturnsObjectContent() {
        ResponseBytes<GetObjectResponse> bytes = ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(), "{\"a\":1}".getBytes(StandardCharsets.UTF_8));
        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(bytes);

        Optional<String> result = repository.retrieve("FR-2024-CA-000123", ArtifactType.NORMALIZED_JSON, null);

        assertThat(result).contains("{\"a\":1}");
    }

    @Test
    void retrieveReturnsEmptyWhenObjectMissing() {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.builder().build());

        assertThat(repository.retrieve("unknown", ArtifactType.NORMALIZED_JSON, null)).isEmpty();
    }

    @Test
    void rejectsContentIdWithPathSeparators() {
        assertThatThrownBy(() -> repository.store("../escape", ArtifactType.NORMALIZED_JSON, "{}", null))
                .isInstanceOf(ArtifactStorageException.class);
    }
}
