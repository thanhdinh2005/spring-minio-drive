package com.spring.backend;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.spring.backend.entity.User;
import com.spring.backend.entity.enums.Plan;
import com.spring.backend.entity.enums.Role;
import com.spring.backend.entity.enums.Status;
import com.spring.backend.repository.UserRepository;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base cho integration test folder/document.
 *
 * Kiến trúc: test HTTP end-to-end (MockMvc + JWT thật) chạy vào infra local
 * (postgres + rustfs do docker compose dựng sẵn), KHÔNG dùng Testcontainers
 * (docker-java không nói chuyện được với Docker Desktop trong môi trường này).
 *
 * Chuẩn bị tay trước khi chạy (xem README mục Integration Test):
 * - Postgres local đã có database {@code app_test} (Flyway tự migrate V1-V7)
 * - RustFS local đã có bucket {@code it-bucket}
 * - Export TEST_DB_PASSWORD / TEST_STORAGE_ACCESS_KEY / TEST_STORAGE_SECRET_KEY
 *   theo đúng giá trị trong file .env ở root.
 *
 * Lưu ý: MockMvc bỏ qua server.servlet.context-path (/api) nên request trong test
 * dùng path trần: /folders, /documents, /auth/login.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public abstract class AbstractIntegrationTest {

  protected static final String TEST_BUCKET = "it-bucket";

  /**
   * Trỏ datasource + storage sang infra local. DynamicPropertySource có độ ưu tiên
   * cao nhất nên thắng mọi giá trị trong application.yaml / application-dev.yaml.
   */
  @DynamicPropertySource
  static void localInfraProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () ->
      "jdbc:postgresql://" + env("TEST_DB_HOST", "localhost")
        + ":" + env("TEST_DB_PORT", "5432")
        + "/" + env("TEST_DB_NAME", "app_test"));
    registry.add("spring.datasource.username", () -> env("TEST_DB_USERNAME", "postgres"));
    registry.add("spring.datasource.password", () -> env("TEST_DB_PASSWORD", ""));
    registry.add("storage.endpoint", () -> env("TEST_STORAGE_ENDPOINT", "http://localhost:9000"));
    registry.add("storage.bucket", () -> TEST_BUCKET);
    registry.add("storage.region", () -> "us-east-1");
    registry.add("storage.access-key", () -> env("TEST_STORAGE_ACCESS_KEY", ""));
    registry.add("storage.secret-key", () -> env("TEST_STORAGE_SECRET_KEY", ""));
  }

  private static String env(String name, String defaultValue) {
    String value = System.getenv(name);
    return value == null ? defaultValue : value;
  }

  @Autowired
  protected MockMvc mockMvc;

  @Autowired
  protected ObjectMapper objectMapper;

  @Autowired
  protected UserRepository userRepository;

  @Autowired
  protected PasswordEncoder passwordEncoder;

  @Autowired
  protected S3Client s3Client;

  /**
   * Đảm bảo bucket test tồn tại (tạo nếu thiếu). Bước tạo tay trong README vẫn là
   * cách chính thống; đây chỉ là lưới an toàn để test không fail vì quên bước đó.
   */
  @org.junit.jupiter.api.BeforeEach
  void ensureTestBucketExists() {
    try {
      s3Client.headBucket(HeadBucketRequest.builder().bucket(TEST_BUCKET).build());
    } catch (Exception headFailed) {
      try {
        s3Client.createBucket(CreateBucketRequest.builder().bucket(TEST_BUCKET).build());
      } catch (Exception ignored) {
        // đã tồn tại (race) hoặc thiếu quyền — để test báo lỗi rõ ở bước gọi S3
      }
    }
  }

  protected static final String PASSWORD = "Secret123";

  /** Email ngẫu nhiên, ≤ 20 ký tự theo đúng @Size của LoginRequest. */
  protected String uniqueEmail() {
    return "it-" + UUID.randomUUID().toString().substring(0, 8) + "@t.co";
  }

  /** Tạo user trực tiếp qua DB (không có endpoint register), trả về email. */
  protected String createUser() {
    String email = uniqueEmail();
    userRepository.save(User.builder()
      .email(email)
      .passwordHash(passwordEncoder.encode(PASSWORD))
      .displayName("IT User")
      .role(Role.USER)
      .status(Status.ACTIVE)
      .plan(Plan.FREE)
      .build());
    return email;
  }

  /** Login thật qua POST /auth/login, trả về accessToken (JWT thật). */
  protected String login(String email) throws Exception {
    String body = objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD));
    MvcResult result = mockMvc.perform(post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isOk())
      .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.success").value(true))
      .andReturn();
    JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
    return root.get("data").get("accessToken").asText();
  }

  /** Tạo user + login, trả về token sẵn dùng cho header Authorization. */
  protected String newUserToken() throws Exception {
    return login(createUser());
  }

  protected String bearer(String token) {
    return "Bearer " + token;
  }

  /** Tạo folder qua API, trả về id. */
  protected UUID createFolder(String token, String name, UUID parentFolderId) throws Exception {
    Map<String, Object> req = new java.util.HashMap<>();
    req.put("name", name);
    req.put("parentFolderId", parentFolderId);
    MvcResult result = mockMvc.perform(post("/folders")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(req)))
      .andExpect(status().isCreated())
      .andReturn();
    JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
    return UUID.fromString(root.get("data").get("id").asText());
  }
}
