package com.lexisnexis.xmltojsoncontenttransformation.controller;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DocumentControllerTest {

    @TempDir
    static Path outputDir;

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void overrideOutputDir(DynamicPropertyRegistry registry) {
        registry.add("app.storage.output-dir", () -> outputDir.toString());
    }

    @Test
    @Order(1)
    void submittingValidDocumentPublishesIt() throws Exception {
        mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(readResource("/valid-judgment.xml")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentId").value("FR-2024-CA-000123"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.duplicate").value(false));
    }

    @Test
    @Order(2)
    void resubmittingSameContentIsIdempotent() throws Exception {
        String xml = readResource("/valid-judgment.xml");
        mockMvc.perform(post("/api/documents")
                .contentType(MediaType.APPLICATION_XML)
                .content(xml));

        mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(xml))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    @Order(3)
    void invalidDocumentIsRejectedWithDiagnostics() throws Exception {
        mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(readResource("/invalid-judgment.xml")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.diagnostics").isNotEmpty());
    }

    @Test
    @Order(4)
    void publishedArtifactsAreRetrievable() throws Exception {
        mockMvc.perform(post("/api/documents")
                .contentType(MediaType.APPLICATION_XML)
                .content(readResource("/valid-judgment.xml")));

        mockMvc.perform(get("/api/documents/FR-2024-CA-000123/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content_id").value("FR-2024-CA-000123"))
                .andExpect(jsonPath("$.full_text", containsString("Par ces motifs")));

        mockMvc.perform(get("/api/documents/FR-2024-CA-000123/text"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Le litige porte sur")));
    }

    @Test
    @Order(5)
    void rejectedDocumentHasNoArtifacts() throws Exception {
        mockMvc.perform(post("/api/documents")
                .contentType(MediaType.APPLICATION_XML)
                .content(readResource("/invalid-judgment.xml")));

        mockMvc.perform(get("/api/documents/FR-2024-CA-000999/json"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(6)
    void unknownDocumentReturns404() throws Exception {
        mockMvc.perform(get("/api/documents/does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(7)
    void uploadedFileIsProcessedLikeRawBody() throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "judgment.xml", MediaType.APPLICATION_XML_VALUE,
                readResource("/valid-judgment.xml").getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentId").value("FR-2024-CA-000123"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    @Order(8)
    void emptyUploadIsRejected() throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "empty.xml", MediaType.APPLICATION_XML_VALUE, new byte[0]);

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isBadRequest());
    }

    private String readResource(String name) throws IOException {
        return new String(getClass().getResourceAsStream(name).readAllBytes(), StandardCharsets.UTF_8);
    }
}
