CREATE TABLE document (
    document_id UUID PRIMARY KEY,
    owner_subject VARCHAR(255) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_document_owner_subject_file_name
        UNIQUE (owner_subject, file_name),

    CONSTRAINT chk_document_file_size_bytes
        CHECK (file_size_bytes > 0 AND file_size_bytes <= 104857600),

    CONSTRAINT chk_document_content_type
        CHECK (content_type IN (
            'application/pdf',
            'text/plain',
            'image/jpeg',
            'image/png'
        ))
);

CREATE INDEX idx_document_owner_subject_uploaded_at
    ON document (owner_subject, uploaded_at);