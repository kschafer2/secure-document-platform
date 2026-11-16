package io.github.kschafer2.securedocumentplatform;

import io.github.kschafer2.securedocumentplatform.controller.DocumentController;
import io.github.kschafer2.securedocumentplatform.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;

import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
public class SecurityIntegrationTests {

    @Autowired
    MockMvc mockMvc;

    @Value("${test.jwt.private-key-location}")
    private Resource privateKeyResource;

    @Value("${spring.security.oauth2.resourceserver.jwt.public-key-location}")
    private Resource publicKeyResource;

    @Value("${test.jwt.invalid-private-key-location}")
    private Resource invalidPrivateKeyResource;

    @Value("${test.jwt.invalid-public-key-location}")
    private Resource invalidPublicKeyResource;

    @Test
    void validJwtToken200() throws Exception {
        JwtEncoder jwtEncoder = getJwtEncoder(publicKeyResource, privateKeyResource);

        Instant now = Instant.now();

        JwtClaimsSet jwtClaimsSet = JwtClaimsSet.builder()
                .subject("shrek")
                .audience(List.of("secure-document-platform"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();

        String jwtToken = jwtEncoder.encode(JwtEncoderParameters.from(jwtClaimsSet))
                .getTokenValue();

        mockMvc.perform(get("/documents")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
        )
                .andExpect(status().is(200))
                .andExpect(authenticated().withUsername("shrek"));
    }

    @Test
    void missingJwtToken401() throws Exception {
        mockMvc.perform(get("/documents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer ")
                )
                .andExpect(status().is(401));
    }

    @Test
    void wrongAudienceJwtToken401() throws Exception {
        JwtEncoder jwtEncoder = getJwtEncoder(publicKeyResource, privateKeyResource);

        Instant now = Instant.now();

        JwtClaimsSet jwtClaimsSet = JwtClaimsSet.builder()
                .subject("shrek")
                .audience(List.of("wrong-audience"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();

        String jwtToken = jwtEncoder.encode(JwtEncoderParameters.from(jwtClaimsSet))
                .getTokenValue();

        mockMvc.perform(get("/documents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                )
                .andExpect(status().is(401));
    }

    @Test
    void invalidSignedJwtToken401() throws Exception {
        JwtEncoder jwtEncoder = getJwtEncoder(invalidPublicKeyResource, invalidPrivateKeyResource);

        Instant now = Instant.now();

        JwtClaimsSet jwtClaimsSet = JwtClaimsSet.builder()
                .subject("shrek")
                .audience(List.of("secure-document-platform"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();

        String jwtToken = jwtEncoder.encode(JwtEncoderParameters.from(jwtClaimsSet))
                .getTokenValue();

        mockMvc.perform(get("/documents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                )
                .andExpect(status().is(401));
    }

    @Test
    void expiredJwtToken401() throws Exception {
        JwtEncoder jwtEncoder = getJwtEncoder(publicKeyResource, privateKeyResource);

        Instant now = Instant.now();

        JwtClaimsSet jwtClaimsSet = JwtClaimsSet.builder()
                .subject("shrek")
                .audience(List.of("secure-document-platform"))
                .issuedAt(now.minusSeconds(600))
                .expiresAt(now.minusSeconds(300))
                .build();

        String jwtToken = jwtEncoder.encode(JwtEncoderParameters.from(jwtClaimsSet))
                .getTokenValue();

        mockMvc.perform(get("/documents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                )
                .andExpect(status().is(401));
    }

    private JwtEncoder getJwtEncoder(Resource publicKeyResource, Resource privateKeyResource) {
        RSAPrivateKey privateKey;
        RSAPublicKey publicKey;
        JwtEncoder jwtEncoder;

        try {
            privateKey = RsaKeyConverters
                    .pkcs8()
                    .convert(privateKeyResource.getInputStream());

            publicKey = RsaKeyConverters
                    .x509()
                    .convert(publicKeyResource.getInputStream());

            jwtEncoder = NimbusJwtEncoder
                    .withKeyPair(publicKey, privateKey)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return jwtEncoder;
    }
}

