package io.github.kschafer2.securedocumentplatform.controller;

import io.github.kschafer2.securedocumentplatform.controller.dto.DocumentMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(path = "/documents")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadDocument(@RequestPart("file") MultipartFile file) {
        log.info("uploadDocument: I've been hit!\nIncoming file: {}", file.getOriginalFilename());
        UUID documentId = UUID.randomUUID(); //todo: will be updated; stand in for returning path, for now
        return ResponseEntity.created(
                UriComponentsBuilder.fromPath("/documents/{documentId}")
                        .buildAndExpand(documentId)
                        .toUri()
        ).build();
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<DocumentMetadata>> listDocuments() {
        log.info("listDocuments: I've been hit!");
        return ResponseEntity.ok().build();
    }

    @GetMapping(path = "/{documentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DocumentMetadata> getDocumentMetadata(@PathVariable UUID documentId) {
        log.info("getDocumentMetadata: I've been hit!\nDocumentId: {}", documentId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(path = "/{documentId}/content")
    public ResponseEntity<?> downloadDocumentContent(@PathVariable UUID documentId) {
        log.info("downloadDocumentContent: I've been hit!\nDocumentId: {}", documentId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(path = "/{documentId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID documentId) {
        log.info("deleteDocument: I've been hit!\nDocumentId: {}", documentId);
        return ResponseEntity.noContent().build();
    }
}