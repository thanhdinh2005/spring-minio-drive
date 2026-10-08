package com.spring.backend.controller;

import com.spring.backend.BaseIntegrationTest;
import com.spring.backend.entity.User;
import com.spring.backend.entity.enums.Plan;
import com.spring.backend.entity.enums.Role;
import com.spring.backend.entity.enums.Status;
import com.spring.backend.repository.UserRepository;
import com.spring.backend.service.impl.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("UserController Integration Test - GET /api/users/me")
class UserControllerTest extends BaseIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private JwtService jwtService;

  private User testUser;
  private String validToken;

  @BeforeEach
  void setUp() {
    // 1. Tạo và lưu user thật vào Database
    User user = User.builder()
        .email("test.integration@example.com")
        .passwordHash("$2a$10$dummyHashedPassword")
        .displayName("Nguyen Van Integration")
        .role(Role.USER)
        .status(Status.ACTIVE)
        .plan(Plan.FREE)
        .storageUsed(0L)
        .storageLimit(104857600L)
        .build();
    testUser = userRepository.save(user);

    // 2. Tạo JWT Token thật tương ứng với user trên
    validToken = jwtService.generateAccessToken(testUser);
  }

  @Nested
  @DisplayName("Khi có JWT Token hợp lệ")
  class ValidTokenTests {

    @Test
    @DisplayName("Trả về 200 OK và đúng thông tin User hiện tại")
    void getMyProfile_Success() throws Exception {
      mockMvc.perform(get("/users/me")
          .header("Authorization", "Bearer " + validToken)
          .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success").value(true))
          .andExpect(jsonPath("$.data.id").value(testUser.getId().toString()))
          .andExpect(jsonPath("$.data.email").value("test.integration@example.com"))
          .andExpect(jsonPath("$.data.displayName").value("Nguyen Van Integration"))
          .andExpect(jsonPath("$.data.role").value("USER"))
          .andExpect(jsonPath("$.data.plan").value("FREE"));
    }
  }

  @Nested
  @DisplayName("Khi không có JWT Token hoặc Token không hợp lệ")
  class InvalidTokenTests {

    @Test
    @DisplayName("Không truyền Authorization header -> Trả về 403 Forbidden")
    void getMyProfile_MissingToken_ShouldReturnForbidden() throws Exception {
      mockMvc.perform(get("/users/me")
          .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Truyền Token sai format/hết hạn -> Trả về 403 Forbidden")
    void getMyProfile_InvalidToken_ShouldReturnForbidden() throws Exception {
      mockMvc.perform(get("/users/me")
          .header("Authorization", "Bearer invalid.jwt.token")
          .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isForbidden());
    }
  }
}
