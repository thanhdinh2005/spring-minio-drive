package com.spring.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.spring.backend.AbstractIntegrationTest;
import com.spring.backend.entity.User;
import com.spring.backend.entity.enums.Status;
import com.spring.backend.repository.UserRepository;

import tools.jackson.databind.JsonNode;

/**
 * Integration test cho auth (thay thế AuthServiceImplTest unit/mock cũ):
 * HTTP thật + AuthenticationManager thật + DB thật + JWT thật.
 */
public class AuthIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private UserRepository userRepo;

  @Test
  @DisplayName("login thành công trả 200 + accessToken + expiredIn")
  void login_ok_returnsToken() throws Exception {
    String email = createUser();

    MvcResult result = mockMvc.perform(post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.data.accessToken").exists())
      .andExpect(jsonPath("$.data.expiredIn").exists())
      .andReturn();

    JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
    String token = root.get("data").get("accessToken").asText();
    assertThat(token).isNotBlank();
    // JWT thật có 3 phần header.payload.signature
    assertThat(token.split("\\.")).hasSize(3);
  }

  @Test
  @DisplayName("sai password trả 401 ERR_REQ_005")
  void login_wrongPassword_returns401() throws Exception {
    String email = createUser();

    mockMvc.perform(post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "WrongPass1"))))
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.code").value("ERR_REQ_005"));
  }

  @Test
  @DisplayName("email chưa tồn tại trả 401 (không lộ user có tồn tại hay không)")
  void login_unknownEmail_returns401() throws Exception {
    String email = uniqueEmail();

    mockMvc.perform(post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.code").value("ERR_REQ_005"));
  }

  @Test
  @DisplayName("tài khoản LOCKED trả 403 ERR_USER_003")
  void login_lockedAccount_returns403() throws Exception {
    String email = createUser();
    User user = userRepo.findByEmail(email).orElseThrow();
    user.setStatus(Status.LOCKED);
    userRepo.save(user);

    mockMvc.perform(post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.code").value("ERR_USER_003"));
  }

  @Test
  @DisplayName("body invalid (email sai format / thiếu password) trả 400 ERR_REQ_001")
  void login_validationError_returns400() throws Exception {
    // email sai format
    mockMvc.perform(post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"not-an-email\",\"password\":\"Secret123\"}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.code").value("ERR_REQ_001"));

    // thiếu password
    mockMvc.perform(post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + uniqueEmail() + "\",\"password\":\"\"}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.code").value("ERR_REQ_001"));
  }
}
