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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Document APIs covering critical error scenarios.
 * Extends AbstractIntegrationTest so PostgreSQL and MinIO (RustFS) containers are started.
 */
@DisplayName("Document Integration Tests – Critical Error Paths")
@Transactional
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
    void setUp() {
        // create a test user and obtain a JWT via login endpoint
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 4);
        User owner = User.builder()
                .email("o" + uniqueSuffix + "@t.com")
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
            loginReq.setEmail("o" + uniqueSuffix + "@t.com");
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
    class GetDownloadUrlErrorTests {

        @Test
        @DisplayName("404 ERR_DOC_001 when document does not exist")
        void documentNotFound_returns404() throws Exception {
            mockMvc.perform(get("/documents/{id}/download-url", UUID.randomUUID())
                            .header("Authorization", authHeader()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ERR_DOC_001"));
        }

        @Test
        @DisplayName("404 ERR_DOC_001 when document not found or no VIEW permission")
        void noViewPermission_returns404() throws Exception {
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

            Folder otherFolder = Folder.builder()
                    .name("Other Folder")
                    .owner(other)
                    .build();
            otherFolder = folderRepository.save(otherFolder);

            Document otherDoc = Document.builder()
                    .name("other.pdf")
                    .mimeType("application/pdf")
                    .size(1024L)
                    .objectKey("other-key.pdf")
                    .folder(otherFolder)
                    .owner(other)
                    .build();
            otherDoc = documentRepository.save(otherDoc);

            mockMvc.perform(get("/documents/{id}/download-url", otherDoc.getId())
                            .header("Authorization", authHeader()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ERR_DOC_001"));
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
    class GetPreviewUrlErrorTests {

        @Test
        @DisplayName("404 ERR_DOC_001 when document does not exist")
        void documentNotFound_returns404() throws Exception {
            mockMvc.perform(get("/documents/{id}/preview-url", UUID.randomUUID())
                            .header("Authorization", authHeader()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ERR_DOC_001"));
        }

        @Test
        @DisplayName("404 ERR_DOC_001 when document not found or no VIEW permission")
        void noViewPermission_returns404() throws Exception {
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
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ERR_DOC_001"));
        }

        @Test
        @DisplayName("400 ERR_REQ_001 when mimeType is not previewable (e.g., video/mp4)")
        void unsupportedMimeType_returns400() throws Exception {
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
                    .andExpect(jsonPath("$.code").value("ERR_REQ_001"));
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
    }

    @Nested
    @DisplayName("POST /documents/{folderId} – upload file")
    class UploadDocumentErrorTests {

        @Test
        @DisplayName("404 ERR_FOLDER_001 when folder does not exist")
        void folderNotFound_returns404() throws Exception {
            var file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "test content".getBytes());

            mockMvc.perform(multipart("/documents/{folderId}", UUID.randomUUID())
                            .file(file)
                            .header("Authorization", authHeader()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ERR_FOLDER_001"));
        }

        @Test
        @DisplayName("403 ERR_FOLDER_002 when user lacks EDIT permission on folder")
        void noEditPermission_returns403() throws Exception {
            User other = User.builder()
                    .email("other3@test.com")
                    .passwordHash(passwordEncoder.encode("password"))
                    .displayName("Other3")
                    .role(Role.USER)
                    .status(Status.ACTIVE)
                    .plan(Plan.FREE)
                    .storageUsed(0L)
                    .storageLimit(104857600L)
                    .build();
            other = userRepository.save(other);

            Folder otherFolder = Folder.builder()
                    .name("Other Folder 3")
                    .owner(other)
                    .build();
            otherFolder = folderRepository.save(otherFolder);

            var file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "test content".getBytes());

            mockMvc.perform(multipart("/documents/{folderId}", otherFolder.getId())
                            .file(file)
                            .header("Authorization", authHeader()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ERR_FOLDER_002"));
        }
    }

    @Nested
    @DisplayName("PATCH /documents/{id} – update document")
    class UpdateDocumentErrorTests {

        @Test
        @DisplayName("404 ERR_DOC_001 when document does not exist")
        void notFound_returns404() throws Exception {
            var req = Map.of("name", "New Name");

            mockMvc.perform(patch("/documents/{id}", UUID.randomUUID())
                            .header("Authorization", authHeader())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ERR_DOC_001"));
        }

        @Test
        @DisplayName("404 ERR_DOC_001 when document not found or no EDIT permission")
        void noEditPermission_returns404() throws Exception {
            User other = User.builder()
                    .email("other4@test.com")
                    .passwordHash(passwordEncoder.encode("password"))
                    .displayName("Other4")
                    .role(Role.USER)
                    .status(Status.ACTIVE)
                    .plan(Plan.FREE)
                    .storageUsed(0L)
                    .storageLimit(104857600L)
                    .build();
            other = userRepository.save(other);

            Folder otherFolder = Folder.builder()
                    .name("Other Folder 4")
                    .owner(other)
                    .build();
            otherFolder = folderRepository.save(otherFolder);

            Document otherDoc = Document.builder()
                    .name("other.pdf")
                    .mimeType("application/pdf")
                    .size(1024L)
                    .objectKey("other-key-4.pdf")
                    .folder(otherFolder)
                    .owner(other)
                    .build();
            otherDoc = documentRepository.save(otherDoc);

            var req = Map.of("name", "New Name");

            mockMvc.perform(patch("/documents/{id}", otherDoc.getId())
                            .header("Authorization", authHeader())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ERR_DOC_001"));
        }

        @Test
        @DisplayName("404 ERR_FOLDER_001 when target folder does not exist")
        void targetFolderNotFound_returns404() throws Exception {
            var req = Map.of("folderId", UUID.randomUUID());

            mockMvc.perform(patch("/documents/{id}", documentId)
                            .header("Authorization", authHeader())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ERR_FOLDER_001"));
        }

        @Test
        @DisplayName("403 ERR_FOLDER_002 when moving to folder without EDIT permission")
        void moveToNoEditPermission_returns403() throws Exception {
            User other = User.builder()
                    .email("other5@test.com")
                    .passwordHash(passwordEncoder.encode("password"))
                    .displayName("Other5")
                    .role(Role.USER)
                    .status(Status.ACTIVE)
                    .plan(Plan.FREE)
                    .storageUsed(0L)
                    .storageLimit(104857600L)
                    .build();
            other = userRepository.save(other);

            Folder otherFolder = Folder.builder()
                    .name("Other Folder 5")
                    .owner(other)
                    .build();
            otherFolder = folderRepository.save(otherFolder);

            var req = Map.of("folderId", otherFolder.getId());

            mockMvc.perform(patch("/documents/{id}", documentId)
                            .header("Authorization", authHeader())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ERR_FOLDER_002"));
        }
    }

    @Nested
    @DisplayName("DELETE /documents/{id} – delete document")
    class DeleteDocumentErrorTests {

        @Test
        @DisplayName("404 ERR_DOC_001 when document does not exist")
        void notFound_returns404() throws Exception {
            mockMvc.perform(delete("/documents/{id}", UUID.randomUUID())
                            .header("Authorization", authHeader()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ERR_DOC_001"));
        }

        @Test
        @DisplayName("404 ERR_DOC_001 when document not found or no EDIT permission")
        void noEditPermission_returns404() throws Exception {
            User other = User.builder()
                    .email("other6@test.com")
                    .passwordHash(passwordEncoder.encode("password"))
                    .displayName("Other6")
                    .role(Role.USER)
                    .status(Status.ACTIVE)
                    .plan(Plan.FREE)
                    .storageUsed(0L)
                    .storageLimit(104857600L)
                    .build();
            other = userRepository.save(other);

            Folder otherFolder = Folder.builder()
                    .name("Other Folder 6")
                    .owner(other)
                    .build();
            otherFolder = folderRepository.save(otherFolder);

            Document otherDoc = Document.builder()
                    .name("other.pdf")
                    .mimeType("application/pdf")
                    .size(1024L)
                    .objectKey("other-key-6.pdf")
                    .folder(otherFolder)
                    .owner(other)
                    .build();
            otherDoc = documentRepository.save(otherDoc);

            mockMvc.perform(delete("/documents/{id}", otherDoc.getId())
                            .header("Authorization", authHeader()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ERR_DOC_001"));
        }
    }

    @Nested
    @DisplayName("POST /documents/bulk-delete – bulk delete documents")
    class BulkDeleteDocumentsErrorTests {

        @Test
        @DisplayName("404 ERR_DOC_001 when document not found or no EDIT permission")
        void noEditPermission_returns404() throws Exception {
            User other = User.builder()
                    .email("other7@test.com")
                    .passwordHash(passwordEncoder.encode("password"))
                    .displayName("Other7")
                    .role(Role.USER)
                    .status(Status.ACTIVE)
                    .plan(Plan.FREE)
                    .storageUsed(0L)
                    .storageLimit(104857600L)
                    .build();
            other = userRepository.save(other);

            Folder otherFolder = Folder.builder()
                    .name("Other Folder 7")
                    .owner(other)
                    .build();
            otherFolder = folderRepository.save(otherFolder);

            Document otherDoc = Document.builder()
                    .name("other.pdf")
                    .mimeType("application/pdf")
                    .size(1024L)
                    .objectKey("other-key-7.pdf")
                    .folder(otherFolder)
                    .owner(other)
                    .build();
            otherDoc = documentRepository.save(otherDoc);

            var req = Map.of("ids", List.of(otherDoc.getId()));

            mockMvc.perform(post("/documents/bulk-delete")
                            .header("Authorization", authHeader())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ERR_DOC_001"));
        }
    }

    @Nested
    @DisplayName("POST /documents/{id}/copy – copy document")
    class CopyDocumentErrorTests {

        @Test
        @DisplayName("404 ERR_DOC_001 when document does not exist")
        void notFound_returns404() throws Exception {
            var req = Map.of("targetFolderId", folderId);

            mockMvc.perform(post("/documents/{id}/copy", UUID.randomUUID())
                            .header("Authorization", authHeader())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ERR_DOC_001"));
        }

        @Test
        @DisplayName("404 ERR_DOC_001 when source document not found or no VIEW permission")
        void noViewPermission_returns404() throws Exception {
            User other = User.builder()
                    .email("other8@test.com")
                    .passwordHash(passwordEncoder.encode("password"))
                    .displayName("Other8")
                    .role(Role.USER)
                    .status(Status.ACTIVE)
                    .plan(Plan.FREE)
                    .storageUsed(0L)
                    .storageLimit(104857600L)
                    .build();
            other = userRepository.save(other);

            Folder otherFolder = Folder.builder()
                    .name("Other Folder 8")
                    .owner(other)
                    .build();
            otherFolder = folderRepository.save(otherFolder);

            Document otherDoc = Document.builder()
                    .name("other.pdf")
                    .mimeType("application/pdf")
                    .size(1024L)
                    .objectKey("other-key-8.pdf")
                    .folder(otherFolder)
                    .owner(other)
                    .build();
            otherDoc = documentRepository.save(otherDoc);

            var req = Map.of("targetFolderId", folderId);

            mockMvc.perform(post("/documents/{id}/copy", otherDoc.getId())
                            .header("Authorization", authHeader())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ERR_DOC_001"));
        }

        @Test
        @DisplayName("403 ERR_FOLDER_002 when user lacks EDIT permission on target folder")
        void noEditOnTarget_returns403() throws Exception {
            User other = User.builder()
                    .email("other9@test.com")
                    .passwordHash(passwordEncoder.encode("password"))
                    .displayName("Other9")
                    .role(Role.USER)
                    .status(Status.ACTIVE)
                    .plan(Plan.FREE)
                    .storageUsed(0L)
                    .storageLimit(104857600L)
                    .build();
            other = userRepository.save(other);

            Folder otherFolder = Folder.builder()
                    .name("Other Folder 9")
                    .owner(other)
                    .build();
            otherFolder = folderRepository.save(otherFolder);

            var req = Map.of("targetFolderId", otherFolder.getId());

            mockMvc.perform(post("/documents/{id}/copy", documentId)
                            .header("Authorization", authHeader())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ERR_FOLDER_002"));
        }

        @Test
        @DisplayName("404 ERR_FOLDER_001 when target folder does not exist")
        void targetFolderNotFound_returns404() throws Exception {
            var req = Map.of("targetFolderId", UUID.randomUUID());

            mockMvc.perform(post("/documents/{id}/copy", documentId)
                            .header("Authorization", authHeader())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ERR_FOLDER_001"));
        }
    }
}