package com.spring.backend.integration.sharing;

import com.spring.backend.entity.Document;
import com.spring.backend.entity.Folder;
import com.spring.backend.entity.Permission;
import com.spring.backend.entity.User;
import com.spring.backend.entity.enums.PermissionType;
import com.spring.backend.entity.enums.Plan;
import com.spring.backend.entity.enums.Role;
import com.spring.backend.entity.enums.Status;
import com.spring.backend.integration.AbstractIntegrationTest;
import com.spring.backend.repository.DocumentRepository;
import com.spring.backend.repository.FolderRepository;
import com.spring.backend.repository.PermissionRepository;
import com.spring.backend.repository.UserRepository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/*
 * Exercises sharing details through HTTP, real repositories and the resolver.
 * Scope: user shares with VIEW/EDIT; documents inherit all shares from folders.
 * Requires Docker for PostgreSQL and RustFS in AbstractIntegrationTest.
 * Run from backend: .\mvnw.cmd -Dtest=SharingDetailsIntegrationTest test
 */
@Transactional
class SharingDetailsIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    private User owner;
    private User recipient;
    private Folder folder;
    private Document document;
    private String ownerToken;
    private String recipientToken;

    @BeforeEach
    void setUp() throws Exception {
        owner = createUser("Owner");
        recipient = createUser("Recipient");
        folder = createFolder("Selected", null);
        document = documentRepository.saveAndFlush(Document.builder()
            .name("Document-" + UUID.randomUUID() + ".txt")
            .folder(folder)
            .owner(owner)
            .objectKey("sharing-details/" + UUID.randomUUID())
            .size(10L)
            .mimeType("text/plain")
            .build());
        ownerToken = login(owner);
        recipientToken = login(recipient);
    }

    @Nested
    @DisplayName("Folder sharing details")
    class FolderSharingDetailsTests {

        @Test
        @DisplayName("returns resource metadata and no users when there are no shares")
        void returnsEmptySharingDetails() throws Exception {
            JsonNode data = readDetails("folders", folder.getId());

            assertResource(data, folder.getId(), folder.getName(), owner);
            assertEquals(0, data.path("users").size());
        }

        @ParameterizedTest
        @EnumSource(value = PermissionType.class, names = {"VIEW", "EDIT"})
        @DisplayName("returns a direct share with full recipient and source details")
        void returnsDirectPermission(PermissionType type) throws Exception {
            Permission direct = saveShare(folder, recipient, type);

            JsonNode data = readDetails("folders", folder.getId());

            assertResource(data, folder.getId(), folder.getName(), owner);
            assertEquals(1, data.path("users").size());
            JsonNode user = assertUser(data, recipient, type);
            assertEquals(1, user.path("sources").size());
            assertSource(user, direct, false);
        }

        @ParameterizedTest
        @EnumSource(value = PermissionType.class, names = {"VIEW", "EDIT"})
        @DisplayName("inherits shares through multiple folder levels")
        void inheritsRootPermission(PermissionType type) throws Exception {
            Folder root = createFolder("Root", null);
            Folder parent = createFolder("Parent", root);
            folder.setParentFolder(parent.getId().toString());
            Permission inherited = saveShare(root, recipient, type);

            JsonNode data = readDetails("folders", folder.getId());

            assertEquals(1, data.path("users").size());
            JsonNode user = assertUser(data, recipient, type);
            assertEquals(1, user.path("sources").size());
            assertSource(user, inherited, true);
        }

        @ParameterizedTest
        @CsvSource({"VIEW, VIEW, VIEW", "VIEW, EDIT, EDIT",
                    "EDIT, VIEW, EDIT", "EDIT, EDIT, EDIT"})
        @DisplayName("uses the strongest direct or inherited permission and retains both sources")
        void combinesDirectAndInheritedPermissions(PermissionType directType,
                                                   PermissionType parentType,
                                                   PermissionType expected) throws Exception {
            Folder parent = createFolder("Parent", null);
            folder.setParentFolder(parent.getId().toString());
            Permission inherited = saveShare(parent, recipient, parentType);
            Permission direct = saveShare(folder, recipient, directType);

            JsonNode data = readDetails("folders", folder.getId());

            assertEquals(1, data.path("users").size());
            JsonNode user = assertUser(data, recipient, expected);
            assertEquals(2, user.path("sources").size());
            assertSource(user, direct, false);
            assertSource(user, inherited, true);
        }

        @Test
        @DisplayName("groups shares by recipient without mixing permissions or sources")
        void groupsMultipleRecipients() throws Exception {
            User secondRecipient = createUser("Second Recipient");
            Folder parent = createFolder("Parent", null);
            folder.setParentFolder(parent.getId().toString());
            Permission firstDirect = saveShare(folder, recipient, PermissionType.VIEW);
            Permission firstInherited = saveShare(parent, recipient, PermissionType.EDIT);
            Permission secondDirect = saveShare(folder, secondRecipient, PermissionType.VIEW);

            JsonNode data = readDetails("folders", folder.getId());

            assertEquals(2, data.path("users").size());
            JsonNode first = assertUser(data, recipient, PermissionType.EDIT);
            assertEquals(2, first.path("sources").size());
            assertSource(first, firstDirect, false);
            assertSource(first, firstInherited, true);
            JsonNode second = assertUser(data, secondRecipient, PermissionType.VIEW);
            assertEquals(1, second.path("sources").size());
            assertSource(second, secondDirect, false);
        }

        @Test
        @DisplayName("excludes child, sibling, unrelated folder and public link shares")
        void excludesPermissionsOutsideSelectedHierarchy() throws Exception {
            Folder parent = createFolder("Parent", null);
            folder.setParentFolder(parent.getId().toString());
            Folder child = createFolder("Child", folder);
            Folder sibling = createFolder("Sibling", parent);
            Folder unrelated = createFolder("Unrelated", null);
            User excludedRecipient = createUser("Excluded Recipient");
            Permission direct = saveShare(folder, recipient, PermissionType.VIEW);
            saveShare(child, recipient, PermissionType.EDIT);
            saveShare(sibling, recipient, PermissionType.EDIT);
            saveShare(unrelated, excludedRecipient, PermissionType.EDIT);
            savePublicLink(folder);
            savePublicLink(parent);

            JsonNode data = readDetails("folders", folder.getId());

            assertEquals(1, data.path("users").size());
            JsonNode user = assertUser(data, recipient, PermissionType.VIEW);
            assertEquals(1, user.path("sources").size());
            assertSource(user, direct, false);
        }

        @Test
        @DisplayName("reflects an updated ancestor permission in both folder and document details")
        void reflectsPermissionChangesFromDatabase() throws Exception {
            Folder parent = createFolder("Parent", null);
            folder.setParentFolder(parent.getId().toString());
            saveShare(folder, recipient, PermissionType.VIEW);
            Permission inherited = saveShare(parent, recipient, PermissionType.VIEW);

            for (PermissionType target : new PermissionType[]{PermissionType.EDIT, PermissionType.VIEW}) {
                mockMvc.perform(patch("/permissions/{id}", inherited.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionType\":\"%s\"}".formatted(target)))
                    .andExpect(status().isOk());

                for (String resource : new String[]{"folders", "documents"}) {
                    UUID id = resource.equals("folders") ? folder.getId() : document.getId();
                    JsonNode user = assertUser(readDetails(resource, id), recipient, target);
                    assertEquals(2, user.path("sources").size());
                    JsonNode source = findById(user.path("sources"), "permissionId", inherited.getId());
                    assertEquals(target.name(), source.path("permission").asString());
                    assertTrue(source.path("inherited").asBoolean());
                }
            }
        }
    }

    @Nested
    @DisplayName("Sharing details after revoking permissions")
    class RevokePermissionDetailsTests {

        @Test
        @DisplayName("parent VIEW survives child EDIT revocation until its own source is revoked")
        void revocationRecalculatesFolderAndDocumentPermissionsFromRemainingSources() throws Exception {
            Folder parent = createFolder("Parent", null);
            folder.setParentFolder(parent.getId().toString());
            Permission inherited = saveShare(parent, recipient, PermissionType.VIEW);

            assertSharingState(PermissionType.VIEW, inherited, null);

            Permission direct = saveShare(folder, recipient, PermissionType.EDIT);
            assertSharingState(PermissionType.EDIT, inherited, direct);

            revoke(direct.getId());
            assertFalse(permissionRepository.existsById(direct.getId()));
            assertTrue(permissionRepository.existsById(inherited.getId()));
            assertSharingState(PermissionType.VIEW, inherited, null);

            revoke(inherited.getId());
            assertFalse(permissionRepository.existsById(inherited.getId()));
            assertFalse(permissionRepository.existsById(direct.getId()));
            for (String resource : new String[]{"folders", "documents"}) {
                JsonNode data = readDetails(resource, resourceId(resource));
                assertResource(data, resourceId(resource),
                    resource.equals("folders") ? folder.getName() : document.getName(), owner);
                assertEquals(0, data.path("users").size(),
                    resource + " must have no recipients after the last source is revoked");
            }
        }

        private void assertSharingState(PermissionType effective, Permission inherited,
                                        Permission direct) throws Exception {
            for (String resource : new String[]{"folders", "documents"}) {
                JsonNode data = readDetails(resource, resourceId(resource));
                assertEquals(1, data.path("users").size(), resource);
                JsonNode user = assertUser(data, recipient, effective);
                assertEquals(direct == null ? 1 : 2, user.path("sources").size(), resource);
                assertSource(user, inherited, true);
                if (direct != null) {
                    // A containing-folder share is inherited for documents, direct for folders.
                    assertSource(user, direct, resource.equals("documents"));
                }
            }
        }

        private void revoke(UUID permissionId) throws Exception {
            entityManager.flush();
            entityManager.clear();
            mockMvc.perform(delete("/permissions/{id}", permissionId)
                    .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value(200));
            entityManager.flush();
            entityManager.clear();
        }
    }

    @Nested
    @DisplayName("Document sharing details")
    class DocumentSharingDetailsTests {

        @Test
        void returnsDocumentMetadataAndEmptyUsersWhenNoSharesExist() throws Exception {
            JsonNode data = readDetails("documents", document.getId());

            assertResource(data, document.getId(), document.getName(), owner);
            assertEquals(0, data.path("users").size());
        }

        @ParameterizedTest
        @EnumSource(value = PermissionType.class, names = {"VIEW", "EDIT"})
        @DisplayName("marks shares on the containing folder as inherited for the document")
        void inheritsContainingFolderPermission(PermissionType type) throws Exception {
            Permission inherited = saveShare(folder, recipient, type);

            JsonNode data = readDetails("documents", document.getId());

            assertResource(data, document.getId(), document.getName(), owner);
            assertEquals(1, data.path("users").size());
            JsonNode user = assertUser(data, recipient, type);
            assertEquals(1, user.path("sources").size());
            assertSource(user, inherited, true);
        }

        @Test
        @DisplayName("merges all ancestor shares and marks every source inherited")
        void inheritsStrongestPermissionAcrossAllLevels() throws Exception {
            Folder root = createFolder("Root", null);
            Folder parent = createFolder("Parent", root);
            folder.setParentFolder(parent.getId().toString());
            Permission rootShare = saveShare(root, recipient, PermissionType.VIEW);
            Permission parentShare = saveShare(parent, recipient, PermissionType.EDIT);
            Permission folderShare = saveShare(folder, recipient, PermissionType.VIEW);
            Folder child = createFolder("Child", folder);
            saveShare(child, createUser("Child Recipient"), PermissionType.EDIT);
            savePublicLink(folder);

            JsonNode data = readDetails("documents", document.getId());

            assertEquals(1, data.path("users").size());
            JsonNode user = assertUser(data, recipient, PermissionType.EDIT);
            assertEquals(3, user.path("sources").size());
            assertSource(user, rootShare, true);
            assertSource(user, parentShare, true);
            assertSource(user, folderShare, true);
        }

        @Test
        @DisplayName("authorizes the document owner when the containing folder has another owner")
        void checksDocumentOwnershipIndependentlyOfFolderOwnership() throws Exception {
            document.setOwner(recipient);
            Permission inherited = saveShare(folder, recipient, PermissionType.VIEW);

            details("documents", document.getId(), ownerToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_DOC_003"));

            JsonNode data = responseData(details("documents", document.getId(), recipientToken));
            assertResource(data, document.getId(), document.getName(), recipient);
            assertEquals(1, data.path("users").size());
            JsonNode user = assertUser(data, recipient, PermissionType.VIEW);
            assertEquals(1, user.path("sources").size());
            assertSource(user, inherited, true);
        }
    }

    @Nested
    @DisplayName("Sharing details access and missing resources")
    class AccessTests {

        @ParameterizedTest
        @ValueSource(strings = {"folders", "documents"})
        void rejectsUnauthenticatedRequests(String resource) throws Exception {
            details(resource, resourceId(resource), null)
                .andExpect(status().isUnauthorized());
        }

        @ParameterizedTest
        @CsvSource({"folders, ERR_PERM_003", "documents, ERR_DOC_003"})
        void rejectsNonOwnerWithoutShares(String resource, String errorCode) throws Exception {
            details(resource, resourceId(resource), recipientToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(errorCode));
        }

        @ParameterizedTest
        @CsvSource({"folders, VIEW, ERR_PERM_003", "folders, EDIT, ERR_PERM_003",
                    "documents, VIEW, ERR_DOC_003", "documents, EDIT, ERR_DOC_003"})
        @DisplayName("VIEW/EDIT recipients cannot read the owner's sharing details")
        void rejectsSharedRecipients(String resource, PermissionType type, String errorCode)
                throws Exception {
            saveShare(folder, recipient, type);

            details(resource, resourceId(resource), recipientToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(errorCode));
        }

        @ParameterizedTest
        @CsvSource({"folders, ERR_FOLDER_001", "documents, ERR_DOC_001"})
        void returns404ForMissingResource(String resource, String errorCode) throws Exception {
            details(resource, UUID.randomUUID(), ownerToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(errorCode));
        }
        @Test
        @DisplayName("ancestor ownership does not authorize child sharing management")
        void rejectsAncestorOwnerWhenChildHasAnotherOwner() throws Exception {
            Folder parent = createFolder("Parent", null);

            // Owner sở hữu cha, recipient sở hữu folder con.
            folder.setParentFolder(parent.getId().toString());
            folder.setOwner(recipient);

            details("folders", folder.getId(), ownerToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_PERM_003"));

            // Owner trực tiếp của folder con vẫn được xem.
            JsonNode data = responseData(
                details("folders", folder.getId(), recipientToken)
            );

            assertResource(
                data,
                folder.getId(),
                folder.getName(),
                recipient
            );
            assertEquals(0, data.path("users").size());
        }
    }

    private User createUser(String displayName) {
        return userRepository.saveAndFlush(User.builder()
            .email("u" + UUID.randomUUID().toString().substring(0, 8) + "@test.com")
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .displayName(displayName)
            .role(Role.USER)
            .status(Status.ACTIVE)
            .plan(Plan.FREE)
            .storageUsed(0L)
            .storageLimit(104857600L)
            .build());
    }

    private Folder createFolder(String name, Folder parent) {
        return folderRepository.saveAndFlush(Folder.builder()
            .name(name + "-" + UUID.randomUUID())
            .owner(owner)
            .parentFolder(parent == null ? null : parent.getId().toString())
            .build());
    }

    private Permission saveShare(Folder target, User user, PermissionType type) {
        return permissionRepository.saveAndFlush(Permission.builder()
            .folder(target)
            .sharedWithUser(user)
            .grantedBy(owner)
            .permissionType(type)
            .build());
    }

    private void savePublicLink(Folder target) {
        permissionRepository.saveAndFlush(Permission.builder()
            .folder(target)
            .grantedBy(owner)
            .permissionType(PermissionType.VIEW)
            .publicToken(UUID.randomUUID().toString())
            .build());
    }

    private String login(User user) throws Exception {
        String response = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}"
                    .formatted(user.getEmail(), PASSWORD)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(response).path("data").path("accessToken").asString();
        assertFalse(token.isBlank(), "Login response has no access token");
        return token;
    }

    private UUID resourceId(String resource) {
        return resource.equals("folders") ? folder.getId() : document.getId();
    }

    private ResultActions details(String resource, UUID id, String token) throws Exception {
        // Read persisted entities instead of relying on the fixture's managed objects.
        entityManager.flush();
        entityManager.clear();
        var request = get("/{resource}/{id}/sharing-details", resource, id);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private JsonNode readDetails(String resource, UUID id) throws Exception {
        return responseData(details(resource, id, ownerToken));
    }

    private JsonNode responseData(ResultActions response) throws Exception {
        String body = response.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.users").isArray())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private void assertResource(JsonNode data, UUID id, String name, User expectedOwner) {
        assertAll(
            () -> assertEquals(id.toString(), data.path("resourceId").asString()),
            () -> assertEquals(name, data.path("resourceName").asString()),
            () -> assertEquals(expectedOwner.getId().toString(), data.path("ownerId").asString())
        );
    }

    private JsonNode assertUser(JsonNode data, User expected, PermissionType type) {
        JsonNode user = findById(data.path("users"), "userId", expected.getId());
        assertAll(
            () -> assertEquals(expected.getDisplayName(), user.path("userName").asString()),
            () -> assertEquals(expected.getEmail(), user.path("userEmail").asString()),
            () -> assertEquals(type.name(), user.path("effectivePermission").asString()),
            () -> assertTrue(user.path("sources").isArray())
        );
        return user;
    }

    private void assertSource(JsonNode user, Permission expected, boolean inherited) {
        JsonNode source = findById(user.path("sources"), "permissionId", expected.getId());
        assertAll(
            () -> assertEquals(expected.getFolder().getId().toString(), source.path("folderId").asString()),
            () -> assertEquals(expected.getFolder().getName(), source.path("folderName").asString()),
            () -> assertEquals(expected.getPermissionType().name(), source.path("permission").asString()),
            () -> assertTrue(source.path("inherited").isBoolean()),
            () -> assertEquals(inherited, source.path("inherited").asBoolean()),
            () -> assertEquals(expected.getGrantedBy().getId().toString(), source.path("grantedById").asString())
        );
    }

    private JsonNode findById(JsonNode array, String field, UUID id) {
        assertTrue(array.isArray(), "Expected an array for " + field);
        for (JsonNode node : array) {
            if (id.toString().equals(node.path(field).asString())) {
                return node;
            }
        }
        return fail("Missing " + field + "=" + id + " in " + array);
    }
}
