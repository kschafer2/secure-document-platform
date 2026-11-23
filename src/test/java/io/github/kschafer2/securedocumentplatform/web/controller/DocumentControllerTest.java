package io.github.kschafer2.securedocumentplatform.web.controller;

import io.github.kschafer2.securedocumentplatform.application.upload.UploadContent;
import io.github.kschafer2.securedocumentplatform.security.SecurityConfig;
import io.github.kschafer2.securedocumentplatform.application.DocumentService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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

    @MockitoBean
    private DocumentService documentService;

    @Test
    void uploadDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "file.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "file".getBytes()
        );

        UUID documentId = UUID.randomUUID();
        String uuidPattern = Pattern.compile("[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$").pattern();

        ArgumentCaptor<UploadContent> contentCaptor = ArgumentCaptor.forClass(UploadContent.class);

        when(documentService.upload(any(), any()))
                .thenReturn(documentId);

        mockMvc.perform(multipart("/documents")
                        .file(file)
                        .with(jwt())
                )
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern("^/documents/" + uuidPattern)))
                .andExpect(header().string(HttpHeaders.LOCATION, "/documents/" + documentId))
                .andExpect(content().string(""));

        verify(documentService).upload(any(), contentCaptor.capture());

        UploadContent captured = contentCaptor.getValue();

        Assertions.assertEquals(4, captured.fileSizeBytes());
        Assertions.assertEquals("file.txt", captured.fileName());
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