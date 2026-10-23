package io.github.kschafer2.securedocumentplatform;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

@Testcontainers
@SpringBootTest
class DocumentSchemaIntegrationTests {

    public record DocumentRow(
            UUID documentId,
            String ownerSubject,
            String fileName,
            Long fileSizeBytes,
            String contentType,
            OffsetDateTime uploadedAt
    ) {}

    private static final String postgresImage = loadPostgresImage();

    @Autowired
    private DataSource dataSource;

    @ServiceConnection
    @Container
    private static final PostgreSQLContainer postgresqlContainer = new PostgreSQLContainer(postgresImage);

    @AfterEach
    void cleanDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("TRUNCATE TABLE document")) {
            statement.executeUpdate();
        }
    }

    @Test
    void insertsValidDocument() throws SQLException {

        DocumentRow document = new DocumentRow(
                UUID.randomUUID(),
                "test_owner1",
                "file_name1.pdf",
                1024L,
                "application/pdf",
                null //not inserting, will default instead
        );

        String insertWithoutUploadedAt =
                """
                    INSERT INTO
                      document (
                        document_id,
                        owner_subject,
                        file_name,
                        file_size_bytes,
                        content_type
                      )
                    VALUES
                      (?, ?, ?, ?, ?);
            """;

        final String selectDocumentByDocumentId =
                """
                        SELECT
                          document_id,
                          owner_subject,
                          file_name,
                          file_size_bytes,
                          content_type,
                          uploaded_at
                        FROM
                          document
                        WHERE
                          document_id = ?
                """;

        OffsetDateTime beforeInsert = OffsetDateTime.now().minusSeconds(1);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(insertWithoutUploadedAt)) {
            statement.setObject(1, document.documentId());
            statement.setString(2, document.ownerSubject());
            statement.setString(3, document.fileName());
            statement.setObject(4, document.fileSizeBytes());
            statement.setString(5, document.contentType());
            int rowsInserted = statement.executeUpdate();
            Assertions.assertEquals(1, rowsInserted);
        }
        OffsetDateTime afterInsert = OffsetDateTime.now().plusSeconds(1);

        DocumentRow actual;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectDocumentByDocumentId)) {
            statement.setObject(1, document.documentId());

            try (ResultSet resultSet = statement.executeQuery()) {
                Assertions.assertTrue(resultSet.next());
                actual = new DocumentRow(
                    resultSet.getObject("document_id", UUID.class),
                    resultSet.getString("owner_subject"),
                    resultSet.getString("file_name"),
                    resultSet.getLong("file_size_bytes"),
                    resultSet.getString("content_type"),
                    resultSet.getObject("uploaded_at", OffsetDateTime.class)
                );
            }
        }

        Assertions.assertEquals(document.documentId(), actual.documentId());
        Assertions.assertEquals(document.ownerSubject(), actual.ownerSubject());
        Assertions.assertEquals(document.fileName(),actual.fileName());
        Assertions.assertEquals(document.fileSizeBytes(), actual.fileSizeBytes());
        Assertions.assertEquals(document.contentType(), actual.contentType());
        Assertions.assertNotNull(actual.uploadedAt());
        Assertions.assertTrue(actual.uploadedAt().isBefore(afterInsert));
        Assertions.assertTrue(actual.uploadedAt().isAfter(beforeInsert));
    }

    @Test
    void rejectsNullDocumentId() {

        DocumentRow document = new DocumentRow(
                null,
                "ownerSubject",
                "fileName1",
                1024L,
                "application/pdf",
                OffsetDateTime.now()
        );

        PSQLException thrown = Assertions.assertThrowsExactly(PSQLException.class, () -> insert(document));
        Assertions.assertEquals(PSQLState.NOT_NULL_VIOLATION.getState(), thrown.getSQLState());
        Assertions.assertNotNull(thrown.getServerErrorMessage());
        Assertions.assertEquals("document_id", thrown.getServerErrorMessage().getColumn());
    }

    @Test
    void rejectsDuplicateDocumentId() throws SQLException {

        DocumentRow document = new DocumentRow(
                UUID.randomUUID(),
                "Mickey Mouse",
                "fileName1",
                1024L,
                "application/pdf",
                OffsetDateTime.now()
        );

        DocumentRow document2 = new DocumentRow(
                document.documentId(),
                "Minnie Mouse",
                "fileName2",
                1024L,
                "application/pdf",
                OffsetDateTime.now()
        );

        int rowsInserted = insert(document);

        Assertions.assertEquals(1, rowsInserted);

        PSQLException thrown = Assertions.assertThrowsExactly(PSQLException.class, () -> insert(document2));
        Assertions.assertEquals(PSQLState.UNIQUE_VIOLATION.getState(), thrown.getSQLState());
        Assertions.assertNotNull(thrown.getServerErrorMessage());
        Assertions.assertEquals("document_pkey", thrown.getServerErrorMessage().getConstraint());
    }

    @Test
    void rejectsNullOwnerSubject() {

        DocumentRow document = new DocumentRow(
                UUID.randomUUID(),
                null,
                "fileName1",
                1024L,
                "application/pdf",
                OffsetDateTime.now()
        );

        PSQLException thrown = Assertions.assertThrowsExactly(PSQLException.class, () -> insert(document));
        Assertions.assertEquals(PSQLState.NOT_NULL_VIOLATION.getState(), thrown.getSQLState());
        Assertions.assertNotNull(thrown.getServerErrorMessage());
        Assertions.assertEquals("owner_subject", thrown.getServerErrorMessage().getColumn());
    }

    @Test
    void rejectsNullFileName() {

        DocumentRow document = new DocumentRow(
                UUID.randomUUID(),
                "ownerSubject",
                null,
                1024L,
                "application/pdf",
                OffsetDateTime.now()
        );

        PSQLException thrown = Assertions.assertThrowsExactly(PSQLException.class, () -> insert(document));
        Assertions.assertEquals(PSQLState.NOT_NULL_VIOLATION.getState(), thrown.getSQLState());
        Assertions.assertNotNull(thrown.getServerErrorMessage());
        Assertions.assertEquals("file_name", thrown.getServerErrorMessage().getColumn());
    }

    @Test
    void rejectsNullFileSizeBytes() {

        DocumentRow document = new DocumentRow(
                UUID.randomUUID(),
                "ownerSubject",
                "fileName",
                null,
                "application/pdf",
                OffsetDateTime.now()
        );

        PSQLException thrown = Assertions.assertThrowsExactly(PSQLException.class, () -> insert(document));
        Assertions.assertEquals(PSQLState.NOT_NULL_VIOLATION.getState(), thrown.getSQLState());
        Assertions.assertNotNull(thrown.getServerErrorMessage());
        Assertions.assertEquals("file_size_bytes", thrown.getServerErrorMessage().getColumn());
    }

    @Test
    void rejectsNullContentType() {

        DocumentRow document = new DocumentRow(
                UUID.randomUUID(),
                "ownerSubject",
                "fileName",
                1024L,
                null,
                OffsetDateTime.now()
        );

        PSQLException thrown = Assertions.assertThrowsExactly(PSQLException.class, () -> insert(document));
        Assertions.assertEquals(PSQLState.NOT_NULL_VIOLATION.getState(), thrown.getSQLState());
        Assertions.assertNotNull(thrown.getServerErrorMessage());
        Assertions.assertEquals("content_type", thrown.getServerErrorMessage().getColumn());
    }

    @Test
    void rejectsNullUploadedAt() {

        DocumentRow document = new DocumentRow(
                UUID.randomUUID(),
                "ownerSubject",
                "fileName",
                1024L,
                "application/pdf",
                null
        );

        PSQLException thrown = Assertions.assertThrowsExactly(PSQLException.class, () -> insert(document));
        Assertions.assertEquals(PSQLState.NOT_NULL_VIOLATION.getState(), thrown.getSQLState());
        Assertions.assertNotNull(thrown.getServerErrorMessage());
        Assertions.assertEquals("uploaded_at", thrown.getServerErrorMessage().getColumn());
    }

    @Test
    void rejectsDuplicateFileNameForSameOwnerSubject() throws SQLException {

        String ownerSubject = "Frank Azar";
        String fileName = "Daytona500.pdf";

        DocumentRow document = new DocumentRow(
                UUID.randomUUID(),
                ownerSubject,
                fileName,
                1024L,
                "application/pdf",
                OffsetDateTime.now()
        );

        DocumentRow document2 = new DocumentRow(
                UUID.randomUUID(),
                ownerSubject,
                fileName,
                1024L,
                "application/pdf",
                OffsetDateTime.now()
        );

        int rowsInserted = insert(document);
        Assertions.assertEquals(1, rowsInserted);

        PSQLException thrown = Assertions.assertThrowsExactly(PSQLException.class, () -> insert(document2));
        Assertions.assertEquals(PSQLState.UNIQUE_VIOLATION.getState(), thrown.getSQLState());
        Assertions.assertNotNull(thrown.getServerErrorMessage());
        Assertions.assertEquals("uq_document_owner_subject_file_name", thrown.getServerErrorMessage().getConstraint());
    }

    @Test
    void insertsDuplicateFileNameForDifferentOwnerSubject() throws SQLException {

        String ownerSubject = "George Sanderson";
        String ownerSubject2 = "James Sullivan";
        String fileName = "Daytona500.pdf";

        DocumentRow document = new DocumentRow(
                UUID.randomUUID(),
                ownerSubject,
                fileName,
                1024L,
                "application/pdf",
                OffsetDateTime.now()
        );

        DocumentRow document2 = new DocumentRow(
                UUID.randomUUID(),
                ownerSubject2,
                fileName,
                1024L,
                "application/pdf",
                OffsetDateTime.now()
        );

        int rowsInserted = insert(document);
        rowsInserted += insert(document2);

        Assertions.assertEquals(2, rowsInserted);
    }

    @Test
    void insertsCaseVariantFileNamesForSameOwner() throws SQLException {

        String ownerSubject = "George Sanderson";
        String fileName = "Daytona500.pdf";
        String fileName2 = "daytona500.pdf";


        DocumentRow document = new DocumentRow(
                UUID.randomUUID(),
                ownerSubject,
                fileName,
                1024L,
                "application/pdf",
                OffsetDateTime.now()
        );

        DocumentRow document2 = new DocumentRow(
                UUID.randomUUID(),
                ownerSubject,
                fileName2,
                1024L,
                "application/pdf",
                OffsetDateTime.now()
        );

        int rowsInserted = insert(document);
        rowsInserted += insert(document2);

        Assertions.assertEquals(2, rowsInserted);
    }

    static List<Long> inRangeFileSizeBytes() {
        return List.of(
                1L,
                104857600L
        );
    }

    @ParameterizedTest
    @MethodSource("inRangeFileSizeBytes")
    void insertsInRangeFileSizeBytes(Long fileSizeBytes) throws SQLException {

        DocumentRow document = new DocumentRow(
                UUID.randomUUID(),
                "Wilt Chamberlain",
                "file" + fileSizeBytes,
                fileSizeBytes,
                "application/pdf",
                OffsetDateTime.now()
        );

        int rowsInserted = insert(document);

        Assertions.assertEquals(1, rowsInserted);
    }

    public static List<Long> outOfRangeFileSizeBytes() {
        return List.of(
                -1L,
                0L,
                104857600L + 1
        );
    }

    @ParameterizedTest
    @MethodSource("outOfRangeFileSizeBytes")
    void rejectsOutOfRangeFileSizeBytes(Long fileSizeBytes) {

        DocumentRow document = new DocumentRow(
                UUID.randomUUID(),
                "Pat Sajak",
                "fileName",
                fileSizeBytes,
                "application/pdf",
                OffsetDateTime.now()
        );

        PSQLException thrown = Assertions.assertThrowsExactly(PSQLException.class, () -> insert(document));
        Assertions.assertEquals(PSQLState.CHECK_VIOLATION.getState(), thrown.getSQLState());
        Assertions.assertNotNull(thrown.getServerErrorMessage());
        Assertions.assertEquals("chk_document_file_size_bytes", thrown.getServerErrorMessage().getConstraint());
    }

    static List<String> allowedContentTypes() {
        return List.of(
                "application/pdf",
                "text/plain",
                "image/jpeg",
                "image/png"
        );
    }

    @ParameterizedTest
    @MethodSource("allowedContentTypes")
    void insertsAllowedContentTypes(String contentType) throws SQLException {

        DocumentRow document = new DocumentRow(
                UUID.randomUUID(),
                "Mike Wazowski",
                "fileName" + contentType.replaceAll("/",""),
                1024L,
                contentType,
                OffsetDateTime.now()
        );

        int insertedRows = insert(document);

        Assertions.assertEquals(1, insertedRows);
    }

    static List<String> disallowedContentTypes() {
        return List.of(
                "",
                " ",
                "content/unknown",
                "unknown/unknown",
                "application/octet-stream",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "image/gif",
                "text/html",
                "application/java-archive",
                "application/json",
                "text/markdown",
                "application/vnd.ms-powerpoint",
                "image/tiff",
                "audio/wav",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/zip"
        );
    }

    @ParameterizedTest
    @MethodSource("disallowedContentTypes")
    void rejectsDisallowedContentTypes(String contentType) {

        DocumentRow document = new DocumentRow(
                UUID.randomUUID(),
                "Leeroy Jenkins",
                "fileName",
                1024L,
                contentType,
                OffsetDateTime.now()
        );

        PSQLException thrown = Assertions.assertThrowsExactly(PSQLException.class, () -> insert(document));
        Assertions.assertEquals(PSQLState.CHECK_VIOLATION.getState(), thrown.getSQLState());
        Assertions.assertNotNull(thrown.getServerErrorMessage());
        Assertions.assertEquals("chk_document_content_type", thrown.getServerErrorMessage().getConstraint());
    }

    private int insert(DocumentSchemaIntegrationTests.DocumentRow document) throws SQLException {
        final String insertDocumentRow =
                """
                        INSERT INTO
                          document (
                            document_id,
                            owner_subject,
                            file_name,
                            file_size_bytes,
                            content_type,
                            uploaded_at
                          )
                        VALUES
                          (?, ?, ?, ?, ?, ?);
                """;

        int rowsInserted;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(insertDocumentRow)) {
            statement.setObject(1, document.documentId());
            statement.setString(2, document.ownerSubject());
            statement.setString(3, document.fileName());
            statement.setObject(4, document.fileSizeBytes());
            statement.setString(5, document.contentType());
            statement.setObject(6, document.uploadedAt());
            rowsInserted = statement.executeUpdate();
        }
        return rowsInserted;
    }

    private static String loadPostgresImage() {
        Properties properties = new Properties();

        try (InputStream input =
                     Files.newInputStream(Path.of(".env.example"))) {
            properties.load(input);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to load .env.example", e);
        }

        String image = properties.getProperty("POSTGRES_IMAGE");

        if (image == null || image.isBlank()) {
            throw new IllegalStateException(
                    "POSTGRES_IMAGE is missing from .env.example");
        }

        return image;
    }
}