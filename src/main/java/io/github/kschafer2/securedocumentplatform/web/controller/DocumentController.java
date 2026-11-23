package io.github.kschafer2.securedocumentplatform.web.controller;

import io.github.kschafer2.securedocumentplatform.web.response.DocumentMetadataResponse;
import io.github.kschafer2.securedocumentplatform.application.DocumentService;
import io.github.kschafer2.securedocumentplatform.web.adapter.MultipartFileUploadContent;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(path = "/documents")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadDocument(HttpServletRequest request, Principal auth, @RequestPart("file") MultipartFile file) {
        logRequest(request, auth);

        UUID documentId = documentService.upload(auth.getName(), new MultipartFileUploadContent(file));

        return ResponseEntity.created(
                UriComponentsBuilder.fromPath("/documents/{documentId}")
                        .buildAndExpand(documentId)
                        .toUri()
        ).build();
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<DocumentMetadataResponse>> listDocuments(HttpServletRequest httpRequest, Principal auth) {
        logRequest(httpRequest, auth);

        return ResponseEntity.ok(List.of());
    }

    @GetMapping(path = "/{documentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DocumentMetadataResponse> getDocumentMetadata(HttpServletRequest httpRequest, Principal auth, @PathVariable UUID documentId) {
        logRequest(httpRequest, auth);

        return ResponseEntity.ok(
                new DocumentMetadataResponse(
                        documentId,
                        null,
                        0L,
                        null,
                        null
                )
        );
    }

    @GetMapping(path = "/{documentId}/content")
    public ResponseEntity<?> downloadDocumentContent(HttpServletRequest httpRequest, Principal auth, @PathVariable UUID documentId) {
        logRequest(httpRequest, auth);

        return ResponseEntity.ok().build();
    }

    @DeleteMapping(path = "/{documentId}")
    public ResponseEntity<Void> deleteDocument(HttpServletRequest httpRequest, Principal auth, @PathVariable UUID documentId) {
        logRequest(httpRequest, auth);

        return ResponseEntity.noContent().build();
    }

    private static void logRequest(HttpServletRequest http, Principal auth) {
        log.info("{} {}; ownerSubject: {}", http.getMethod(), http.getRequestURI(), auth.getName());
    }
}