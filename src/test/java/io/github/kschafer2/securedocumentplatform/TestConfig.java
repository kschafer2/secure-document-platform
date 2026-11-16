package io.github.kschafer2.securedocumentplatform;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer(@Value("${test.docker.postgres-image}") String postgresImage) {
        return new PostgreSQLContainer(postgresImage);
    }
}
