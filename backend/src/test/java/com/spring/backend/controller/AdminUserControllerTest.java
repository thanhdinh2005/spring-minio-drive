package com.spring.backend.controller;

import com.spring.backend.common.PageResponse;
import com.spring.backend.dto.admin.UpdateUserStatusResponse;
import com.spring.backend.dto.auth.AdminUserResponse;
import com.spring.backend.entity.User;
import com.spring.backend.entity.enums.Plan;
import com.spring.backend.entity.enums.Role;
import com.spring.backend.entity.enums.Status;
import com.spring.backend.exception.AppException;
import com.spring.backend.exception.ErrorCode;
import com.spring.backend.exception.GlobalExceptionHandler;
import com.spring.backend.security.CustomUserDetails;
import com.spring.backend.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUserController API Test")
class AdminUserControllerTest {

  @InjectMocks
  private AdminUserController adminUserController;

  @Mock
  private UserService userService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(adminUserController)
      .setControllerAdvice(new GlobalExceptionHandler())
      .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
      .build();

    // Giả lập User Admin đã đăng nhập sẵn trong SecurityContext
    User adminUser = User.builder()
      .id(UUID.randomUUID())
      .email("admin@test.com")
      .passwordHash("any_dummy_hash")
      .displayName("Admin")
      .role(Role.ADMIN)
      .status(Status.ACTIVE)
      .plan(Plan.PREMIUM)
      .build();

    var authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
    var authentication = new UsernamePasswordAuthenticationToken(
      new CustomUserDetails(adminUser, authorities), null, authorities);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("GET /admin/users - Trả về 200 khi có quyền ADMIN")
  void shouldReturn200_whenGetAllUsers() throws Exception {
    AdminUserResponse userItem = AdminUserResponse.builder()
      .id(UUID.randomUUID())
      .email("user@gmail.com")
        .displayName("User A")
      .status(Status.ACTIVE)
      .plan(Plan.FREE)
      .createdAt(Instant.now())
      .build();

    PageResponse<AdminUserResponse> pageResponse = PageResponse.<AdminUserResponse>builder()
      .content(List.of(userItem))
      .pageNo(0)
      .pageSize(10)
      .totalElements(1)
      .totalPages(1)
      .build();

    given(userService.getAllUsers(any(Pageable.class))).willReturn(pageResponse);

    mockMvc.perform(get("/admin/users?page=0&size=10")
        .contentType(MediaType.APPLICATION_JSON))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.data.content[0].email").value("user@gmail.com"))
      .andExpect(jsonPath("$.data.totalElements").value(1))
      .andExpect(jsonPath("$.data.totalPages").value(1));
  }

  @Test
  @DisplayName("PATCH /admin/users/{id}/status thành công -> 200 OK")
  void updateUserStatus_Success() throws Exception {
    UUID targetUserId = UUID.randomUUID();
    UpdateUserStatusResponse response = UpdateUserStatusResponse.builder()
      .id(targetUserId)
      .status(Status.LOCKED)
      .build();

    given(userService.updateUserStatus(eq(targetUserId), eq(Status.LOCKED)))
      .willReturn(response);

    mockMvc.perform(patch("/admin/users/{id}/status", targetUserId)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"LOCKED\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.data.id").value(targetUserId.toString()))
      .andExpect(jsonPath("$.data.status").value("LOCKED"));
  }

  @Test
  @DisplayName("User không tồn tại -> 404 USER_NOT_FOUND")
  void updateUserStatus_NotFound() throws Exception {
    UUID targetUserId = UUID.randomUUID();

    given(userService.updateUserStatus(eq(targetUserId), eq(Status.LOCKED)))
      .willThrow(new AppException(ErrorCode.USER_NOT_FOUND));
    mockMvc.perform(patch("/admin/users/{id}/status", targetUserId)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"LOCKED\"}"))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.code").value("ERR_USER_001"));
  }
}
