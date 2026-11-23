package io.github.kschafer2.securedocumentplatform.application.upload;

import io.github.kschafer2.securedocumentplatform.application.upload.exception.ContentTypeDetectionException;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class ContentTypeDetector {

    private final Tika tika = new Tika();

    public String detect(InputStream contentStream) {
        try {
            return tika.detect(contentStream);
        } catch (IOException e) {
            throw new ContentTypeDetectionException("Failed to detect content type", e);
        }
    }
}
