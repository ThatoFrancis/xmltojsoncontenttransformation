package com.lexisnexis.xmltojsoncontenttransformation.service;

import com.lexisnexis.xmltojsoncontenttransformation.constant.ArtifactType;
import com.lexisnexis.xmltojsoncontenttransformation.constant.BatchStatus;
import com.lexisnexis.xmltojsoncontenttransformation.entity.BatchJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class BatchProcessingServiceTest {

    @TempDir
    static Path outputDir;

    @TempDir
    Path inputDir;

    @Autowired
    private BatchProcessingService batchService;

    @Autowired
    private DocumentProcessingService processingService;

    @DynamicPropertySource
    static void overrideOutputDir(DynamicPropertyRegistry registry) {
        registry.add("app.storage.output-dir", () -> outputDir.toString());
    }

    @Test
    void processesFolderConcurrentlyAndTracksCounts() throws Exception {
        for (int i = 1; i <= 20; i++) {
            Files.writeString(inputDir.resolve("doc-" + i + ".xml"), judgment("BATCH-" + i));
        }
        Files.writeString(inputDir.resolve("broken.xml"), "<judgment>not valid</judgment>");
        Files.writeString(inputDir.resolve("not-xml.txt"), "ignored");

        BatchJob job = batchService.submit(inputDir.toString());
        awaitCompletion(job);

        assertThat(job.getTotalFiles()).isEqualTo(21);
        assertThat(job.getPublished().get()).isEqualTo(20);
        assertThat(job.getRejected().get()).isEqualTo(1);
        assertThat(job.getFailed().get()).isZero();

        // batch artifacts are grouped under the bulk collection folder
        assertThat(outputDir.resolve("bulk").resolve("BATCH-1").resolve("normalized.json")).exists();
        assertThat(outputDir.resolve("bulk").resolve("BATCH-20").resolve("fulltext.txt")).exists();
    }

    @Test
    void batchPublishedArtifactsAreRetrievableThroughTheDocumentApi() throws Exception {
        Files.writeString(inputDir.resolve("r.xml"), judgment("RETRIEVE-1"));
        BatchJob job = batchService.submit(inputDir.toString());
        awaitCompletion(job);

        assertThat(processingService.retrievePublishedArtifact("RETRIEVE-1", ArtifactType.NORMALIZED_JSON))
                .isPresent()
                .hasValueSatisfying(json -> assertThat(json).contains("RETRIEVE-1"));
    }

    @Test
    void duplicateFilesInBatchAreCountedNotRepublished() throws Exception {
        Files.writeString(inputDir.resolve("a.xml"), judgment("DUP-1"));
        BatchJob first = batchService.submit(inputDir.toString());
        awaitCompletion(first);

        BatchJob second = batchService.submit(inputDir.toString());
        awaitCompletion(second);

        assertThat(second.getDuplicates().get()).isEqualTo(1);
        assertThat(second.getPublished().get()).isZero();
    }

    @Test
    void unknownDirectoryIsRejected() {
        assertThatThrownBy(() -> batchService.submit(inputDir.resolve("missing").toString()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void awaitCompletion(BatchJob job) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (job.getStatus() != BatchStatus.COMPLETED && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(job.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }

    private String judgment(String contentId) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <judgment xmlns="urn:lex:content:1">
                  <header>
                    <content_id>%s</content_id>
                    <title>Test judgment %s</title>
                    <court>Cour d'appel de Paris</court>
                    <jurisdiction>FR</jurisdiction>
                    <decision_date>2024-03-12</decision_date>
                  </header>
                  <body>
                    <section type="facts">
                      <p id="p1">Le litige porte sur...</p>
                    </section>
                  </body>
                </judgment>
                """.formatted(contentId, contentId);
    }
}
