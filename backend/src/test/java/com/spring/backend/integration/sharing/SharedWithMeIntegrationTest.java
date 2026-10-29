package com.spring.backend.integration.sharing;

import com.spring.backend.entity.Folder;
import com.spring.backend.entity.Permission;
import com.spring.backend.entity.User;
import com.spring.backend.entity.enums.PermissionType;
import com.spring.backend.entity.enums.Plan;
import com.spring.backend.entity.enums.Role;
import com.spring.backend.entity.enums.Status;
import com.spring.backend.integration.AbstractIntegrationTest;
import com.spring.backend.repository.FolderRepository;
import com.spring.backend.repository.PermissionRepository;
import com.spring.backend.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Exercises /shared-with-me with real authentication, PostgreSQL queries and DTO mapping.
 * Requires Docker for the containers in AbstractIntegrationTest.
 * Run from backend: mvn -Dtest=SharedWithMeIntegrationTest test
 */
@Transactional
class SharedWithMeIntegrationTest extends AbstractIntegrationTest {

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
    private PermissionRepository permissionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User owner;
    private User recipient;
    private Folder folder;
    private String recipientToken;

    @BeforeEach
    void setUp() throws Exception {
        owner = createUser("Owner");
        recipient = createUser("Bình");
        folder = createFolder("Shared Folder", owner, null);
        recipientToken = login(recipient);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/shared-with-me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsEmptyListWhenNothingIsShared() throws Exception {
        assertEquals(0, readShares(recipientToken).size());
    }

    @Test
    void returnsDirectViewAndEditShares() throws Exception {
        Folder editable = createFolder("Editable", owner, null);
        saveShare(folder, recipient, owner, PermissionType.VIEW);
        saveShare(editable, recipient, owner, PermissionType.EDIT);

        JsonNode shares = readShares(recipientToken);

        assertEquals(2, shares.size());
        assertShare(findShare(shares, folder), folder, PermissionType.VIEW, owner);
        assertShare(findShare(shares, editable), editable, PermissionType.EDIT, owner);
    }

    @Test
    void excludesSharesForOtherRecipients() throws Exception {
        User otherRecipient = createUser("Other Recipient");
        Folder otherFolder = createFolder("Other Recipient's Folder", owner, null);
        saveShare(folder, recipient, owner, PermissionType.VIEW);
        saveShare(otherFolder, otherRecipient, owner, PermissionType.EDIT);

        JsonNode shares = readShares(recipientToken);

        assertEquals(1, shares.size());
        assertShare(shares.get(0), folder, PermissionType.VIEW, owner);
    }

    @Test
    void excludesPublicLinks() throws Exception {
        Folder publicFolder = createFolder("Public Folder", owner, null);
        Permission publicLink = permissionRepository.saveAndFlush(Permission.builder()
            .folder(publicFolder).grantedBy(owner).permissionType(PermissionType.VIEW)
            .publicToken(UUID.randomUUID().toString()).build());
        saveShare(folder, recipient, owner, PermissionType.EDIT);

        JsonNode shares = readShares(recipientToken);

        assertEquals(1, shares.size());
        assertShare(shares.get(0), folder, PermissionType.EDIT, owner);
        assertTrue(permissionRepository.existsById(publicLink.getId()));
    }

    @ParameterizedTest
    @EnumSource(value = PermissionType.class, names = {"NONE", "OWNER"})
    void excludesUnsupportedPermissionTypes(PermissionType unsupported) throws Exception {
        Folder excluded = createFolder("Unsupported Permission", owner, null);
        saveShare(excluded, recipient, owner, unsupported);
        saveShare(folder, recipient, owner, PermissionType.VIEW);

        JsonNode shares = readShares(recipientToken);

        assertEquals(1, shares.size());
        assertShare(shares.get(0), folder, PermissionType.VIEW, owner);
    }

    @Test
    void doesNotExpandInheritedChildFolders() throws Exception {
        createFolder("Child", owner, folder);
        saveShare(folder, recipient, owner, PermissionType.EDIT);

        JsonNode shares = readShares(recipientToken);

        assertEquals(1, shares.size());
        assertShare(shares.get(0), folder, PermissionType.EDIT, owner);
    }

    @Test
    void returnsChildWhenItIsAlsoSharedDirectly() throws Exception {
        Folder child = createFolder("Child", owner, folder);
        saveShare(folder, recipient, owner, PermissionType.EDIT);
        saveShare(child, recipient, owner, PermissionType.VIEW);

        JsonNode shares = readShares(recipientToken);

        assertEquals(2, shares.size());
        assertShare(findShare(shares, folder), folder, PermissionType.EDIT, owner);
        // This list reports the direct share, even when EDIT is inherited from the parent.
        assertShare(findShare(shares, child), child, PermissionType.VIEW, owner);
    }

    @Test
    void returnsSharesFromDifferentGrantors() throws Exception {
        User otherGrantor = createUser("Other Grantor");
        Folder otherFolder = createFolder("Other Grantor's Folder", otherGrantor, null);
        saveShare(folder, recipient, owner, PermissionType.VIEW);
        saveShare(otherFolder, recipient, otherGrantor, PermissionType.EDIT);

        JsonNode shares = readShares(recipientToken);

        assertEquals(2, shares.size());
        assertShare(findShare(shares, folder), folder, PermissionType.VIEW, owner);
        assertShare(findShare(shares, otherFolder), otherFolder, PermissionType.EDIT, otherGrantor);
    }

    @Test
    void ordersSharesByCreatedAtDescending() throws Exception {
        Folder newestFolder = createFolder("Newest", owner, null);
        Folder oldestFolder = createFolder("Oldest", owner, null);
        // Insert in neither ascending nor descending timestamp order.
        Permission middle = saveShare(folder, recipient, owner, PermissionType.VIEW);
        Permission newest = saveShare(newestFolder, recipient, owner, PermissionType.EDIT);
        Permission oldest = saveShare(oldestFolder, recipient, owner, PermissionType.VIEW);
        Instant base = Instant.parse("2024-01-01T00:00:00Z");
        setCreatedAt(middle, base.plusSeconds(60));
        setCreatedAt(newest, base.plusSeconds(120));
        setCreatedAt(oldest, base);

        JsonNode shares = readShares(recipientToken);

        assertEquals(3, shares.size());
        assertShare(shares.get(0), newestFolder, PermissionType.EDIT, owner);
        assertShare(shares.get(1), folder, PermissionType.VIEW, owner);
        assertShare(shares.get(2), oldestFolder, PermissionType.VIEW, owner);
    }

    @Test
    void grantingAndUpdatingPermissionChangesSharedWithMe() throws Exception {
        String ownerToken = login(owner);
        User otherUser = createUser("Other User");
        String otherToken = login(otherUser);
        String grantedBody = mockMvc.perform(post("/folders/{id}/permissions", folder.getId())
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sharedWithUserId":"%s","permissionType":"VIEW"}
                    """.formatted(recipient.getId())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.permissionType").value("VIEW"))
            .andReturn().getResponse().getContentAsString();
        UUID permissionId = UUID.fromString(
            objectMapper.readTree(grantedBody).path("data").path("id").asString());

        JsonNode shares = readShares(recipientToken);
        assertEquals(1, shares.size());
        assertShare(shares.get(0), folder, PermissionType.VIEW, owner);

        mockMvc.perform(patch("/permissions/{id}", permissionId)
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissionType\":\"EDIT\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(permissionId.toString()))
            .andExpect(jsonPath("$.data.permissionType").value("EDIT"));

        shares = readShares(recipientToken);
        assertEquals(1, shares.size());
        assertShare(shares.get(0), folder, PermissionType.EDIT, owner);
        Permission persisted = permissionRepository.findByFolder_IdAndSharedWithUser_Id(
            folder.getId(), recipient.getId()).orElseThrow();
        assertEquals(permissionId, persisted.getId());
        assertEquals(PermissionType.EDIT, persisted.getPermissionType());
        assertEquals(0, readShares(otherToken).size());
    }

    private User createUser(String displayName) {
        return userRepository.saveAndFlush(User.builder()
            .email("u" + UUID.randomUUID().toString().substring(0, 8) + "@test.com")
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .displayName(displayName).role(Role.USER).status(Status.ACTIVE).plan(Plan.FREE)
            .storageUsed(0L).storageLimit(104857600L).build());
    }

    private Folder createFolder(String name, User folderOwner, Folder parent) {
        return folderRepository.saveAndFlush(Folder.builder()
            .name(name + "-" + UUID.randomUUID()).owner(folderOwner)
            .parentFolder(parent == null ? null : parent.getId().toString()).build());
    }

    private Permission saveShare(Folder target, User sharedWith, User grantor, PermissionType type) {
        return permissionRepository.saveAndFlush(Permission.builder()
            .folder(target).sharedWithUser(sharedWith).grantedBy(grantor)
            .permissionType(type).build());
    }

    private void setCreatedAt(Permission permission, Instant createdAt) {
        // Auditing sets createdAt on insert and JPA marks it updatable=false.
        // Set a deterministic persisted timestamp directly for this ordering fixture.
        entityManager.flush();
        assertEquals(1, jdbcTemplate.update("UPDATE permissions SET created_at = ? WHERE id = ?",
            Timestamp.from(createdAt), permission.getId()));
    }

    private String login(User user) throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"%s"}
                    """.formatted(user.getEmail(), PASSWORD)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(body).path("data").path("accessToken").asString();
        assertFalse(token.isBlank(), "Login response has no access token");
        return token;
    }

    private JsonNode readShares(String token) throws Exception {
        entityManager.flush();
        entityManager.clear();
        String body = mockMvc.perform(get("/shared-with-me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data").isArray())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private JsonNode findShare(JsonNode shares, Folder target) {
        for (JsonNode share : shares) {
            if (target.getId().toString().equals(share.path("folderId").asString())) {
                return share;
            }
        }
        return fail("Missing folder " + target.getId() + " in " + shares);
    }

    private void assertShare(JsonNode actual, Folder expectedFolder,
                             PermissionType expectedType, User grantor) {
        assertAll(
            () -> assertEquals(expectedFolder.getId().toString(), actual.path("folderId").asString()),
            () -> assertEquals(expectedFolder.getName(), actual.path("name").asString()),
            () -> assertEquals(expectedType.name(), actual.path("permissionType").asString()),
            () -> assertEquals(grantor.getEmail(), actual.path("sharedBy").asString())
        );
    }
}
