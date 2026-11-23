package io.github.kschafer2.securedocumentplatform.application.upload;

import java.util.UUID;

public record NewDocument(
        UUID documentId,
        String ownerSubject,
        String fileName,
        long fileSizeBytes,
        String contentType
) {
}
