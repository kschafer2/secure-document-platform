package io.github.kschafer2.securedocumentplatform.application;

import io.github.kschafer2.securedocumentplatform.application.upload.NewDocument;
import io.github.kschafer2.securedocumentplatform.application.upload.UploadContent;
import io.github.kschafer2.securedocumentplatform.application.upload.exception.DocumentFileReadException;
import io.github.kschafer2.securedocumentplatform.application.upload.exception.DocumentNameConflictException;
import io.github.kschafer2.securedocumentplatform.application.upload.DocumentIdGenerator;
import io.github.kschafer2.securedocumentplatform.application.upload.DocumentValidator;
import io.github.kschafer2.securedocumentplatform.application.upload.ContentTypeDetector;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Service
public class DefaultDocumentService implements DocumentService {

    private final DocumentValidator documentValidator;
    private final ContentTypeDetector contentTypeDetector;
    private final DocumentIdGenerator documentIdGenerator;
    private final DocumentRepository documentRepository;

    public DefaultDocumentService(DocumentValidator documentValidator,
                                  ContentTypeDetector contentTypeDetector,
                                  DocumentIdGenerator documentIdGenerator,
                                  DocumentRepository documentRepository) {
        this.documentValidator = documentValidator;
        this.contentTypeDetector = contentTypeDetector;
        this.documentIdGenerator = documentIdGenerator;
        this.documentRepository = documentRepository;
    }

    @Override
    public UUID upload(String ownerSubject, UploadContent uploadContent) {
        long fileSize = uploadContent.fileSizeBytes();
        String fileName = uploadContent.fileName();
        String contentType;

        documentValidator.validateFileSize(fileSize);
        documentValidator.validateFileName(fileName);

        try(InputStream contentStream = uploadContent.openStream()) {
            contentType = contentTypeDetector.detect(contentStream);
        } catch (IOException e) {
            throw new DocumentFileReadException("File content could not be read.", e);
        }
        documentValidator.validateContentType(contentType);

        if(documentRepository.existsByOwnerSubjectAndFileName(ownerSubject, fileName)) {
            throw new DocumentNameConflictException(ownerSubject, fileName);
        }
        UUID documentId = documentIdGenerator.generate();

        NewDocument document = new NewDocument(
                documentId,
                ownerSubject,
                fileName,
                fileSize,
                contentType
        );
        documentRepository.saveDocument(document);

        return documentId;
    }
}
