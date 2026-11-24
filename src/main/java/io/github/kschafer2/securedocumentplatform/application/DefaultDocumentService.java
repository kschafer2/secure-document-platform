package io.github.kschafer2.securedocumentplatform.application;

import io.github.kschafer2.securedocumentplatform.application.upload.NewDocument;
import io.github.kschafer2.securedocumentplatform.application.upload.UploadContent;
import io.github.kschafer2.securedocumentplatform.application.upload.exception.DocumentFileReadException;
import io.github.kschafer2.securedocumentplatform.application.upload.exception.DocumentNameConflictException;
import io.github.kschafer2.securedocumentplatform.application.upload.DocumentIdGenerator;
import io.github.kschafer2.securedocumentplatform.application.upload.DocumentValidator;
import io.github.kschafer2.securedocumentplatform.application.upload.ContentTypeDetector;
import io.github.kschafer2.securedocumentplatform.application.upload.exception.ObjectStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Service
public class DefaultDocumentService implements DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DefaultDocumentService.class);

    private final DocumentValidator documentValidator;
    private final ContentTypeDetector contentTypeDetector;
    private final DocumentIdGenerator documentIdGenerator;
    private final DocumentRepository documentRepository;
    private final ObjectStorage objectStorage;

    public DefaultDocumentService(DocumentValidator documentValidator,
                                  ContentTypeDetector contentTypeDetector,
                                  DocumentIdGenerator documentIdGenerator,
                                  DocumentRepository documentRepository,
                                  ObjectStorage objectStorage) {
        this.documentValidator = documentValidator;
        this.contentTypeDetector = contentTypeDetector;
        this.documentIdGenerator = documentIdGenerator;
        this.documentRepository = documentRepository;
        this.objectStorage = objectStorage;
    }

    @Override
    public UUID upload(String ownerSubject, UploadContent uploadContent) {
        long fileSize = uploadContent.size();
        String fileName = uploadContent.name();
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
        documentRepository.save(document);

        try(InputStream contentStream = uploadContent.openStream()) {
            objectStorage.store(documentId, contentStream, fileSize);
        } catch (IOException | ObjectStorageException e) {
            try {
                documentRepository.deleteByDocumentIdAndOwnerSubject(documentId, ownerSubject);
            } catch (Exception e2) {
                log.error(
                        "Failed to compensate document upload. documentId: {}, ownerSubject: {}",
                        documentId, ownerSubject, e2
                );
                e.addSuppressed(e2);
            }
            if(e instanceof IOException) {
                throw new DocumentFileReadException("File content could not be read.", e);
            }
            throw (ObjectStorageException) e;
        }
        return documentId;
    }
}
