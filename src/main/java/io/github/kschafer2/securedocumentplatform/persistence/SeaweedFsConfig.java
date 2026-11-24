package io.github.kschafer2.securedocumentplatform.persistence;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(SeaweedFsProperties.class)
public class SeaweedFsConfig {

    private final SeaweedFsProperties seaweedFsProperties;

    public SeaweedFsConfig(SeaweedFsProperties seaweedFsProperties) {
        this.seaweedFsProperties = seaweedFsProperties;
    }

    @Bean
    public SeaweedFsDocumentObjectStorage seaweedFsDocumentObjectStorage() {
        S3Client s3Client = S3Client.builder()
                .endpointOverride(seaweedFsProperties.endpoint())
                .region(Region.of(seaweedFsProperties.region()))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(
                                        seaweedFsProperties.accessKey(),
                                        seaweedFsProperties.secretKey()
                                )
                        )
                )
                .forcePathStyle(true)
                .build();

        return new SeaweedFsDocumentObjectStorage(s3Client, seaweedFsProperties.bucket());
    }
}
