package io.github.kschafer2.securedocumentplatform.application;

import io.github.kschafer2.securedocumentplatform.application.upload.NewDocument;

public interface DocumentRepository {

    void saveDocument(NewDocument document);

    boolean existsByOwnerSubjectAndFileName(String ownerSubject, String fileName);
}
