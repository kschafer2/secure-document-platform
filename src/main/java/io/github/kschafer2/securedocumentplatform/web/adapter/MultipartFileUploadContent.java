package io.github.kschafer2.securedocumentplatform.web.adapter;

import io.github.kschafer2.securedocumentplatform.application.upload.UploadContent;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

public class MultipartFileUploadContent implements UploadContent {

    private final MultipartFile file;

    public MultipartFileUploadContent(MultipartFile file) {
        this.file = file;
    }

    @Override
    public String name() {
        return file.getOriginalFilename();
    }

    @Override
    public long size() {
        return file.getSize();
    }

    @Override
    public InputStream openStream() throws IOException {
        return file.getInputStream();
    }
}
