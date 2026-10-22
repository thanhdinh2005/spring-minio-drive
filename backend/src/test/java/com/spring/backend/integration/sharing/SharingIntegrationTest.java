package com.spring.backend.integration.sharing;
import com.spring.backend.entity.Permission;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.spring.backend.entity.Folder;
import com.spring.backend.entity.User;
import com.spring.backend.entity.enums.Plan;
import com.spring.backend.entity.enums.Role;
import com.spring.backend.entity.enums.Status;
import com.spring.backend.entity.enums.PermissionType;
import com.spring.backend.integration.AbstractIntegrationTest;
import com.spring.backend.repository.FolderRepository;
import com.spring.backend.repository.PermissionRepository;
import com.spring.backend.repository.UserRepository;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/*
 * SHARING INTEGRATION TEST
 *
 * Theo cấu trúc AuthIntegrationTest của dự án.
 *
 * Kế thừa AbstractIntegrationTest để sử dụng:
 * - Spring Boot Test
 * - MockMvc
 * - PostgreSQL Testcontainers
 * - RustFS Testcontainers
 *
 * Yêu cầu: Docker Desktop đang chạy.
 *
 * Chạy riêng:
 * mvnw.cmd -Dtest=SharingIntegrationTest test
 *
 * Chạy cùng unit test:
 * mvnw.cmd -Dtest=SharingServiceImplTest,SharingIntegrationTest test
 *
 * Lưu ý: MockMvc dùng /folders/...,
 * không thêm /api vào đường dẫn request.
 */
class SharingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Nested
    @DisplayName("Grant Permission Integration Test")
    @Transactional
    class GrantPermissionIntegrationTest {

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private FolderRepository folderRepository;

        @Autowired
        private PermissionRepository permissionRepository;

        @Autowired
        private PasswordEncoder passwordEncoder;

        @Autowired
        private EntityManager entityManager;

        private User owner;
        private User recipient;
        private Folder folder;

        private String ownerToken;
        private String recipientToken;

        private static final String PASSWORD = "password123";

        @BeforeEach
        void setUp() throws Exception {

            // Tạo user sở hữu folder
            owner = createUser("Owner");

            // Tạo user nhận quyền
            recipient = createUser("Recipient");

            // Tạo folder mới thuộc owner
            folder = folderRepository.saveAndFlush(
                Folder.builder()
                    .name("Share-" + UUID.randomUUID())
                    .owner(owner)
                    .build()
            );

            // Lấy JWT qua API đăng nhập thật
            ownerToken = login(owner.getEmail());
            recipientToken = login(recipient.getEmail());
        }

        /*
         * TEST 1: Owner cấp VIEW thành công.
         *
         * Kiểm tra HTTP 201, JSON và database.
         */
        @Test
        @DisplayName("should return 201 and save VIEW permission")
        void shouldReturn201_whenOwnerGrantsView()
                throws Exception {

            String request = shareRequest(
                recipient.getId(), "VIEW"
            );

            mockMvc.perform(
                    post("/folders/{id}/permissions", folder.getId())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.folderId")
                    .value(folder.getId().toString()))
                .andExpect(jsonPath("$.data.sharedWithUserId")
                    .value(recipient.getId().toString()))
                .andExpect(jsonPath("$.data.permissionType")
                    .value("VIEW"));

            var saved = permissionRepository
                .findByFolder_IdAndSharedWithUser_Id(
                    folder.getId(), recipient.getId()
                );

            assertTrue(saved.isPresent());

            assertEquals(
                PermissionType.VIEW,
                saved.get().getPermissionType()
            );
        }

        /*
         * TEST 2: Owner cấp EDIT thành công.
         */
        @Test
        @DisplayName("should return 201 and save EDIT permission")
        void shouldReturn201_whenOwnerGrantsEdit()
                throws Exception {

            mockMvc.perform(
                    post("/folders/{id}/permissions", folder.getId())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shareRequest(
                            recipient.getId(), "EDIT"
                        ))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.permissionType")
                    .value("EDIT"));

            var saved = permissionRepository
                .findByFolder_IdAndSharedWithUser_Id(
                    folder.getId(), recipient.getId()
                );

            assertTrue(saved.isPresent());

            assertEquals(
                PermissionType.EDIT,
                saved.get().getPermissionType()
            );
        }

        /*
         * TEST 3: User không phải owner
         * không được cấp quyền.
         */
        @Test
        @DisplayName("should return 403 when caller is not owner")
        void shouldReturn403_whenCallerIsNotOwner()
                throws Exception {

            mockMvc.perform(
                    post("/folders/{id}/permissions", folder.getId())
                        .header("Authorization", bearer(recipientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shareRequest(
                            owner.getId(), "VIEW"
                        ))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_PERM_003"));

            assertTrue(permissionRepository
                .findByFolder_IdAndSharedWithUser_Id(
                    folder.getId(), owner.getId()
                )
                .isEmpty());
        }

        /*
         * TEST 4: Không cho phép share
         * cho chính owner.
         */
        @Test
        @DisplayName("should return 400 when sharing with owner")
        void shouldReturn400_whenSharingWithOwner()
                throws Exception {

            mockMvc.perform(
                    post("/folders/{id}/permissions", folder.getId())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shareRequest(
                            owner.getId(), "VIEW"
                        ))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_PERM_002"));
        }

        /*
         * TEST 5: Thiếu sharedWithUserId.
         */
        @Test
        @DisplayName("should return 400 when target user is missing")
        void shouldReturn400_whenTargetMissing()
                throws Exception {

            String request = """
                {
                    "permissionType": "VIEW"
                }
                """;

            mockMvc.perform(
                    post("/folders/{id}/permissions", folder.getId())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_PERM_002"));
        }

        /*
         * TEST 6: Thiếu permissionType.
         */
        @Test
        @DisplayName("should return 400 when permission is missing")
        void shouldReturn400_whenPermissionMissing()
                throws Exception {

            String request = """
                {
                    "sharedWithUserId": "%s"
                }
                """.formatted(recipient.getId());

            mockMvc.perform(
                    post("/folders/{id}/permissions", folder.getId())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request)
                )
                .andExpect(status().isBadRequest());
        }

        /*
         * TEST 7: Không cho phép cấp
         * permissionType khác VIEW/EDIT.
         */
        @Test
        @DisplayName("should return 400 for invalid permission type")
        void shouldReturn400_whenPermissionInvalid()
                throws Exception {

            mockMvc.perform(
                    post("/folders/{id}/permissions", folder.getId())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shareRequest(
                            recipient.getId(), "INVALID"
                        ))
                )
                .andExpect(status().isBadRequest());
        }

        /*
         * TEST 8: Người nhận không tồn tại.
         */
        @Test
        @DisplayName("should return 404 when recipient not found")
        void shouldReturn404_whenRecipientNotFound()
                throws Exception {

            UUID unknownUserId = UUID.randomUUID();

            mockMvc.perform(
                    post("/folders/{id}/permissions", folder.getId())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shareRequest(
                            unknownUserId, "VIEW"
                        ))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_USER_001"));
        }

        /*
         * TEST 9: Folder không tồn tại.
         */
        @Test
        @DisplayName("should return 404 when folder not found")
        void shouldReturn404_whenFolderNotFound()
                throws Exception {

            mockMvc.perform(
                    post("/folders/{id}/permissions", UUID.randomUUID())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shareRequest(
                            recipient.getId(), "VIEW"
                        ))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_FOLDER_001"));
        }

        /*
         * TEST 10: Không được tạo quyền trùng.
         *
         * POST lần đầu: 201
         * POST lần hai: 409
         *
         * Kiểm tra vẫn chỉ có một bản ghi.
         */
        @Test
        @DisplayName("should return 409 for duplicate permission")
        void shouldReturn409_whenPermissionAlreadyExists()
                throws Exception {

            String request = shareRequest(
                recipient.getId(), "VIEW"
            );

            mockMvc.perform(
                    post("/folders/{id}/permissions", folder.getId())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request)
                )
                .andExpect(status().isCreated());

            UUID firstPermissionId = permissionRepository
                .findByFolder_IdAndSharedWithUser_Id(
                    folder.getId(), recipient.getId()
                )
                .orElseThrow()
                .getId();

            mockMvc.perform(
                    post("/folders/{id}/permissions", folder.getId())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request)
                )
                .andExpect(status().isConflict());

            var permission = permissionRepository
                .findByFolder_IdAndSharedWithUser_Id(
                    folder.getId(), recipient.getId()
                )
                .orElseThrow();

            assertEquals(firstPermissionId, permission.getId());
            assertEquals(
                PermissionType.VIEW,
                permission.getPermissionType()
            );
        }

        /*
         * TEST 11: Không gửi JWT.
         *
         * API yêu cầu đăng nhập.
         */
        @Test
        @DisplayName("should return 401 without JWT")
        void shouldReturn401_whenUnauthenticated()
                throws Exception {

            mockMvc.perform(
                    post("/folders/{id}/permissions", folder.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shareRequest(
                            recipient.getId(), "VIEW"
                        ))
                )
                .andExpect(status().isUnauthorized());
        }

        /*
         * TEST 12: Owner xem quyền trực tiếp.
         *
         * Kiểm tra cả DTO lồng sharedWithUser theo API Specs.
         */
        @Test
        @DisplayName("should return 200 with direct shares")
        void shouldReturn200_whenOwnerListsShares()
                throws Exception {

            mockMvc.perform(
                    post("/folders/{id}/permissions", folder.getId())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shareRequest(
                            recipient.getId(), "VIEW"
                        ))
                )
                .andExpect(status().isCreated());

            mockMvc.perform(
                    get("/folders/{id}/permissions", folder.getId())
                        .header("Authorization", bearer(ownerToken))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].sharedWithUser.id")
                    .value(recipient.getId().toString()))
                .andExpect(jsonPath("$.data[0].sharedWithUser.email")
                    .value(recipient.getEmail()))
                .andExpect(jsonPath("$.data[0].permissionType").value("VIEW"));
        }

        /*
         * TEST 13: Recipient không phải owner
         * không được xem danh sách chia sẻ.
         */
        @Test
        @DisplayName("should return 403 when non-owner lists shares")
        void shouldReturn403_whenNonOwnerListsShares()
                throws Exception {

            mockMvc.perform(
                    get("/folders/{id}/permissions", folder.getId())
                        .header("Authorization", bearer(recipientToken))
                )
                .andExpect(status().isForbidden());
        }

        // PATCH is an additional API, outside the original Module 3 specs.
        @Test
        @DisplayName("owner can update VIEW to EDIT and back, keeping the same record")
        void ownerCanUpdatePermissionInBothDirections() throws Exception {
            Permission initial = saveShare(folder, PermissionType.VIEW);
            UUID permissionId = initial.getId();

            for (String target : new String[]{"EDIT", "VIEW"}) {
                mockMvc.perform(
                        patch("/permissions/{id}", permissionId)
                            .header("Authorization", bearer(ownerToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateBody(target)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.id").value(permissionId.toString()))
                    .andExpect(jsonPath("$.data.folderId").value(folder.getId().toString()))
                    .andExpect(jsonPath("$.data.sharedWithUserId").value(recipient.getId().toString()))
                    .andExpect(jsonPath("$.data.permissionType").value(target));

                // Flush + clear ensures the database is re-read, not only the managed object.
                entityManager.flush();
                entityManager.clear();
                Permission reloaded = permissionRepository.findById(permissionId).orElseThrow();
                assertEquals(PermissionType.valueOf(target), reloaded.getPermissionType());
                assertEquals(recipient.getId(), reloaded.getSharedWithUser().getId());
                assertEquals(owner.getId(), reloaded.getGrantedBy().getId());

                mockMvc.perform(get("/folders/{id}/permissions", folder.getId())
                        .header("Authorization", bearer(ownerToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value(permissionId.toString()))
                    .andExpect(jsonPath("$.data[0].permissionType").value(target));
            }
        }

        @Test
        void nonOwnerCannotUpdatePermission() throws Exception {
            Permission existing = saveShare(folder, PermissionType.VIEW);
            UUID id = existing.getId();
            mockMvc.perform(patch("/permissions/{id}", id)
                    .header("Authorization", bearer(recipientToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateBody("EDIT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_PERM_003"));
            entityManager.clear();
            assertEquals(PermissionType.VIEW,
                permissionRepository.findById(id).orElseThrow().getPermissionType());
        }

        @Test
        void updatingMissingPermissionReturns404() throws Exception {
            mockMvc.perform(patch("/permissions/{id}", UUID.randomUUID())
                    .header("Authorization", bearer(ownerToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateBody("EDIT")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_PERM_001"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"NONE", "OWNER", "INVALID"})
        void rejectsInvalidUpdatePermission(String type) throws Exception {
            Permission existing = saveShare(folder, PermissionType.VIEW);
            UUID id = existing.getId();
            mockMvc.perform(patch("/permissions/{id}", id)
                    .header("Authorization", bearer(ownerToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateBody(type)))
                .andExpect(status().isBadRequest());
            entityManager.clear();
            assertEquals(PermissionType.VIEW,
                permissionRepository.findById(id).orElseThrow().getPermissionType());
        }

        @Test
        void rejectsMissingUpdatePermissionType() throws Exception {
            Permission existing = saveShare(folder, PermissionType.VIEW);
            mockMvc.perform(patch("/permissions/{id}", existing.getId())
                    .header("Authorization", bearer(ownerToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isBadRequest());
        }

        @Test
        void updateRequiresAuthentication() throws Exception {
            Permission existing = saveShare(folder, PermissionType.VIEW);
            mockMvc.perform(patch("/permissions/{id}", existing.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateBody("EDIT")))
                .andExpect(status().isUnauthorized());
        }

        @Test
        void publicLinkCannotBeChangedToEdit() throws Exception {
            Permission link = permissionRepository.saveAndFlush(Permission.builder()
                .folder(folder)
                .grantedBy(owner)
                .permissionType(PermissionType.VIEW)
                .publicToken(UUID.randomUUID().toString())
                .build());
            UUID id = link.getId();
            mockMvc.perform(patch("/permissions/{id}", id)
                    .header("Authorization", bearer(ownerToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateBody("EDIT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_PERM_002"));
            entityManager.clear();
            assertEquals(PermissionType.VIEW,
                permissionRepository.findById(id).orElseThrow().getPermissionType());
        }

        @ParameterizedTest
        @ValueSource(strings = {"NONE", "OWNER"})
        void grantRejectsKnownButUnsupportedEnumValues(String type) throws Exception {
            mockMvc.perform(post("/folders/{id}/permissions", folder.getId())
                    .header("Authorization", bearer(ownerToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(shareRequest(recipient.getId(), type)))
                .andExpect(status().isBadRequest());
            assertTrue(permissionRepository.findByFolder_IdAndSharedWithUser_Id(
                folder.getId(), recipient.getId()).isEmpty());
        }

        @Test
        void listingSharesExcludesParentChildAndPublicLinkPermissions() throws Exception {
            saveShare(folder, PermissionType.EDIT);
            Folder selected = folderRepository.saveAndFlush(Folder.builder()
                .name("Selected-" + UUID.randomUUID()).owner(owner)
                .parentFolder(folder.getId().toString()).build());
            Folder child = folderRepository.saveAndFlush(Folder.builder()
                .name("Child-" + UUID.randomUUID()).owner(owner)
                .parentFolder(selected.getId().toString()).build());
            Permission direct = saveShare(selected, PermissionType.VIEW);
            saveShare(child, PermissionType.EDIT);
            permissionRepository.saveAndFlush(Permission.builder()
                .folder(selected).grantedBy(owner)
                .permissionType(PermissionType.VIEW)
                .publicToken(UUID.randomUUID().toString()).build());

            mockMvc.perform(get("/folders/{id}/permissions", selected.getId())
                    .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(direct.getId().toString()))
                .andExpect(jsonPath("$.data[0].sharedWithUser.id").value(recipient.getId().toString()))
                .andExpect(jsonPath("$.data[0].sharedWithUser.email").value(recipient.getEmail()))
                .andExpect(jsonPath("$.data[0].permissionType").value("VIEW"));
        }

        @Test
        void ancestorOwnerCannotManageSharesOfAnotherOwnersChild() throws Exception {
            Folder child = folderRepository.saveAndFlush(Folder.builder()
                .name("OtherOwner-" + UUID.randomUUID()).owner(recipient)
                .parentFolder(folder.getId().toString()).build());
            Permission direct = permissionRepository.saveAndFlush(Permission.builder()
                .folder(child).sharedWithUser(owner).grantedBy(recipient)
                .permissionType(PermissionType.VIEW).build());

            mockMvc.perform(patch("/permissions/{id}", direct.getId())
                    .header("Authorization", bearer(ownerToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateBody("EDIT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_PERM_003"));
        }

        private Permission saveShare(Folder target, PermissionType type) {
            return permissionRepository.saveAndFlush(Permission.builder()
                .folder(target).sharedWithUser(recipient).grantedBy(owner)
                .permissionType(type).build());
        }

        private String updateBody(String type) {
            return "{\"permissionType\":\"%s\"}".formatted(type);
        }

        // =========================
        // HELPER METHODS
        // =========================

        private User createUser(String displayName) {
            String email = "u"
        + UUID.randomUUID().toString().substring(0, 8)
        + "@test.com";
            return userRepository.saveAndFlush(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .displayName(displayName)
                .role(Role.USER)
                .status(Status.ACTIVE)
                .plan(Plan.FREE)
                .storageUsed(0L)
                .storageLimit(104857600L)
                .build());
        }

        private String login(String email) throws Exception {
            String request = """
                {
                    "email": "%s",
                    "password": "%s"
                }
                """.formatted(email, PASSWORD);

            String response = mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

            JsonNode json = objectMapper.readTree(response);
            String token = json.path("data").path("accessToken").asString();
            assertFalse(token.isBlank(), "Login response has no access token");
            return token;
        }

        private String bearer(String token) {
            return "Bearer " + token;
        }

        private String shareRequest(UUID userId, String permissionType) {
            return """
                {
                    "sharedWithUserId": "%s",
                    "permissionType": "%s"
                }
                """.formatted(userId, permissionType);
        }
    }
}
