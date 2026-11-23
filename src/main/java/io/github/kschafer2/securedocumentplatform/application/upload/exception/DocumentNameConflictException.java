package io.github.kschafer2.securedocumentplatform.application.upload.exception;

public class DocumentNameConflictException extends RuntimeException {

    public DocumentNameConflictException(String ownerSubject, String fileName) {
        super("Duplicate document for ownerSubject: " + ownerSubject + " and fileName: " + fileName);
    }

    public DocumentNameConflictException(String ownerSubject, String fileName, Throwable cause) {
        super("Duplicate document for ownerSubject: " + ownerSubject + " and fileName: " + fileName, cause);
    }
}
