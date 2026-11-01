package com.spring.backend.integration.sharing;

import com.spring.backend.entity.*;
import com.spring.backend.entity.enums.*;
import com.spring.backend.integration.AbstractIntegrationTest;
import com.spring.backend.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Requires Docker. Transactional, sequential tests do not verify lock concurrency. */
@Transactional
class PublicLinkIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired FolderRepository folderRepository;
    @Autowired PermissionRepository permissionRepository;
    @Autowired DocumentRepository documentRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    private static final String PASSWORD = "password123";
    private User owner;
    private Folder folder;
    private String ownerToken;

    @BeforeEach
    void setUp() throws Exception {
        owner = createUser("Owner");
        folder = createFolder("Public-" + UUID.randomUUID(), owner, null);
        ownerToken = login(owner);
    }

    @Test
    void createsReusesReadsRevokesAndRecreatesLinkWithoutDeletingUserShare() throws Exception {
        User recipient = createUser("Recipient");
        Permission direct = permissionRepository.saveAndFlush(Permission.builder()
            .folder(folder).sharedWithUser(recipient).grantedBy(owner)
            .permissionType(PermissionType.EDIT).build());
        JsonNode first = createLink(folder.getId(), ownerToken);
        String token = first.path("publicToken").asString();
        assertTrue(token.matches("[A-Za-z0-9_-]{43}"));
        String url = first.path("publicUrl").asString();
        assertTrue(java.net.URI.create(url).isAbsolute());
        assertTrue(url.endsWith("/public/folders/" + token));
        JsonNode second = createLink(folder.getId(), ownerToken);
        assertEquals(token, second.path("publicToken").asString());
        assertEquals(url, second.path("publicUrl").asString());
        sync();


    sync();

    // Chỉ truy vấn public link của folder đang được kiểm thử,
    // tránh tải các bản ghi quyền không liên quan.
    Permission publicRecord = permissionRepository
        .findByFolder_IdAndSharedWithUserIsNullAndPublicTokenIsNotNull(
            folder.getId()
        )
        .orElseThrow(() ->
            new AssertionError("Public link record not found")
        );

    assertEquals(
        PermissionType.VIEW,
        publicRecord.getPermissionType()
    );
    assertEquals(
        owner.getId(),
        publicRecord.getGrantedBy().getId()
    );

    UUID publicPermissionId = publicRecord.getId();

        read(token);
        revoke(folder.getId(), ownerToken).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.status").value(200));
        sync();
        assertFalse(permissionRepository.existsById(publicPermissionId));
        Permission retained = permissionRepository.findById(direct.getId()).orElseThrow();
        assertEquals(recipient.getId(), retained.getSharedWithUser().getId());
        assertEquals(PermissionType.EDIT, retained.getPermissionType());
        getPublic(token).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ERR_PERM_007"));
        revoke(folder.getId(), ownerToken).andExpect(status().isNotFound());
        String newToken = createLink(folder.getId(), ownerToken).path("publicToken").asString();
        assertNotEquals(token, newToken);
        read(newToken);
        getPublic(token).andExpect(status().isNotFound());
    }

    @Test
    void anonymousReadReturnsOnlyDirectContentsAndSafeMetadata() throws Exception {
        Folder child = createFolder("Child-" + UUID.randomUUID(), owner, folder);
        Folder grandchild = createFolder("Grandchild-" + UUID.randomUUID(), owner, child);
        Folder leaf = createFolder("Leaf-" + UUID.randomUUID(), owner, folder);
        Document visible = createDocument(folder);
        Document excluded = createDocument(child);
        String token = createLink(folder.getId(), ownerToken).path("publicToken").asString();
        JsonNode data = read(token);
        assertEquals(folder.getId().toString(), data.path("folder").path("id").asString());
        assertEquals(folder.getName(), data.path("folder").path("name").asString());
        assertEquals(3, data.path("items").size());
        JsonNode childNode = find(data.path("items"), child.getId());
        assertEquals("FOLDER", childNode.path("itemType").asString());
        assertEquals(child.getName(), childNode.path("name").asString());
        assertTrue(childNode.path("hasChildren").asBoolean());
        assertFalse(find(data.path("items"), leaf.getId()).path("hasChildren").asBoolean());
        JsonNode doc = find(data.path("items"), visible.getId());
        assertEquals("DOCUMENT", doc.path("itemType").asString());
        assertEquals(visible.getName(), doc.path("name").asString());
        assertEquals(visible.getSize().longValue(), doc.path("size").asLong());
        assertEquals(visible.getMimeType(), doc.path("mimeType").asString());
        assertFalse(data.toString().contains(excluded.getId().toString()));
        assertFalse(data.toString().contains(grandchild.getId().toString()));
        assertSafe(data);
    }

    @Test
    void anonymousVisitorCanBrowseSubFoldersAndDownloadDocumentsWithoutJwt() throws Exception {
        Folder child = createFolder("Child-" + UUID.randomUUID(), owner, folder);
        Folder grandchild = createFolder("Grandchild-" + UUID.randomUUID(), owner, child);
        Document inChild = createDocument(child);
        Document inGrandchild = createDocument(grandchild);
        String token = createLink(folder.getId(), ownerToken).path("publicToken").asString();

        JsonNode rootData = read(token);
        JsonNode childItem = find(rootData.path("items"), child.getId());
        assertEquals("folders/" + child.getId(), childItem.path("itemUrl").asString());

        JsonNode childData = objectMapper.readTree(
                getPublicRequest(token, "/folders/" + child.getId())
                        .andExpect(status().isOk())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andReturn().getResponse().getContentAsString())
                .path("data");
        assertEquals(child.getId().toString(), childData.path("folder").path("id").asString());

        JsonNode docItem = find(childData.path("items"), inChild.getId());
        assertFalse(docItem.path("downloadUrl").asString().isBlank());
        assertEquals("documents/" + inChild.getId(), docItem.path("itemUrl").asString());

        getPublicRequest(token, "/documents/" + inChild.getId() + "/download-url")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresIn").value(300));
        getPublicRequest(token, "/folders/" + grandchild.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(inGrandchild.getId().toString()));
    }

    @Test
    void anonymousVisitorCannotEscapePublicTreeOrRevokedLink() throws Exception {
        User otherOwner = createUser("Foreign Owner");
        Folder foreign = createFolder("Foreign-" + UUID.randomUUID(), otherOwner, null);
        Document foreignDoc = createDocument(foreign);
        String token = createLink(folder.getId(), ownerToken).path("publicToken").asString();

        getPublicRequest(token, "/folders/" + foreign.getId()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_PERM_007"));
        getPublicRequest(token, "/documents/" + foreignDoc.getId() + "/download-url")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_DOC_001"));

        Document ownDoc = createDocument(folder);
        revoke(folder.getId(), ownerToken).andExpect(status().isOk());
        getPublicRequest(token, "/documents/" + ownDoc.getId() + "/download-url")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_PERM_007"));
    }

    @Test
    void emptyFolderReturnsEmptyItems() throws Exception {
        String token = createLink(folder.getId(), ownerToken).path("publicToken").asString();
        JsonNode data = read(token);
        assertTrue(data.path("items").isArray());
        assertEquals(0, data.path("items").size());
        assertEquals(0, data.path("totalElements").asInt());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void managementRequiresAuthentication(boolean deleting) throws Exception {
        String token = createLink(folder.getId(), ownerToken).path("publicToken").asString();
        manage(folder.getId(), null, deleting).andExpect(status().isUnauthorized());
        read(token);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NONE", "VIEW", "EDIT"})
    void sharedRecipientCannotManagePublicLink(String type) throws Exception {
        User other = createUser("Other");
        if (!type.equals("NONE")) {
            permissionRepository.saveAndFlush(Permission.builder().folder(folder).sharedWithUser(other)
                .grantedBy(owner).permissionType(PermissionType.valueOf(type)).build());
        }
        String otherToken = login(other);
        String token = createLink(folder.getId(), ownerToken).path("publicToken").asString();
        for (boolean deleting : new boolean[]{false, true}) {
            manage(folder.getId(), otherToken, deleting).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_PERM_003"));
        }
        read(token);
        assertEquals(token, createLink(folder.getId(), ownerToken).path("publicToken").asString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void managementRejectsMissingFolder(boolean deleting) throws Exception {
        manage(UUID.randomUUID(), ownerToken, deleting).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ERR_FOLDER_001"));
    }

    @Test
    void revocationRejectsFolderWithoutPublicLink() throws Exception {
        revoke(folder.getId(), ownerToken).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ERR_PERM_007"));
    }

    @Test
    void unknownTokenReturns404WithoutAuthentication() throws Exception {
        getPublic("missing-" + UUID.randomUUID()).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ERR_PERM_007"));
    }

    
    @ParameterizedTest
    @ValueSource(strings = {"EDIT", "USER_VIEW"})
    void rejectsTokensThatDoNotRepresentPublicViewPermission(
        String kind
    ) throws Exception {

        String token = UUID.randomUUID().toString();

        Permission.PermissionBuilder builder = Permission.builder()
            .folder(folder)
            .grantedBy(owner);

        if ("USER_VIEW".equals(kind)) {
            // Quyền VIEW dành cho người dùng cụ thể.
            // Không gắn publicToken vì đây không phải public link.
            builder.sharedWithUser(createUser("Recipient"))
                .permissionType(PermissionType.VIEW);
        } else {
            // Public link có quyền EDIT không được phép đọc công khai.
            builder.permissionType(PermissionType.EDIT)
                .publicToken(token);
        }

        permissionRepository.saveAndFlush(builder.build());

        getPublic(token)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ERR_PERM_007"));
    }


    @Test
    void sameNamesUnderDifferentParentsResolveIndependently() throws Exception {
        Folder anotherRoot = createFolder("Other-" + UUID.randomUUID(), owner, null);
        Folder homonym = createFolder(folder.getName(), owner, anotherRoot);
        Folder homonymChild = createFolder("Homonym-" + UUID.randomUUID(), owner, homonym);
        createDocument(homonym);
        Folder selectedChild = createFolder("Selected-" + UUID.randomUUID(), owner, folder);
        String token = createLink(folder.getId(), ownerToken).path("publicToken").asString();
        JsonNode data = read(token);
        assertEquals(1, data.path("items").size());
        find(data.path("items"), selectedChild.getId());
        assertFalse(data.toString().contains(homonym.getId().toString()));
        assertFalse(data.toString().contains(homonymChild.getId().toString()));
    }

    @Test
    void sameNamesAcrossOwnersDoNotExposeOtherOwnersChildren() throws Exception {
        User other = createUser("Other Owner");
        Folder otherRoot = createFolder(folder.getName(), other, null);
        createFolder("Secret-" + UUID.randomUUID(), other, otherRoot);
        createDocument(otherRoot);
        Folder visibleChild = createFolder("Visible-" + UUID.randomUUID(), owner, folder);
        String token = createLink(folder.getId(), ownerToken).path("publicToken").asString();
        JsonNode data = read(token);
        assertEquals(1, data.path("items").size());
        find(data.path("items"), visibleChild.getId());
    }

    @Test
    void ancestorOwnerCannotManageAnotherOwnersChild() throws Exception {
        User childOwner = createUser("Child Owner");
        Folder child = createFolder("ForeignChild-" + UUID.randomUUID(), childOwner, folder);
        String childOwnerToken = login(childOwner);
        String token = createLink(child.getId(), childOwnerToken).path("publicToken").asString();
        for (boolean deleting : new boolean[]{false, true}) {
            manage(child.getId(), ownerToken, deleting).andExpect(status().isForbidden());
        }
        read(token);
    }

    private void sync() { entityManager.flush(); entityManager.clear(); }

    private ResultActions manage(UUID id, String jwt, boolean deleting) throws Exception {
        sync();
        var request = deleting ? delete("/folders/{id}/public-link", id)
            : post("/folders/{id}/public-link", id);
        if (jwt != null) request.header("Authorization", "Bearer " + jwt);
        return mockMvc.perform(request);
    }

    private ResultActions revoke(UUID id, String jwt) throws Exception { return manage(id, jwt, true); }

    private JsonNode createLink(UUID id, String jwt) throws Exception {
        String body = manage(id, jwt, false).andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.status").value(201))
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private ResultActions getPublic(String token) throws Exception {
        sync();
        return mockMvc.perform(get("/public/folders/{token}", token));
    }

    private ResultActions getPublicRequest(String token, String path) throws Exception {
        sync();
        return mockMvc.perform(get("/public/folders/{token}" + path, token));
    }

    private JsonNode read(String token) throws Exception {
        String body = getPublic(token).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.items").isArray())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private JsonNode find(JsonNode array, UUID id) {
        for (JsonNode node : array) if (id.toString().equals(node.path("id").asString())) return node;
        return fail("Missing resource " + id);
    }

    private void assertSafe(JsonNode node) {
        for (String field : List.of("ownerId", "owner", "objectKey", "passwordHash", "grantedBy", "publicToken"))
            assertFalse(node.has(field), "Unexpected public field: " + field);
        for (JsonNode child : node) {
            if (child.isObject() || child.isArray()) {
                assertSafe(child);
            }
        }
    }

    private User createUser(String name) {
        return userRepository.saveAndFlush(User.builder()
            .email("u" + UUID.randomUUID().toString().substring(0, 8) + "@test.com")
            .passwordHash(passwordEncoder.encode(PASSWORD)).displayName(name)
            .role(Role.USER).status(Status.ACTIVE).plan(Plan.FREE)
            .storageUsed(0L).storageLimit(104857600L).build());
    }

    private Folder createFolder(String name, User user, Folder parent) {
        return folderRepository.saveAndFlush(Folder.builder().name(name).owner(user)
            .parent(parent).build());
    }

    private Document createDocument(Folder target) {
        return documentRepository.saveAndFlush(Document.builder().name("Report-" + UUID.randomUUID() + ".pdf")
            .folder(target).owner(target.getOwner()).objectKey("private/" + UUID.randomUUID())
            .size(123L).mimeType("application/pdf").build());
    }

    private String login(User user) throws Exception {
        String body = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(user.getEmail(), PASSWORD)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(body).path("data").path("accessToken").asString();
        assertFalse(token.isBlank());
        return token;
    }
}
