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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.spring.backend.dto.document.PresignedUrlResponse;
import com.spring.backend.entity.User;
import com.spring.backend.entity.enums.Plan;
import com.spring.backend.entity.enums.Role;
import com.spring.backend.entity.enums.Status;
import com.spring.backend.exception.AppException;
import com.spring.backend.exception.ErrorCode;
import com.spring.backend.exception.GlobalExceptionHandler;
import com.spring.backend.security.CustomUserDetails;
import com.spring.backend.service.DocumentService;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test luồng API cho DocumentController (download-url, preview-url).
 * Chỉ test tầng API (controller + exception handler), service được mock
 * nên không cần DB, không cần Docker/Testcontainers, không cần S3.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentController API Test")
class DocumentControllerTest {

  @InjectMocks
  private DocumentController documentController;

  @Mock
  private DocumentService documentService;

  private MockMvc mockMvc;

  private UUID userId;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(documentController)
      .setControllerAdvice(new GlobalExceptionHandler())
      .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
      .build();

    userId = UUID.randomUUID();
    User user = User.builder()
      .id(userId)
      .email("user@email.com")
      .passwordHash("hash")
      .displayName("user")
      .role(Role.USER)
      .status(Status.ACTIVE)
      .plan(Plan.FREE)
      .build();

    Authentication authentication = new UsernamePasswordAuthenticationToken(
      new CustomUserDetails(user, List.of()), null, List.of());
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Nested
  @DisplayName("GET /documents/{id}/download-url")
  class DownloadUrlTests {

    @Test
    @DisplayName("lấy presigned URL thành công → 200 với url và expiresIn=300")
    void shouldReturn200_whenDownloadUrl() throws Exception {
      UUID documentId = UUID.randomUUID();
      PresignedUrlResponse response = PresignedUrlResponse.builder()
        .url("https://minio.local/presigned-download")
        .expiresIn(300L)
        .build();
      given(documentService.getDownloadUrl(eq(documentId), eq(userId))).willReturn(response);

      mockMvc.perform(get("/documents/{id}/download-url", documentId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.data.url").value("https://minio.local/presigned-download"))
        .andExpect(jsonPath("$.data.expiresIn").value(300));
    }

    @Test
    @DisplayName("document không tồn tại → 404 DOCUMENT_NOT_FOUND")
    void shouldReturn404_whenDocumentNotFound() throws Exception {
      UUID documentId = UUID.randomUUID();
      given(documentService.getDownloadUrl(eq(documentId), eq(userId)))
        .willThrow(new AppException(ErrorCode.DOCUMENT_NOT_FOUND));

      mockMvc.perform(get("/documents/{id}/download-url", documentId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value(ErrorCode.DOCUMENT_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("không có quyền VIEW trên folder chứa file → 403 FOLDER_ACCESS_DENIED")
    void shouldReturn403_whenNoViewPermission() throws Exception {
      UUID documentId = UUID.randomUUID();
      given(documentService.getDownloadUrl(eq(documentId), eq(userId)))
        .willThrow(new AppException(ErrorCode.FOLDER_ACCESS_DENIED));

      mockMvc.perform(get("/documents/{id}/download-url", documentId))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value(ErrorCode.FOLDER_ACCESS_DENIED.getCode()));
    }
  }

  @Nested
  @DisplayName("GET /documents/{id}/preview-url")
  class PreviewUrlTests {

    @Test
    @DisplayName("mimeType hỗ trợ preview → 200 với url và expiresIn=300")
    void shouldReturn200_whenPreviewSupported() throws Exception {
      UUID documentId = UUID.randomUUID();
      PresignedUrlResponse response = PresignedUrlResponse.builder()
        .url("https://minio.local/presigned-preview")
        .expiresIn(300L)
        .build();
      given(documentService.getPreviewUrl(eq(documentId), eq(userId))).willReturn(response);

      mockMvc.perform(get("/documents/{id}/preview-url", documentId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.url").value("https://minio.local/presigned-preview"))
        .andExpect(jsonPath("$.data.expiresIn").value(300));
    }

    @Test
    @DisplayName("mimeType không hỗ trợ preview → 400 VALIDATION_ERROR")
    void shouldReturn400_whenPreviewUnsupported() throws Exception {
      UUID documentId = UUID.randomUUID();
      given(documentService.getPreviewUrl(eq(documentId), eq(userId)))
        .willThrow(new AppException(ErrorCode.VALIDATION_ERROR));

      mockMvc.perform(get("/documents/{id}/preview-url", documentId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));
    }

    @Test
    @DisplayName("document không tồn tại → 404 DOCUMENT_NOT_FOUND")
    void shouldReturn404_whenDocumentNotFound() throws Exception {
      UUID documentId = UUID.randomUUID();
      given(documentService.getPreviewUrl(eq(documentId), eq(userId)))
        .willThrow(new AppException(ErrorCode.DOCUMENT_NOT_FOUND));

      mockMvc.perform(get("/documents/{id}/preview-url", documentId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value(ErrorCode.DOCUMENT_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("không có quyền VIEW trên folder chứa file → 403 FOLDER_ACCESS_DENIED")
    void shouldReturn403_whenNoViewPermission() throws Exception {
      UUID documentId = UUID.randomUUID();
      given(documentService.getPreviewUrl(eq(documentId), eq(userId)))
        .willThrow(new AppException(ErrorCode.FOLDER_ACCESS_DENIED));

      mockMvc.perform(get("/documents/{id}/preview-url", documentId))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value(ErrorCode.FOLDER_ACCESS_DENIED.getCode()));
    }
  }
}
