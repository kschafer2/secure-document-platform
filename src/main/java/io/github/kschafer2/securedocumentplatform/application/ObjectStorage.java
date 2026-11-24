package io.github.kschafer2.securedocumentplatform.application;

import java.io.InputStream;
import java.util.UUID;

public interface ObjectStorage {

    void store(UUID documentId, InputStream contentStream, long contentLength);
}
