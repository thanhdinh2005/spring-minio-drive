package com.spring.backend.controller;

import java.util.List;
import java.util.UUID;

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

import com.spring.backend.dto.folder.CreateFolderRequest;
import com.spring.backend.dto.folder.FolderResponse;
import com.spring.backend.entity.User;
import com.spring.backend.entity.enums.Plan;
import com.spring.backend.entity.enums.Role;
import com.spring.backend.entity.enums.Status;
import com.spring.backend.exception.AppException;
import com.spring.backend.exception.ErrorCode;
import com.spring.backend.exception.GlobalExceptionHandler;
import com.spring.backend.security.CustomUserDetails;
import com.spring.backend.service.FolderService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test luồng API cho POST /folders.
 * Chỉ test tầng API (controller + validation + exception handler), service được mock
 * nên không cần DB, không cần Docker/Testcontainers.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FolderController API Test")
class FolderControllerTest {

  @InjectMocks
  private FolderController folderController;

  @Mock
  private FolderService folderService;

  private MockMvc mockMvc;

  private UUID ownerId;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(folderController)
      .setControllerAdvice(new GlobalExceptionHandler())
      .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
      .build();

    ownerId = UUID.randomUUID();
    User owner = User.builder()
      .id(ownerId)
      .email("owner@email.com")
      .passwordHash("hash")
      .displayName("owner")
      .role(Role.USER)
      .status(Status.ACTIVE)
      .plan(Plan.FREE)
      .build();

    Authentication authentication = new UsernamePasswordAuthenticationToken(
      new CustomUserDetails(owner, List.of()), null, List.of());
    // standalone MockMvc không có filter chain nên AuthenticationPrincipalArgumentResolver
    // chỉ đọc được principal từ SecurityContextHolder
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Nested
  @DisplayName("POST /folders")
  class CreateTests {

    @Test
    @DisplayName("tạo folder gốc thành công → 201 Created")
    void shouldReturn201_whenCreateRootFolder() throws Exception {
      FolderResponse response = FolderResponse.builder()
        .id(UUID.randomUUID())
        .name("Docs")
        .parentFolderId(null)
        .ownerId(ownerId)
        .build();
      given(folderService.create(any(CreateFolderRequest.class), eq(ownerId))).willReturn(response);

      mockMvc.perform(post("/folders")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"name\":\"Docs\",\"parentFolderId\":null}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.status").value(201))
        .andExpect(jsonPath("$.data.id").value(response.getId().toString()))
        .andExpect(jsonPath("$.data.name").value("Docs"))
        .andExpect(jsonPath("$.data.parentFolderId").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data.ownerId").value(ownerId.toString()));

      verify(folderService).create(any(CreateFolderRequest.class), eq(ownerId));
    }

    @Test
    @DisplayName("tạo folder con thành công → 201 với parentFolderId")
    void shouldReturn201_whenCreateChildFolder() throws Exception {
      UUID parentId = UUID.randomUUID();
      FolderResponse response = FolderResponse.builder()
        .id(UUID.randomUUID())
        .name("Sub")
        .parentFolderId(parentId)
        .ownerId(ownerId)
        .build();
      given(folderService.create(any(CreateFolderRequest.class), eq(ownerId))).willReturn(response);

      mockMvc.perform(post("/folders")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"name\":\"Sub\",\"parentFolderId\":\"" + parentId + "\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.parentFolderId").value(parentId.toString()));
    }

    @Test
    @DisplayName("tên rỗng → 400 VALIDATION_ERROR, không gọi service")
    void shouldReturn400_whenNameBlank() throws Exception {
      mockMvc.perform(post("/folders")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"name\":\"\",\"parentFolderId\":null}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));

      verify(folderService, never()).create(any(), any());
    }

    @Test
    @DisplayName("parent không tồn tại → 404 FOLDER_NOT_FOUND")
    void shouldReturn404_whenParentNotFound() throws Exception {
      given(folderService.create(any(CreateFolderRequest.class), eq(ownerId)))
        .willThrow(new AppException(ErrorCode.FOLDER_NOT_FOUND));

      mockMvc.perform(post("/folders")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"name\":\"Sub\",\"parentFolderId\":\"" + UUID.randomUUID() + "\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value(ErrorCode.FOLDER_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("không có quyền EDIT trên parent → 403 FOLDER_ACCESS_DENIED")
    void shouldReturn403_whenNoEditPermission() throws Exception {
      given(folderService.create(any(CreateFolderRequest.class), eq(ownerId)))
        .willThrow(new AppException(ErrorCode.FOLDER_ACCESS_DENIED));

      mockMvc.perform(post("/folders")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"name\":\"Sub\",\"parentFolderId\":\"" + UUID.randomUUID() + "\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value(ErrorCode.FOLDER_ACCESS_DENIED.getCode()));
    }

    @Test
    @DisplayName("tên chứa ký tự không hợp lệ → 400 INVALID_FOLDER_NAME")
    void shouldReturn400_whenNameInvalid() throws Exception {
      given(folderService.create(any(CreateFolderRequest.class), eq(ownerId)))
        .willThrow(new AppException(ErrorCode.INVALID_FOLDER_NAME));

      mockMvc.perform(post("/folders")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"name\":\"a/b\",\"parentFolderId\":null}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_FOLDER_NAME.getCode()));
    }
  }
}
