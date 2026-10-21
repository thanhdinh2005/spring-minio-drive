package com.spring.backend.integration.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.backend.dto.auth.LoginRequest;
import com.spring.backend.entity.Document;
import com.spring.backend.entity.Folder;
import com.spring.backend.entity.User;
import com.spring.backend.entity.enums.Plan;
import com.spring.backend.entity.enums.Role;
import com.spring.backend.entity.enums.Status;
import com.spring.backend.integration.AbstractIntegrationTest;
import com.spring.backend.repository.DocumentRepository;
import com.spring.backend.repository.FolderRepository;
import com.spring.backend.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Document APIs covering critical error scenarios.
 * Extends AbstractIntegrationTest so PostgreSQL and MinIO (RustFS) containers are started.
 */
@DisplayName("Document Integration Tests – Critical Error Paths")
class DocumentIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    FolderRepository folderRepository;

    @Autowired
    DocumentRepository documentRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    private UUID ownerId;
    private UUID folderId;
    private UUID documentId;
    private String accessToken;

    @BeforeEach
    @Transactional
    void setUp() {
        // create a test user and obtain a JWT via login endpoint
        User owner = User.builder()
                .email("owner@test.com")
                .passwordHash(passwordEncoder.encode("password"))
                .displayName("Owner")
                .role(Role.USER)
                .status(Status.ACTIVE)
                .plan(Plan.FREE)
                .storageUsed(0L)
                .storageLimit(104857600L)
                .build();
        owner = userRepository.save(owner);
        ownerId = owner.getId();

        // create a root folder for the user
        Folder folder = Folder.builder()
                .name("Test Folder")
                .owner(owner)
                .build();
        folder = folderRepository.save(folder);
        folderId = folder.getId();

        // create a test document (PDF - previewable)
        Document document = Document.builder()
                .name("test.pdf")
                .mimeType("application/pdf")
                .size(1024L)
                .objectKey("test-object-key.pdf")
                .folder(folder)
                .owner(owner)
                .build();
        document = documentRepository.save(document);
        documentId = document.getId();

        // login to get token
        try {
            var loginReq = new LoginRequest();
            loginReq.setEmail("owner@test.com");
            loginReq.setPassword("password");
            var result = mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginReq)))
                    .andExpect(status().isOk())
                    .andReturn();
            String json = result.getResponse().getContentAsString();
            var map = objectMapper.readValue(json, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) map.get("data");
            accessToken = (String) data.get("accessToken");
        } catch (Exception e) {
            throw new RuntimeException("Failed to login test user", e);
        }
    }

    private String authHeader() {
        return "Bearer " + accessToken;
    }

    @Nested
    @DisplayName("GET /documents/{id}/download-url")
    @Transactional
    class GetDownloadUrlErrorTests {

        @Test
        @DisplayName("404 DOCUMENT_NOT_FOUND when document does not exist")
        void documentNotFound_returns404() throws Exception {
            mockMvc.perform(get("/documents/{id}/download-url", UUID.randomUUID())
                            .header("Authorization", authHeader()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
        }

        @Test
        @DisplayName("403 FOLDER_ACCESS_DENIED when user lacks VIEW permission on parent folder")
        void noViewPermission_returns403() throws Exception {
            // create a second user
            User other = User.builder()
                    .email("other@test.com")
                    .passwordHash(passwordEncoder.encode("password"))
                    .displayName("Other")
                    .role(Role.USER)
                    .status(Status.ACTIVE)
                    .plan(Plan.FREE)
                    .storageUsed(0L)
                    .storageLimit(104857600L)
                    .build();
            other = userRepository.save(other);

            // create a folder owned by other user
            Folder otherFolder = Folder.builder()
                    .name("Other Folder")
                    .owner(other)
                    .build();
            otherFolder = folderRepository.save(otherFolder);

            // create a document in other's folder
            Document otherDoc = Document.builder()
                    .name("other.pdf")
                    .mimeType("application/pdf")
                    .size(1024L)
                    .objectKey("other-key.pdf")
                    .folder(otherFolder)
                    .owner(other)
                    .build();
            otherDoc = documentRepository.save(otherDoc);

            // try to access with owner's token (no permission)
            mockMvc.perform(get("/documents/{id}/download-url", otherDoc.getId())
                            .header("Authorization", authHeader()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FOLDER_ACCESS_DENIED"));
        }

        @Test
        @DisplayName("200 OK when user has VIEW permission on parent folder")
        void success_returns200() throws Exception {
            mockMvc.perform(get("/documents/{id}/download-url", documentId)
                            .header("Authorization", authHeader()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.url").exists())
                    .andExpect(jsonPath("$.data.expiresIn").value(300));
        }
    }

    @Nested
    @DisplayName("GET /documents/{id}/preview-url")
    @Transactional
    class GetPreviewUrlErrorTests {

        @Test
        @DisplayName("404 DOCUMENT_NOT_FOUND when document does not exist")
        void documentNotFound_returns404() throws Exception {
            mockMvc.perform(get("/documents/{id}/preview-url", UUID.randomUUID())
                            .header("Authorization", authHeader()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
        }

        @Test
        @DisplayName("403 FOLDER_ACCESS_DENIED when user lacks VIEW permission on parent folder")
        void noViewPermission_returns403() throws Exception {
            User other = User.builder()
                    .email("other2@test.com")
                    .passwordHash(passwordEncoder.encode("password"))
                    .displayName("Other2")
                    .role(Role.USER)
                    .status(Status.ACTIVE)
                    .plan(Plan.FREE)
                    .storageUsed(0L)
                    .storageLimit(104857600L)
                    .build();
            other = userRepository.save(other);

            Folder otherFolder = Folder.builder()
                    .name("Other Folder 2")
                    .owner(other)
                    .build();
            otherFolder = folderRepository.save(otherFolder);

            Document otherDoc = Document.builder()
                    .name("other.pdf")
                    .mimeType("application/pdf")
                    .size(1024L)
                    .objectKey("other-key-2.pdf")
                    .folder(otherFolder)
                    .owner(other)
                    .build();
            otherDoc = documentRepository.save(otherDoc);

            mockMvc.perform(get("/documents/{id}/preview-url", otherDoc.getId())
                            .header("Authorization", authHeader()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FOLDER_ACCESS_DENIED"));
        }

        @Test
        @DisplayName("400 VALIDATION_ERROR when mimeType is not previewable (e.g., video/mp4)")
        void unsupportedMimeType_returns400() throws Exception {
            // create a document with non-previewable mimeType
            Document videoDoc = Document.builder()
                    .name("video.mp4")
                    .mimeType("video/mp4")
                    .size(5000000L)
                    .objectKey("video-key.mp4")
                    .folder(folderRepository.findById(folderId).orElseThrow())
                    .owner(userRepository.findById(ownerId).orElseThrow())
                    .build();
            videoDoc = documentRepository.save(videoDoc);

            mockMvc.perform(get("/documents/{id}/preview-url", videoDoc.getId())
                            .header("Authorization", authHeader()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("400 VALIDATION_ERROR when mimeType is application/zip")
        void zipMimeType_returns400() throws Exception {
            Document zipDoc = Document.builder()
                    .name("archive.zip")
                    .mimeType("application/zip")
                    .size(2048L)
                    .objectKey("zip-key.zip")
                    .folder(folderRepository.findById(folderId).orElseThrow())
                    .owner(userRepository.findById(ownerId).orElseThrow())
                    .build();
            zipDoc = documentRepository.save(zipDoc);

            mockMvc.perform(get("/documents/{id}/preview-url", zipDoc.getId())
                            .header("Authorization", authHeader()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("200 OK for application/pdf")
        void pdfPreview_returns200() throws Exception {
            mockMvc.perform(get("/documents/{id}/preview-url", documentId)
                            .header("Authorization", authHeader()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.url").exists())
                    .andExpect(jsonPath("$.data.expiresIn").value(300));
        }

        @Test
        @DisplayName("200 OK for image/png")
        void imagePreview_returns200() throws Exception {
            // create an image document
            Document imageDoc = Document.builder()
                    .name("photo.png")
                    .mimeType("image/png")
                    .size(2048L)
                    .objectKey("image-key.png")
                    .folder(folderRepository.findById(folderId).orElseThrow())
                    .owner(userRepository.findById(ownerId).orElseThrow())
                    .build();
            imageDoc = documentRepository.save(imageDoc);

            mockMvc.perform(get("/documents/{id}/preview-url", imageDoc.getId())
                            .header("Authorization", authHeader()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.url").exists())
                    .andExpect(jsonPath("$.data.expiresIn").value(300));
        }

        @Test
        @DisplayName("200 OK for image/jpeg")
        void jpegPreview_returns200() throws Exception {
            Document imageDoc = Document.builder()
                    .name("photo.jpg")
                    .mimeType("image/jpeg")
                    .size(2048L)
                    .objectKey("image-key.jpg")
                    .folder(folderRepository.findById(folderId).orElseThrow())
                    .owner(userRepository.findById(ownerId).orElseThrow())
                    .build();
            imageDoc = documentRepository.save(imageDoc);

            mockMvc.perform(get("/documents/{id}/preview-url", imageDoc.getId())
                            .header("Authorization", authHeader()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.url").exists())
                    .andExpect(jsonPath("$.data.expiresIn").value(300));
        }
    }
}