package io.github.kschafer2.securedocumentplatform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestConfig.class)
@ActiveProfiles("test")
class SecureDocumentPlatformApplicationTests {

	@Test
	void contextLoads() {
	}

}
