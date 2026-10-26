package com.spring.backend.integration.folder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.backend.dto.auth.LoginRequest;
import com.spring.backend.dto.folder.CreateFolderRequest;
import com.spring.backend.dto.folder.UpdateFolderRequest;
import com.spring.backend.entity.User;
import com.spring.backend.entity.Folder;
import com.spring.backend.entity.enums.Plan;
import com.spring.backend.entity.enums.Role;
import com.spring.backend.entity.enums.Status;
import com.spring.backend.integration.AbstractIntegrationTest;
import com.spring.backend.repository.UserRepository;
import com.spring.backend.repository.FolderRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Folder APIs covering critical error scenarios.
 * Extends AbstractIntegrationTest so PostgreSQL and MinIO (RustFS) containers are started.
 */
@DisplayName("Folder Integration Tests – Critical Error Paths")
@Transactional
class FolderIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    FolderRepository folderRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    private UUID ownerId;
    private UUID folderId;
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
    @DisplayName("POST /folders – create folder")
    class CreateFolderErrorTests {

        @Test
        @DisplayName("404 ERR_FOLDER_001 when parent folder does not exist")
        void parentNotFound_returns404() throws Exception {
            CreateFolderRequest req = new CreateFolderRequest();
            req.setName("Child");
            req.setParentFolderName("NonExistentFolder");

            mockMvc.perform(post("/folders")
                            .header("Authorization", authHeader())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ERR_FOLDER_001"));
        }

        @Test
        @DisplayName("404 ERR_FOLDER_001 when parent folder not found or no EDIT permission")
        void noEditPermission_returns404() throws Exception {
            // Create a second user
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

            // Create parent folder as other user
            Folder parentFolder = Folder.builder()
                    .name("Other Parent")
                    .owner(other)
                    .build();
            parentFolder = folderRepository.save(parentFolder);

            CreateFolderRequest req = new CreateFolderRequest();
            req.setName("Child");
            req.setParentFolderName(parentFolder.getName());

            mockMvc.perform(post("/folders")
                            .header("Authorization", authHeader())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ERR_FOLDER_001"));
        }
    }

    @Nested
    @DisplayName("GET /folders/{id}/contents – read folder contents")
    class GetContentsErrorTests {

        @Test
        @DisplayName("404 ERR_FOLDER_001 when folder does not exist")
        void folderNotFound_returns404() throws Exception {
            mockMvc.perform(get("/folders/{id}/contents", UUID.randomUUID())
                            .header("Authorization", authHeader()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ERR_FOLDER_001"));
        }

        @Test
        @DisplayName("403 ERR_FOLDER_002 when user has no VIEW permission")
        void noViewPermission_returns403() throws Exception {
            // Create folder owned by another user
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

            mockMvc.perform(get("/folders/{id}/contents", otherFolder.getId())
                            .header("Authorization", authHeader()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ERR_FOLDER_002"));
        }
    }

    @Nested
    @DisplayName("PATCH /folders/{id} – update folder")
    class UpdateFolderErrorTests {

        @Test
        @DisplayName("404 ERR_FOLDER_001 when folder does not exist")
        void notFound_returns404() throws Exception {
            UpdateFolderRequest req = new UpdateFolderRequest();
            req.setName("New Name");

            mockMvc.perform(patch("/folders/{id}", UUID.randomUUID())
                            .header("Authorization", authHeader())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ERR_FOLDER_001"));
        }

        @Test
        @DisplayName("403 ERR_FOLDER_002 when user lacks EDIT permission")
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

            UpdateFolderRequest req = new UpdateFolderRequest();
            req.setName("Valid Name");

            mockMvc.perform(patch("/folders/{id}", otherFolder.getId())
                            .header("Authorization", authHeader())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ERR_FOLDER_002"));
        }
    }

    @Nested
    @DisplayName("DELETE /folders/{id} – delete folder")
    class DeleteFolderErrorTests {

        @Test
        @DisplayName("404 ERR_FOLDER_001 when folder does not exist")
        void notFound_returns404() throws Exception {
            mockMvc.perform(delete("/folders/{id}", UUID.randomUUID())
                            .header("Authorization", authHeader()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ERR_FOLDER_001"));
        }

        @Test
        @DisplayName("403 ERR_FOLDER_002 when user is not owner")
        void notOwner_returns403() throws Exception {
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

            mockMvc.perform(delete("/folders/{id}", otherFolder.getId())
                            .header("Authorization", authHeader()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ERR_FOLDER_002"));
        }
    }

    @Nested
    @DisplayName("POST /folders/bulk-delete – bulk delete folders")
    class BulkDeleteFoldersErrorTests {

        @Test
        @DisplayName("403 ERR_FOLDER_002 when user is not owner of any folder")
        void notOwner_returns403() throws Exception {
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

            var req = Map.of("ids", List.of(otherFolder.getId()));

            mockMvc.perform(post("/folders/bulk-delete")
                            .header("Authorization", authHeader())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ERR_FOLDER_002"));
        }
    }

    @Nested
    @DisplayName("POST /folders/{id}/copy – copy folder")
    class CopyFolderErrorTests {

        @Test
        @DisplayName("404 ERR_FOLDER_001 when source folder does not exist")
        void sourceNotFound_returns404() throws Exception {
            var req = Map.of("targetFolderId", UUID.randomUUID());

            mockMvc.perform(post("/folders/{id}/copy", UUID.randomUUID())
                            .header("Authorization", authHeader())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ERR_FOLDER_001"));
        }

        @Test
        @DisplayName("403 ERR_FOLDER_002 when user lacks VIEW permission on source folder")
        void noViewPermission_returns403() throws Exception {
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

            var req = Map.of("targetFolderId", UUID.randomUUID());

            mockMvc.perform(post("/folders/{id}/copy", otherFolder.getId())
                            .header("Authorization", authHeader())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ERR_FOLDER_002"));
        }

        @Test
        @DisplayName("404 ERR_FOLDER_001 when target folder not found or no EDIT permission")
        void noEditOnTarget_returns404() throws Exception {
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

            var req = Map.of("targetFolderId", otherFolder.getId());

            mockMvc.perform(post("/folders/{id}/copy", UUID.randomUUID())
                            .header("Authorization", authHeader())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ERR_FOLDER_001"));
        }

        @Test
        @DisplayName("404 ERR_FOLDER_001 when target folder does not exist")
        void targetFolderNotFound_returns404() throws Exception {
            var req = Map.of("targetFolderId", UUID.randomUUID());

            mockMvc.perform(post("/folders/{id}/copy", UUID.randomUUID())
                            .header("Authorization", authHeader())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ERR_FOLDER_001"));
        }
    }

    @Nested
    @DisplayName("POST /folders/bulk-move – bulk move folders")
    class BulkMoveFoldersErrorTests {

        @Test
        @DisplayName("404 ERR_FOLDER_001 when target folder does not exist")
        void targetNotFound_returns404() throws Exception {
            var req = Map.of("ids", List.of(UUID.randomUUID()), "targetFolderId", UUID.randomUUID());

            mockMvc.perform(post("/folders/bulk-move")
                            .header("Authorization", authHeader())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ERR_FOLDER_001"));
        }

        @Test
        @DisplayName("403 ERR_FOLDER_002 when user lacks EDIT permission on target folder")
        void noEditOnTarget_returns403() throws Exception {
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

            var req = Map.of("ids", List.of(UUID.randomUUID()), "targetFolderId", otherFolder.getId());

            mockMvc.perform(post("/folders/bulk-move")
                            .header("Authorization", authHeader())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ERR_FOLDER_002"));
        }
    }

    @Nested
    @DisplayName("GET /folders/tree – folder tree (lazy load)")
    class FolderTreeErrorTests {

        @Test
        @DisplayName("200 OK returns root folders when parentFolderName is null")
        void rootFolders_returns200() throws Exception {
            mockMvc.perform(get("/folders/tree")
                            .header("Authorization", authHeader()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.folders").isArray());
        }
    }

    @Nested
    @DisplayName("GET /folders/{id}/path – breadcrumbs path")
    class FolderPathErrorTests {

        @Test
        @DisplayName("404 ERR_FOLDER_001 when folder does not exist")
        void notFound_returns404() throws Exception {
            mockMvc.perform(get("/folders/{id}/path", UUID.randomUUID())
                            .header("Authorization", authHeader()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ERR_FOLDER_001"));
        }

        @Test
        @DisplayName("403 ERR_FOLDER_002 when user lacks VIEW permission")
        void noViewPermission_returns403() throws Exception {
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

            mockMvc.perform(get("/folders/{id}/path", otherFolder.getId())
                            .header("Authorization", authHeader()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ERR_FOLDER_002"));
        }
    }
}