package io.github.kschafer2.securedocumentplatform.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "storage.seaweedfs")
public record SeaweedFsProperties(
        URI endpoint,
        String bucket,
        String accessKey,
        String secretKey,
        String region
) {

    @Override
    public String toString() {
        return "SeaweedFsProperties[" +
                "endpoint=" + endpoint +
                ", bucket=" + bucket +
                ", accessKey=[REDACTED]" +
                ", secretKey=[REDACTED]" +
                ", region=" + region +
                ']';
    }
}