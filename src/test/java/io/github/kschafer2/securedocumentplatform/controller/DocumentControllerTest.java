package io.github.kschafer2.securedocumentplatform.controller;

import io.github.kschafer2.securedocumentplatform.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DocumentController.class)
@Import(SecurityConfig.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void uploadDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "file.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "file".getBytes()
        );

        String uuidPattern = Pattern.compile("[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$").pattern();

        mockMvc.perform(multipart("/documents")
                        .file(file)
                        .with(jwt())
                )
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern("^/documents/" + uuidPattern)))
                .andExpect(content().string(""));
    }

    @Test
    void listDocuments() throws Exception {
        mockMvc.perform(get("/documents")
                        .with(jwt())
                )
                .andExpect(status().is(200))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getDocumentMetadata() throws Exception {
        UUID documentId = UUID.randomUUID();

        mockMvc.perform(get("/documents/" + documentId)
                        .with(jwt())
                )
                .andExpect(status().is(200))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.documentId").value(documentId.toString()));
    }

    @Test
    void getDocumentMetadataWithInvalidId() throws Exception {
        mockMvc.perform(get("/documents/not-a-uuid")
                        .with(jwt())
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void downloadDocumentContent() throws Exception {
        UUID documentId = UUID.randomUUID();

        mockMvc.perform(get("/documents/" + documentId + "/content")
                        .with(jwt())
                )
                .andExpect(status().is(200));
    }

    @Test
    void downloadDocumentContentWithInvalidId() throws Exception {
        mockMvc.perform(get("/documents/not-a-uuid/content")
                        .with(jwt())
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteDocument() throws Exception {
        UUID documentId = UUID.randomUUID();

        mockMvc.perform(delete("/documents/" + documentId)
                        .with(jwt())
                )
                .andExpect(status().is(204))
                .andExpect(content().string(""));
    }

    @Test
    void noAuthenticationUploadDocument() throws Exception {
        mockMvc.perform(post("/documents"))
                .andExpect(status().is(401));
    }

    @Test
    void noAuthenticationListDocuments() throws Exception {
        mockMvc.perform(get("/documents"))
                .andExpect(status().is(401));
    }

    @Test
    void noAuthenticationGetDocumentMetadata() throws Exception {
        UUID documentId = UUID.randomUUID();

        mockMvc.perform(get("/documents/" + documentId))
                .andExpect(status().is(401));
    }

    @Test
    void noAuthenticationDownloadDocumentContent() throws Exception {
        UUID documentId = UUID.randomUUID();

        mockMvc.perform(get("/documents/" + documentId + "/content"))
                .andExpect(status().is(401));
    }

    @Test
    void noAuthenticationDeleteDocument() throws Exception {
        UUID documentId = UUID.randomUUID();

        mockMvc.perform(delete("/documents/" + documentId))
                .andExpect(status().is(401));
    }
}