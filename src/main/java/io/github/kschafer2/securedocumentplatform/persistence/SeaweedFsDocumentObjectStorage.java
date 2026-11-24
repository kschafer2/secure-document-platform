package io.github.kschafer2.securedocumentplatform.persistence;

import io.github.kschafer2.securedocumentplatform.application.ObjectStorage;
import io.github.kschafer2.securedocumentplatform.application.upload.exception.ObjectStorageException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.util.UUID;

public class SeaweedFsDocumentObjectStorage implements ObjectStorage, AutoCloseable {

    private final S3Client s3Client;
    private final String bucket;

    public SeaweedFsDocumentObjectStorage(S3Client s3Client, String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    @Override
    public void store(UUID documentId, InputStream contentStream, long contentLength) {
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(documentId.toString())
                            .build(),
                    RequestBody.fromInputStream(contentStream, contentLength)
            );
        } catch (SdkException e) {
            throw new ObjectStorageException("Failed to store document content for documentId: " + documentId, e);
        }
    }

    @Override
    public void close() {
        s3Client.close();
    }
}
