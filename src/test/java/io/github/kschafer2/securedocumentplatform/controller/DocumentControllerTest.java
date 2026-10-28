package io.github.kschafer2.securedocumentplatform.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void uploadDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "file.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "file".getBytes()
        );

        String uuidPattern = Pattern.compile("[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$").pattern();

        mockMvc.perform(multipart("/documents").file(file))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern("^/documents/" + uuidPattern)))
                .andExpect(content().string(""));
    }

    @Test
    void listDocuments() throws Exception {
        mockMvc.perform(get("/documents"))
                .andExpect(status().is(200))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getDocumentMetadata() throws Exception {
        UUID documentId = UUID.randomUUID();

        mockMvc.perform(get("/documents/" + documentId))
                .andExpect(status().is(200))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.documentId").value(documentId.toString()));
    }

    @Test
    void getDocumentMetadataWithInvalidId() throws Exception {
        mockMvc.perform(get("/documents/not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void downloadDocumentContent() throws Exception {
        UUID documentId = UUID.randomUUID();

        mockMvc.perform(get("/documents/" + documentId + "/content"))
                .andExpect(status().is(200));
    }

    @Test
    void downloadDocumentContentWithInvalidId() throws Exception {
        mockMvc.perform(get("/documents/not-a-uuid/content"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteDocument() throws Exception {
        UUID documentId = UUID.randomUUID();

        mockMvc.perform(delete("/documents/" + documentId))
                .andExpect(status().is(204))
                .andExpect(content().string(""));
    }
}