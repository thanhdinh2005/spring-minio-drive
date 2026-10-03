package com.spring.backend.integration.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.backend.dto.auth.LoginRequest;
import com.spring.backend.entity.User;
import com.spring.backend.entity.enums.Plan;
import com.spring.backend.entity.enums.Role;
import com.spring.backend.entity.enums.Status;
import com.spring.backend.integration.AbstractIntegrationTest;
import com.spring.backend.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
Class này extend setup Testcontainer từ abstract class do đó phần db và rustfs đã có sẵn
Yêu cầu chạy docker desktop trước, lần đầu chạy sẽ khá lâu nếu máy chưa có sẵn image của
rustfs và postgreSQL
 */
class AuthIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  MockMvc mockMvc;

  @Autowired
  ObjectMapper objectMapper;

  @Nested
  @DisplayName("Login Integration Test")
  @Transactional
  class LoginIntegrationTest {

    @Autowired
    private UserRepository repository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private LoginRequest validRequest;
    private LoginRequest invalidRequest;

    @BeforeEach
    void setUp() {
      User user = User.builder()
        .email("test@email.com")
        .passwordHash(passwordEncoder.encode("password123"))
        .displayName("Test user")
        .role(Role.USER)
        .status(Status.ACTIVE)
        .plan(Plan.FREE)
        .storageUsed(0L)
        .storageLimit(104857600L)
        .build();

      repository.save(user);

      validRequest = LoginRequest.builder()
        .email("test@email.com")
        .password("password123")
        .build();

      invalidRequest = LoginRequest.builder()
        .email("invalid")
        .password("wrongpassword")
        .build();
    }

    @AfterEach
    void tearDown() {
      repository.deleteAll();
    }

    @Test
    @DisplayName("should return 200")
    void shouldReturn200AndTokenResponse_whenRequestIsValid() throws Exception {
      mockMvc.perform(post("/auth/login")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(validRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.data.expiredIn").isNotEmpty());
    }

    @Test
    @DisplayName("should return 400 when email format invalid")
    void shouldReturn400_whenEmailInvalid() throws Exception {
      mockMvc.perform(post("/auth/login")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest());
    }
  }

  // TODO thêm các nested khác
}
