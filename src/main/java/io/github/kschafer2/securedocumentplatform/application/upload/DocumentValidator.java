package io.github.kschafer2.securedocumentplatform.application.upload;

import io.github.kschafer2.securedocumentplatform.application.upload.exception.UnsupportedFileContentTypeException;
import io.github.kschafer2.securedocumentplatform.application.upload.exception.EmptyFileException;
import io.github.kschafer2.securedocumentplatform.application.upload.exception.InvalidDocumentFileNameException;
import io.github.kschafer2.securedocumentplatform.application.upload.exception.TooLargeFileException;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class DocumentValidator {

    private final static long MAX_FILE_SIZE_BYTES = 104857600L;

    private final static int MAX_FILE_NAME_LENGTH = 255;

    private final static Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "text/plain",
            "image/jpeg",
            "image/png"
    );

    public void validateFileSize(long fileSizeBytes) {
        if(fileSizeBytes <= 0) {
            throw new EmptyFileException("File is empty. fileSizeBytes: " + fileSizeBytes);
        } else if(fileSizeBytes > MAX_FILE_SIZE_BYTES) {
            throw new TooLargeFileException("File is too large. fileSizeBytes: " + fileSizeBytes);
        }
    }

    public void validateFileName(String fileName) {
        if(
                fileName == null ||
                fileName.isBlank() ||
                fileName.length() > MAX_FILE_NAME_LENGTH ||
                fileName.contains("\\") ||
                fileName.contains("/") ||
                fileName.equals(".") ||
                fileName.equals("..") ||
                containsControlCharacter(fileName)
        ) {
            throw new InvalidDocumentFileNameException("Invalid filename: " + fileName);
        }
    }

    private boolean containsControlCharacter(String fileName) {
        return fileName.chars().anyMatch(Character::isISOControl);
    }

    public void validateContentType(String contentType) {
        if(!ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new UnsupportedFileContentTypeException("Unsupported content type: " + contentType);
        }
    }
}
