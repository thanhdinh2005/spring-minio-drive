package com.spring.backend.integration;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
public abstract class AbstractIntegrationTest {

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
  }

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);

    registry.add("storage.endpoint", () ->
      "http://" + rustfs.getHost() + ":" + rustfs.getMappedPort(9000));
  }
}
