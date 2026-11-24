package io.github.kschafer2.securedocumentplatform.application;

import io.github.kschafer2.securedocumentplatform.application.upload.NewDocument;

import java.util.UUID;

public interface DocumentRepository {

    void save(NewDocument document);

    boolean existsByOwnerSubjectAndFileName(String ownerSubject, String fileName);

    void deleteByDocumentIdAndOwnerSubject(UUID documentId, String ownerSubject);
}
