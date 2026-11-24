package io.github.kschafer2.securedocumentplatform.application.upload;

import java.io.IOException;
import java.io.InputStream;

public interface UploadContent {

    String name();

    long size();

    /**
     * Opens a new stream over the complete content.
     * Each invocation must return a stream positioned at the beginning.
     * The caller is responsible for closing the returned stream.
     */
    InputStream openStream() throws IOException;
}
