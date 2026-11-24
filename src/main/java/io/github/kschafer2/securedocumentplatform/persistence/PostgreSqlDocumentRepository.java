package io.github.kschafer2.securedocumentplatform.persistence;

import io.github.kschafer2.securedocumentplatform.application.DocumentRepository;
import io.github.kschafer2.securedocumentplatform.application.upload.NewDocument;
import io.github.kschafer2.securedocumentplatform.application.upload.exception.DocumentNameConflictException;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class PostgreSqlDocumentRepository implements DocumentRepository {

    private static final String DOCUMENT_NAME_CONSTRAINT = "uq_document_owner_subject_file_name";

    private final JdbcClient jdbcClient;

    public PostgreSqlDocumentRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void save(NewDocument document) {
        String sql =
                """
                        INSERT INTO
                          DOCUMENT (
                            document_id,
                            owner_subject,
                            file_name,
                            file_size_bytes,
                            content_type
                          )
                        VALUES
                          (
                        :documentId,
                        :ownerSubject,
                        :fileName,
                        :fileSizeBytes,
                        :contentType
                          );
                """;

        try {
            jdbcClient.sql(sql)
                    .paramSource(document)
                    .update();
        } catch(DuplicateKeyException e) {
            Throwable cause = e.getMostSpecificCause();

            if(
                    cause instanceof PSQLException postgresException &&
                    PSQLState.UNIQUE_VIOLATION.getState().equals(postgresException.getSQLState()) && 
                    postgresException.getServerErrorMessage() != null &&
                    DOCUMENT_NAME_CONSTRAINT.equals(postgresException.getServerErrorMessage().getConstraint())
            ) {
                throw new DocumentNameConflictException(document.ownerSubject(), document.fileName(), e);
            }
            throw e;
        }
    }

    @Override
    public boolean existsByOwnerSubjectAndFileName(String ownerSubject, String fileName) {
        String sql =
                """
                        SELECT
                          EXISTS (
                            SELECT
                              1
                            FROM
                              document
                            WHERE
                              owner_subject = :ownerSubject
                              AND file_name = :fileName
                          );
                """;

        MapSqlParameterSource mapSqlParameterSource = new MapSqlParameterSource()
                .addValue("ownerSubject", ownerSubject)
                .addValue("fileName", fileName);

        return jdbcClient.sql(sql)
                .paramSource(mapSqlParameterSource)
                .query(Boolean.class)
                .single();
    }

    @Override
    public void deleteByDocumentIdAndOwnerSubject(UUID documentId, String ownerSubject) {
        String sql =
                """
                        DELETE FROM document
                        WHERE
                          document_id = :documentId
                          AND owner_subject = :ownerSubject;
                """;

        MapSqlParameterSource mapSqlParameterSource = new MapSqlParameterSource()
                .addValue("documentId", documentId)
                .addValue("ownerSubject", ownerSubject);

        jdbcClient.sql(sql)
                .paramSource(mapSqlParameterSource)
                .update();
    }
}
