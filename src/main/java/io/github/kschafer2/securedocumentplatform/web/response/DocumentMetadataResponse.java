package io.github.kschafer2.securedocumentplatform.web.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentMetadataResponse(
        UUID documentId,
        String fileName,
        long fileSizeBytes,
        String contentType,
        OffsetDateTime uploadedAt
) {}