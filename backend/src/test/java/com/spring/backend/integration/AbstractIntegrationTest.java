package com.spring.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import java.time.Duration;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
public abstract class AbstractIntegrationTest {

  /** Must match storage.bucket in application-test.yaml. */
  protected static final String BUCKET = "test-bucket";

  static final PostgreSQLContainer<?> postgres =
    new PostgreSQLContainer<>("postgres:17");

  static final GenericContainer<?> rustfs =
    new GenericContainer<>(DockerImageName.parse("rustfs/rustfs:1.0.0"))
      .withExposedPorts(9000, 9001)
      .withEnv("RUSTFS_ACCESS_KEY", "test-access-key")
      .withEnv("RUSTFS_SECRET_KEY", "test-secret-key")
      .waitingFor(Wait.forListeningPort()
        .withStartupTimeout(Duration.ofSeconds(60)));

  static {
    postgres.start();
    rustfs.start();
    createBucketIfMissing();
  }

  /**
   * S3-compatible storage does not create buckets automatically (unlike PostgreSQL
   * creating its schema), so the integration tests have to provision it themselves.
   */
  private static void createBucketIfMissing() {
    try (S3Client s3 = S3Client.builder()
      .endpointOverride(URI.create(
        "http://" + rustfs.getHost() + ":" + rustfs.getMappedPort(9000)))
      .region(Region.of("test-region"))
      .credentialsProvider(StaticCredentialsProvider.create(
        AwsBasicCredentials.create("test-access-key", "test-secret-key")))
      .forcePathStyle(true)
      .build()) {
      try {
        s3.createBucket(b -> b.bucket(BUCKET));
      } catch (S3Exception e) {
        // 409 = bucket already exists/owned by us (previous run on a reused container)
        if (e.statusCode() != 409) {
          throw e;
        }
      }
    }
  }

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);

    registry.add("storage.endpoint", () ->
      "http://" + rustfs.getHost() + ":" + rustfs.getMappedPort(9000));
  }

  @Configuration
  static class TestConfig {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
