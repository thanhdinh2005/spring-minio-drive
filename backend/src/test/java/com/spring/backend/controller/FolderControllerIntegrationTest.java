package com.spring.backend.controller;

import com.spring.backend.dto.auth.LoginRequest;
import com.spring.backend.dto.auth.TokenResponse;
import com.spring.backend.dto.folder.FolderCreateRequest;
import com.spring.backend.entity.User;
import com.spring.backend.entity.enums.Plan;
import com.spring.backend.entity.enums.Role;
import com.spring.backend.entity.enums.Status;
import com.spring.backend.repository.FolderRepository;
import com.spring.backend.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.backend.common.AppResponse;
import com.spring.backend.TestBackendApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = TestBackendApplication.class
)
@Testcontainers
@DisplayName("Integration test: FolderController.createFolder")
class FolderControllerIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

  @DynamicPropertySource
  static void registerPgProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private FolderRepository folderRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private ObjectMapper objectMapper;
  private String accessToken;
  private UUID userId;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();

    // Tạo user thật trong DB
    User user = User.builder()
        .email("integration@email.com")
        .passwordHash(passwordEncoder.encode("Password123"))
        .displayName("Integration Test User")
        .status(Status.ACTIVE)
        .role(Role.USER)
        .plan(Plan.FREE)
        .build();
    user = userRepository.save(user);
    userId = user.getId();

    // Đăng nhập để lấy JWT
    LoginRequest loginRequest = LoginRequest.builder()
        .email("integration@email.com")
        .password("Password123")
        .build();

    ResponseEntity<AppResponse<TokenResponse>> loginResp =
        restTemplate.postForEntity("/auth/login", loginRequest,
            new ParameterizedTypeReference<>() {});
    assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(loginResp.getBody()).isNotNull();
    assertThat(loginResp.getBody().getData()).isNotNull();
    accessToken = loginResp.getBody().getData().getAccessToken();
  }

  @Test
  @DisplayName("should return 201 and created folder when valid request with JWT")
  void shouldReturnCreated_whenValidRequestWithJwt() throws Exception {
    // Given
    FolderCreateRequest request = FolderCreateRequest.builder()
        .name("Tài liệu dự án")
        .parentFolderId(null)
        .build();

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    headers.setContentType(MediaType.APPLICATION_JSON);

    HttpEntity<String> entity = new HttpEntity<>(
        objectMapper.writeValueAsString(request), headers);

    // When
    ResponseEntity<AppResponse<FolderDtoResponse>> response =
        restTemplate.exchange("/api/folders", HttpMethod.POST, entity,
            new ParameterizedTypeReference<>() {});

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().isSuccess()).isTrue();
    assertThat(response.getBody().getData()).isNotNull();
    assertThat(response.getBody().getData().name()).isEqualTo("Tài liệu dự án");
    assertThat(response.getBody().getData().ownerId()).isEqualTo(userId.toString());
    assertThat(response.getBody().getData().parentFolderId()).isNull();

    // Verify persisted in DB
    assertThat(folderRepository.findAll()).isNotEmpty();
  }

  @Test
  @DisplayName("should return 404 when parentFolderId does not exist")
  void shouldReturnNotFound_whenParentFolderIdDoesNotExist() throws Exception {
    // Given
    FolderCreateRequest request = FolderCreateRequest.builder()
        .name("Con 1")
        .parentFolderId(UUID.randomUUID())
        .build();

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    headers.setContentType(MediaType.APPLICATION_JSON);

    HttpEntity<String> entity = new HttpEntity<>(
        objectMapper.writeValueAsString(request), headers);

    // When
    ResponseEntity<AppResponse<Void>> response =
        restTemplate.exchange("/api/folders", HttpMethod.POST, entity,
            new ParameterizedTypeReference<>() {});

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().isSuccess()).isFalse();
    assertThat(response.getBody().getCode()).isEqualTo("ERR_FOLDER_001");
  }

  // ===== Record dùng cho response mapping (không liên quan entity) =====
  public record FolderDtoResponse(
      UUID id,
      String name,
      String ownerId,
      String parentFolderId,
      Instant createdAt,
      Instant updatedAt
  ) {
  }
}
