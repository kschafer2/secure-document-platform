package io.github.kschafer2.securedocumentplatform.application.upload.exception;

public class TooLargeFileException extends RuntimeException {

    public TooLargeFileException(String message) {
        super(message);
    }
}
