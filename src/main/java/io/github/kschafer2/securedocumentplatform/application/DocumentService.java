package io.github.kschafer2.securedocumentplatform.application;

import io.github.kschafer2.securedocumentplatform.application.upload.UploadContent;

import java.util.UUID;

public interface DocumentService {

    UUID upload(String ownerSubject, UploadContent uploadContent);
}