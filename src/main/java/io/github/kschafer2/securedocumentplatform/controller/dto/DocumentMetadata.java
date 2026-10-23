package io.github.kschafer2.securedocumentplatform.controller.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentMetadata(
        UUID documentId,
        String fileName,
        long fileSizeBytes,
        String contentType,
        OffsetDateTime uploadedAt
) {}