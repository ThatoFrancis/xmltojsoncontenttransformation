package com.lexisnexis.xmltojsoncontenttransformation.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BatchControllerTest {

    @TempDir
    static Path outputDir;

    @TempDir
    Path inputDir;

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void overrideOutputDir(DynamicPropertyRegistry registry) {
        registry.add("app.storage.output-dir", () -> outputDir.toString());
    }

    @Test
    void submittingAFolderIsAcceptedAndTracked() throws Exception {
        Files.writeString(inputDir.resolve("one.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <judgment xmlns="urn:lex:content:1">
                  <header>
                    <content_id>BATCH-API-1</content_id>
                    <title>t</title>
                    <court>c</court>
                    <jurisdiction>FR</jurisdiction>
                    <decision_date>2024-03-12</decision_date>
                  </header>
                  <body><section type="facts"><p id="p1">x</p></section></body>
                </judgment>
                """);

        String batchId = com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(post("/api/batches")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"inputDir\": \"%s\"}".formatted(inputDir.toString().replace("\\", "\\\\"))))
                        .andExpect(status().isAccepted())
                        .andExpect(jsonPath("$.batchId").exists())
                        .andExpect(jsonPath("$.totalFiles").value(1))
                        .andReturn().getResponse().getContentAsString(), "$.batchId");

        mockMvc.perform(get("/api/batches/" + batchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFiles").value(1));

        mockMvc.perform(get("/api/batches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void blankInputDirIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inputDir\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonExistentFolderIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inputDir\": \"C:/does/not/exist\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownBatchIdReturns404() throws Exception {
        mockMvc.perform(get("/api/batches/nope"))
                .andExpect(status().isNotFound());
    }
}
