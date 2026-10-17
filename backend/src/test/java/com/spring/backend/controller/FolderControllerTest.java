package com.spring.backend.controller;

import com.spring.backend.dto.folder.FolderCreateRequest;
import com.spring.backend.dto.folder.FolderDto;
import com.spring.backend.exception.GlobalExceptionHandler;
import com.spring.backend.security.CustomUserDetails;
import com.spring.backend.service.FolderService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FolderControllerTest {

    private MockMvc mockMvc;

    @Mock
    private FolderService folderService;

    @InjectMocks
    private FolderController folderController;

    private ObjectMapper objectMapper;
    private UUID userId;
    private UUID folderId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(folderController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        userId = UUID.randomUUID();
        folderId = UUID.randomUUID();

        // Set authenticated security context
        CustomUserDetails userDetails = new CustomUserDetails(
                com.spring.backend.entity.User.builder()
                        .id(userId)
                        .email("test@email.com")
                        .passwordHash("password")
                        .displayName("test user")
                        .build(),
                null
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Nested
    @DisplayName("POST /api/folders")
    class CreateFolderTests {

        @Test
        @DisplayName("should return 201 and created folder when request is valid")
        void shouldReturnCreated_whenRequestIsValid() throws Exception {
            // Given
            FolderCreateRequest request = FolderCreateRequest.builder()
                    .name("Tài liệu dự án")
                    .parentFolderId(null)
                    .build();

            FolderDto responseDto = FolderDto.builder()
                    .id(folderId)
                    .name("Tài liệu dự án")
                    .ownerId(userId)
                    .parentFolderId(null)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            given(folderService.createFolder(any(FolderCreateRequest.class), any(UUID.class)))
                    .willReturn(responseDto);

            // When & Then
            mockMvc.perform(post("/api/folders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(folderId.toString()))
                    .andExpect(jsonPath("$.data.name").value("Tài liệu dự án"))
                    .andExpect(jsonPath("$.data.ownerId").value(userId.toString()))
                    .andExpect(jsonPath("$.data.parentFolderId").isEmpty());

            verify(folderService).createFolder(any(FolderCreateRequest.class), any(UUID.class));
        }

        @Test
        @DisplayName("should return 400 when name is blank")
        void shouldReturnBadRequest_whenNameIsBlank() throws Exception {
            // Given
            FolderCreateRequest request = FolderCreateRequest.builder()
                    .name("   ")
                    .parentFolderId(null)
                    .build();

            // When & Then
            mockMvc.perform(post("/api/folders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("ERR_REQ_001"));

            verify(folderService, never()).createFolder(any(), any());
        }
    }
}
