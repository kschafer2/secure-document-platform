package io.github.kschafer2.securedocumentplatform.web.error;

import io.github.kschafer2.securedocumentplatform.application.upload.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DocumentNameConflictException.class)
    ProblemDetail handleDocumentNameConflict(DocumentNameConflictException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "A document with this filename already exists."
        );
        problemDetail.setProperty("code", "DOCUMENT_NAME_CONFLICT");
        return problemDetail;
    }

    @ExceptionHandler(EmptyFileException.class)
    ProblemDetail handleEmptyFile(EmptyFileException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "The document file is empty."
        );
        problemDetail.setProperty("code", "EMPTY_FILE");
        return problemDetail;
    }

    @ExceptionHandler(TooLargeFileException.class)
    ProblemDetail handleOversizedFile(TooLargeFileException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONTENT_TOO_LARGE,
                "The document file is too large."
        );
        problemDetail.setProperty("code", "FILE_TOO_LARGE");
        return problemDetail;
    }

    @ExceptionHandler(UnsupportedFileContentTypeException.class)
    ProblemDetail handleUnsupportedFileContentType(UnsupportedFileContentTypeException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "File content type is not supported."
        );
        problemDetail.setProperty("code", "UNSUPPORTED_FILE_TYPE");
        return problemDetail;
    }

    @ExceptionHandler(InvalidDocumentFileNameException.class)
    ProblemDetail handleInvalidDocument(InvalidDocumentFileNameException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "The given filename is invalid."
        );
        problemDetail.setProperty("code", "INVALID_FILE_NAME");
        return problemDetail;
    }

    @ExceptionHandler(ContentTypeDetectionException.class)
    ProblemDetail handleContentTypeDetectionFailure(ContentTypeDetectionException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to detect file content type."
        );
        problemDetail.setProperty("code", "CONTENT_TYPE_DETECTION_FAILURE");
        return problemDetail;
    }

    @ExceptionHandler(DocumentFileReadException.class)
    ProblemDetail handleDocumentFileReadFailure(DocumentFileReadException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to read file content."
        );
        problemDetail.setProperty("code", "FILE_READ_FAILURE");
        return problemDetail;
    }
}
