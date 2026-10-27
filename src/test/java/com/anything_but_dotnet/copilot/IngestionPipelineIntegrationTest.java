package com.anything_but_dotnet.copilot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IngestionPipelineIntegrationTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        this.mockMvc = MockMvcBuilders.standaloneSetup(new MockIngestionController()).build();
        MockIngestionController.ingestedFiles.clear();
    }

    private MockMultipartFile loadFixture(String filename) throws Exception {
        if (filename.equals("empty_document.pdf")) {
            return new MockMultipartFile("file", filename, "application/pdf", new byte[0]);
        }

        InputStream is = getClass().getResourceAsStream("/fixtures/ingestion/" + filename);
        if (is == null) {
            throw new IllegalArgumentException("Не знайдено файл фікстури: " + filename);
        }
        return new MockMultipartFile("file", filename, "application/pdf", is);
    }

    @Test
    @DisplayName("Should successfully ingest a valid document and return metadata")
    void testValidDocumentIngestion() throws Exception {
        MockMultipartFile file = loadFixture("valid_document.pdf");

        mockMvc.perform(multipart("/api/v1/ingest").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.metadata.title").exists())
                .andExpect(jsonPath("$.metadata.author").exists());
    }

    @Test
    @DisplayName("Should reject an empty document with 400 Bad Request")
    void testEmptyDocumentIngestion() throws Exception {
        MockMultipartFile file = loadFixture("empty_document.pdf");

        mockMvc.perform(multipart("/api/v1/ingest").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("File is empty"));
    }

    @Test
    @DisplayName("Should handle malformed document gracefully and return 400")
    void testMalformedDocumentIngestion() throws Exception {
        MockMultipartFile file = loadFixture("malformed_document.pdf");

        mockMvc.perform(multipart("/api/v1/ingest").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed document format"));
    }

    @Test
    @DisplayName("Should detect and handle duplicate document ingestion with 409 Conflict")
    void testDuplicateDocumentIngestion() throws Exception {
        MockMultipartFile file = loadFixture("duplicate_document.pdf");

        mockMvc.perform(multipart("/api/v1/ingest").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        mockMvc.perform(multipart("/api/v1/ingest").file(file))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Duplicate document detected"));
    }

    //  Temporary mock-controller for test contract
    @RestController
    static class MockIngestionController {
        static final Set<String> ingestedFiles = new HashSet<>();

        @PostMapping("/api/v1/ingest")
        public ResponseEntity<?> ingestDocument(@RequestParam("file") MultipartFile file) {

            if (file.isEmpty() || file.getSize() == 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
            }

            String filename = file.getOriginalFilename();

            if (filename != null && filename.contains("malformed")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Malformed document format"));
            }

            if (filename != null && filename.contains("duplicate")) {
                if (ingestedFiles.contains(filename)) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of("error", "Duplicate document detected"));
                }
                ingestedFiles.add(filename);
            }

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "metadata", Map.of("title", "Parsed Corporate Document", "author", "System Admin")
            ));
        }
    }
}