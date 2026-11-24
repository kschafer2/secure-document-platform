package io.github.kschafer2.securedocumentplatform.application.upload.exception;

public class ObjectStorageException extends RuntimeException {

    public ObjectStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
