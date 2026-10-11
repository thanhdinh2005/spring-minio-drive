package com.spring.backend.controller;

import com.spring.backend.dto.auth.UserResponse;
import com.spring.backend.dto.user.StorageUsageResponse;
import com.spring.backend.entity.User;
import com.spring.backend.entity.enums.Plan;
import com.spring.backend.entity.enums.Role;
import com.spring.backend.entity.enums.Status;
import com.spring.backend.security.CustomUserDetails;
import com.spring.backend.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;

import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

  @InjectMocks
  private UserController userController;

  @Mock
  private UserService userService;

  private MockMvc mockMvc;
  private UUID currentUserId;
  private User currentUser;

  @BeforeEach
  void setUp() {
    HandlerMethodArgumentResolver principalResolver = new AuthenticationPrincipalArgumentResolver();
    mockMvc = MockMvcBuilders.standaloneSetup(userController)
      .setCustomArgumentResolvers(principalResolver)
      .build();

    currentUserId = UUID.randomUUID();
    currentUser = User.builder()
      .id(currentUserId)
      .email("user@example.com")
      .passwordHash("hashedPassword")
      .displayName("Test User")
      .role(Role.USER)
      .status(Status.ACTIVE)
      .plan(Plan.FREE)
      .storageUsed(0L)
      .storageLimit(104857600L)
      .build();

    Authentication authentication = new UsernamePasswordAuthenticationToken(
      new CustomUserDetails(currentUser, List.of()), null, List.of());
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Nested
  @DisplayName("GET /users/me")
  class GetMyProfileTests {

    @Test
    @DisplayName("Trả về 200 OK và thông tin profile của currentUser")
    void getMyProfile_Success() throws Exception {
      UserResponse response = UserResponse.builder()
        .id(currentUserId)
        .email("user@example.com")
        .displayName("Test User")
        .role(Role.USER)
        .plan(Plan.FREE)
        .build();

      given(userService.getProfile(currentUserId)).willReturn(response);

      mockMvc.perform(get("/users/me")
          .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(currentUserId.toString()))
        .andExpect(jsonPath("$.data.email").value("user@example.com"))
        .andExpect(jsonPath("$.data.displayName").value("Test User"))
        .andExpect(jsonPath("$.data.role").value("USER"))
        .andExpect(jsonPath("$.data.plan").value("FREE"));

      verify(userService).getProfile(currentUserId);
    }
  }

  @Nested
  @DisplayName("GET /users/me/storage")
  class GetMyStorageTests {

    @Test
    @DisplayName("Trả về 200 OK và dung lượng lưu trữ của currentUser")
    void getMyStorage_Success() throws Exception {
      StorageUsageResponse response = StorageUsageResponse.builder()
        .storageUsed(52428800L)
        .storageLimit(104857600L)
        .plan(Plan.FREE)
        .build();

      given(userService.getStorageUsage(currentUserId)).willReturn(response);

      mockMvc.perform(get("/users/me/storage")
          .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.storageUsed").value(52428800L))
        .andExpect(jsonPath("$.data.storageLimit").value(104857600L))
        .andExpect(jsonPath("$.data.plan").value("FREE"));

      verify(userService).getStorageUsage(currentUserId);
    }
  }
}
