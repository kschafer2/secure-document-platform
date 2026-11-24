package io.github.kschafer2.securedocumentplatform.web.error;

import io.github.kschafer2.securedocumentplatform.application.upload.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DocumentNameConflictException.class)
    ProblemDetail handleDocumentNameConflict(HttpServletRequest request) {
        logDebug("Document name conflict.", request);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "A document with this filename already exists."
        );
        problemDetail.setProperty("code", "DOCUMENT_NAME_CONFLICT");
        return problemDetail;
    }

    @ExceptionHandler(EmptyFileException.class)
    ProblemDetail handleEmptyFile(HttpServletRequest request) {
        logDebug("Empty file.", request);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "The document file is empty."
        );
        problemDetail.setProperty("code", "EMPTY_FILE");
        return problemDetail;
    }

    @ExceptionHandler(TooLargeFileException.class)
    ProblemDetail handleOversizedFile(HttpServletRequest request) {
        logDebug("Oversized file.", request);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONTENT_TOO_LARGE,
                "The document file is too large."
        );
        problemDetail.setProperty("code", "FILE_TOO_LARGE");
        return problemDetail;
    }

    @ExceptionHandler(UnsupportedFileContentTypeException.class)
    ProblemDetail handleUnsupportedFileContentType(HttpServletRequest request) {
        logDebug("Unsupported file content type.", request);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "File content type is not supported."
        );
        problemDetail.setProperty("code", "UNSUPPORTED_FILE_TYPE");
        return problemDetail;
    }

    @ExceptionHandler(InvalidDocumentFileNameException.class)
    ProblemDetail handleInvalidDocumentFileName(HttpServletRequest request) {
        logDebug("Invalid document file name.", request);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "The given filename is invalid."
        );
        problemDetail.setProperty("code", "INVALID_FILE_NAME");
        return problemDetail;
    }

    @ExceptionHandler(ContentTypeDetectionException.class)
    ProblemDetail handleContentTypeDetectionFailure(ContentTypeDetectionException e, HttpServletRequest request) {
        logError("Content type detection failure.", request, e);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to detect file content type."
        );
        problemDetail.setProperty("code", "CONTENT_TYPE_DETECTION_FAILURE");
        return problemDetail;
    }

    @ExceptionHandler(DocumentFileReadException.class)
    ProblemDetail handleDocumentFileReadFailure(DocumentFileReadException e, HttpServletRequest request) {
        logError("Document file read failure.", request, e);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to read file content."
        );
        problemDetail.setProperty("code", "FILE_READ_FAILURE");
        return problemDetail;
    }

    @ExceptionHandler(ObjectStorageException.class)
    ProblemDetail handleObjectStorageFailure(ObjectStorageException e, HttpServletRequest request) {
        logError("Object storage failure.", request, e);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Object storage is unavailable."
        );
        problemDetail.setProperty("code", "OBJECT_STORAGE_UNAVAILABLE");
        return problemDetail;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpectedError(Exception e, HttpServletRequest request) {
        logError("Unexpected error.", request, e);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred."
        );
        problemDetail.setProperty("code", "SYSTEM_ERROR");
        return problemDetail;
    }

    private static void logDebug(String message, HttpServletRequest request) {
        log.debug("{} {} {}", message, request.getMethod(), request.getRequestURI());
    }

    private static void logError(String message, HttpServletRequest request, Exception e) {
        log.error("{} {} {}", message, request.getMethod(), request.getRequestURI(), e);
    }
}
