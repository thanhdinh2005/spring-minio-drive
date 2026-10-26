package com.spring.backend.service.impl;

import com.spring.backend.dto.sharing.EffectivePermissionResponse;
import com.spring.backend.dto.sharing.PermissionSourceResponse;
import com.spring.backend.entity.Document;
import com.spring.backend.entity.Folder;
import com.spring.backend.entity.Permission;
import com.spring.backend.entity.User;
import com.spring.backend.entity.enums.PermissionType;
import com.spring.backend.exception.AppException;
import com.spring.backend.exception.ErrorCode;
import com.spring.backend.repository.FolderRepository;
import com.spring.backend.repository.PermissionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/*
 * Unit tests using mocked repositories; no Spring context or database required.
 * Run from backend: .\mvnw.cmd -Dtest=SharingPermissionResolverTest test
 */
@ExtendWith(MockitoExtension.class)
class SharingPermissionResolverTest {

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @InjectMocks
    private SharingPermissionResolver resolver;

    private User owner;
    private User recipient;
    private Folder folder;

    @BeforeEach
    void setUp() {
        owner = user("Owner", "owner@test.com");
        recipient = user("Recipient", "recipient@test.com");
        folder = folder("Selected Folder");
    }

    private User user(String name, String email) {
        return User.builder()
            .id(UUID.randomUUID())
            .displayName(name)
            .email(email)
            .build();
    }

    private Folder folder(String name) {
        return Folder.builder()
            .id(UUID.randomUUID())
            .name(name)
            .owner(owner)
            .build();
    }

    private Permission permission(Folder source, User target, PermissionType type) {
        return Permission.builder()
            .id(UUID.randomUUID())
            .folder(source)
            .sharedWithUser(target)
            .grantedBy(owner)
            .permissionType(type)
            .build();
    }

    private void mockParent(Folder child, Folder parent) {
        child.setParentFolder(parent.getId().toString());
        when(folderRepository.findById(parent.getId()))
            .thenReturn(Optional.of(parent));
    }

    private void mockPermissions(List<UUID> folderIds, Permission... permissions) {
        when(permissionRepository
            .findByFolder_IdInAndSharedWithUserIsNotNullOrderByCreatedAtDesc(folderIds))
            .thenReturn(List.of(permissions));
    }

    private void assertRecipient(EffectivePermissionResponse response, User expected,
                                 PermissionType expectedPermission) {
        assertAll(
            () -> assertEquals(expected.getId(), response.getUserId()),
            () -> assertEquals(expected.getDisplayName(), response.getUserName()),
            () -> assertEquals(expected.getEmail(), response.getUserEmail()),
            () -> assertEquals(expectedPermission, response.getEffectivePermission())
        );
    }

    private void assertSource(PermissionSourceResponse response, Permission expected,
                              boolean inherited) {
        assertAll(
            () -> assertEquals(expected.getId(), response.getPermissionId()),
            () -> assertEquals(expected.getFolder().getId(), response.getFolderId()),
            () -> assertEquals(expected.getFolder().getName(), response.getFolderName()),
            () -> assertEquals(expected.getPermissionType(), response.getPermission()),
            () -> assertEquals(inherited, response.isInherited()),
            () -> assertEquals(expected.getGrantedBy().getId(), response.getGrantedById())
        );
    }

    @Nested
    @DisplayName("Resolve folder permissions")
    class ResolveForFolderTests {

        @Test
        @DisplayName("returns an empty list when the folder has no shares")
        void returnsEmptyListWhenNoSharesExist() {
            mockPermissions(List.of(folder.getId()));

            List<EffectivePermissionResponse> result = resolver.resolveForFolder(folder);

            assertNotNull(result);
            assertTrue(result.isEmpty());
            verifyNoInteractions(folderRepository);
        }

        @ParameterizedTest
        @EnumSource(value = PermissionType.class, names = {"VIEW", "EDIT"})
        @DisplayName("maps direct permissions and recipient details")
        void returnsDirectPermission(PermissionType type) {
            Permission direct = permission(folder, recipient, type);
            mockPermissions(List.of(folder.getId()), direct);

            List<EffectivePermissionResponse> result = resolver.resolveForFolder(folder);

            assertEquals(1, result.size());
            assertRecipient(result.get(0), recipient, type);
            assertEquals(1, result.get(0).getSources().size());
            assertSource(result.get(0).getSources().get(0), direct, false);
            verifyNoInteractions(folderRepository);
        }

        @Test
        @DisplayName("collects all ancestors and includes shares from the root")
        void inheritsPermissionFromRootThroughMultipleLevels() {
            Folder parent = folder("Parent");
            Folder root = folder("Root");
            mockParent(folder, parent);
            mockParent(parent, root);
            Permission inherited = permission(root, recipient, PermissionType.EDIT);
            List<UUID> folderIds = List.of(folder.getId(), parent.getId(), root.getId());
            mockPermissions(folderIds, inherited);

            List<EffectivePermissionResponse> result = resolver.resolveForFolder(folder);

            assertEquals(1, result.size());
            assertRecipient(result.get(0), recipient, PermissionType.EDIT);
            assertEquals(1, result.get(0).getSources().size());
            assertSource(result.get(0).getSources().get(0), inherited, true);
            verify(folderRepository).findById(parent.getId());
            verify(folderRepository).findById(root.getId());
            verify(permissionRepository)
                .findByFolder_IdInAndSharedWithUserIsNotNullOrderByCreatedAtDesc(folderIds);
        }

        @ParameterizedTest
        @CsvSource({
            "VIEW, VIEW, VIEW",
            "VIEW, EDIT, EDIT",
            "EDIT, VIEW, EDIT",
            "EDIT, EDIT, EDIT"
        })
        @DisplayName("combines direct and inherited shares using the strongest permission")
        void combinesDirectAndInheritedPermissions(PermissionType directType,
                                                   PermissionType inheritedType,
                                                   PermissionType expected) {
            Folder parent = folder("Parent");
            mockParent(folder, parent);
            Permission direct = permission(folder, recipient, directType);
            Permission inherited = permission(parent, recipient, inheritedType);
            mockPermissions(List.of(folder.getId(), parent.getId()), direct, inherited);

            List<EffectivePermissionResponse> result = resolver.resolveForFolder(folder);

            assertEquals(1, result.size());
            assertRecipient(result.get(0), recipient, expected);
            assertEquals(2, result.get(0).getSources().size());
            assertSource(result.get(0).getSources().get(0), direct, false);
            assertSource(result.get(0).getSources().get(1), inherited, true);
        }

        @Test
        @DisplayName("groups by user ID and preserves repository order for users and sources")
        void groupsUsersWithoutMixingTheirPermissionsOrSources() {
            User otherRecipient = user("Other Recipient", "other@test.com");
            User sameRecipient = User.builder()
                .id(recipient.getId())
                .displayName(recipient.getDisplayName())
                .email(recipient.getEmail())
                .build();
            Folder parent = folder("Parent");
            mockParent(folder, parent);
            Permission first = permission(parent, recipient, PermissionType.EDIT);
            Permission second = permission(folder, otherRecipient, PermissionType.VIEW);
            Permission third = permission(folder, sameRecipient, PermissionType.VIEW);
            User otherGrantor = user("Other Grantor", "grantor@test.com");
            third.setGrantedBy(otherGrantor);
            mockPermissions(List.of(folder.getId(), parent.getId()), first, second, third);

            List<EffectivePermissionResponse> result = resolver.resolveForFolder(folder);

            assertEquals(2, result.size());
            assertRecipient(result.get(0), recipient, PermissionType.EDIT);
            assertRecipient(result.get(1), otherRecipient, PermissionType.VIEW);
            assertEquals(2, result.get(0).getSources().size());
            assertSource(result.get(0).getSources().get(0), first, true);
            assertSource(result.get(0).getSources().get(1), third, false);
            assertEquals(1, result.get(1).getSources().size());
            assertSource(result.get(1).getSources().get(0), second, false);
        }

        @ParameterizedTest
        @NullSource
        @EnumSource(value = PermissionType.class, names = {"NONE", "OWNER"})
        @DisplayName("ignores unsupported permission types without creating recipients or sources")
        void ignoresUnsupportedPermissionTypes(PermissionType type) {
            User ignoredRecipient = user("Ignored", "ignored@test.com");
            Permission ignored = permission(folder, ignoredRecipient, type);
            Permission ignoredForRecipient = permission(folder, recipient, type);
            Permission valid = permission(folder, recipient, PermissionType.VIEW);
            mockPermissions(List.of(folder.getId()), ignored, ignoredForRecipient, valid);

            List<EffectivePermissionResponse> result = resolver.resolveForFolder(folder);

            assertEquals(1, result.size());
            assertRecipient(result.get(0), recipient, PermissionType.VIEW);
            assertEquals(1, result.get(0).getSources().size());
            assertSource(result.get(0).getSources().get(0), valid, false);
        }
    }

    @Nested
    @DisplayName("Resolve document permissions")
    class ResolveForDocumentTests {

        private Document document() {
            return Document.builder()
                .id(UUID.randomUUID())
                .name("Test Document")
                .folder(folder)
                .owner(owner)
                .build();
        }

        @Test
        @DisplayName("returns an empty list when the containing folder has no shares")
        void returnsEmptyListWhenNoSharesExist() {
            mockPermissions(List.of(folder.getId()));

            List<EffectivePermissionResponse> result = resolver.resolveForDocument(document());

            assertNotNull(result);
            assertTrue(result.isEmpty());
            verifyNoInteractions(folderRepository);
        }

        @ParameterizedTest
        @EnumSource(value = PermissionType.class, names = {"VIEW", "EDIT"})
        @DisplayName("marks shares on the containing folder as inherited for a document")
        void inheritsPermissionFromContainingFolder(PermissionType type) {
            Permission inherited = permission(folder, recipient, type);
            mockPermissions(List.of(folder.getId()), inherited);

            List<EffectivePermissionResponse> result = resolver.resolveForDocument(document());

            assertEquals(1, result.size());
            assertRecipient(result.get(0), recipient, type);
            assertEquals(1, result.get(0).getSources().size());
            assertSource(result.get(0).getSources().get(0), inherited, true);
            verifyNoInteractions(folderRepository);
        }

        @Test
        @DisplayName("combines folder and ancestor shares with every source marked inherited")
        void inheritsStrongestPermissionFromFolderAndAncestors() {
            Folder parent = folder("Parent");
            mockParent(folder, parent);
            Permission folderPermission = permission(folder, recipient, PermissionType.VIEW);
            Permission ancestorPermission = permission(parent, recipient, PermissionType.EDIT);
            mockPermissions(List.of(folder.getId(), parent.getId()),
                folderPermission, ancestorPermission);

            List<EffectivePermissionResponse> result = resolver.resolveForDocument(document());

            assertEquals(1, result.size());
            assertRecipient(result.get(0), recipient, PermissionType.EDIT);
            assertEquals(2, result.get(0).getSources().size());
            assertSource(result.get(0).getSources().get(0), folderPermission, true);
            assertSource(result.get(0).getSources().get(1), ancestorPermission, true);
        }
    }

    @Nested
    @DisplayName("Invalid folder hierarchy")
    class InvalidHierarchyTests {

        @ParameterizedTest
        @ValueSource(strings = {"not-a-uuid", "", " "})
        @DisplayName("rejects malformed parent IDs before querying repositories")
        void rejectsInvalidParentId(String parentId) {
            folder.setParentFolder(parentId);

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> resolver.resolveForFolder(folder));

            assertEquals("Folder hierarchy contains an invalid parent ID", exception.getMessage());
            assertInstanceOf(IllegalArgumentException.class, exception.getCause());
            verifyNoInteractions(folderRepository, permissionRepository);
        }

        @Test
        @DisplayName("rejects an invalid parent ID on an ancestor")
        void rejectsInvalidAncestorParentId() {
            Folder parent = folder("Parent");
            mockParent(folder, parent);
            parent.setParentFolder("invalid-parent");

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> resolver.resolveForFolder(folder));

            assertEquals("Folder hierarchy contains an invalid parent ID", exception.getMessage());
            verifyNoInteractions(permissionRepository);
        }

        @Test
        @DisplayName("reports FOLDER_NOT_FOUND when an ancestor does not exist")
        void rejectsMissingAncestor() {
            Folder parent = folder("Parent");
            mockParent(folder, parent);
            UUID missingId = UUID.randomUUID();
            parent.setParentFolder(missingId.toString());
            when(folderRepository.findById(missingId)).thenReturn(Optional.empty());

            AppException exception = assertThrows(AppException.class,
                () -> resolver.resolveForFolder(folder));

            assertEquals(ErrorCode.FOLDER_NOT_FOUND, exception.getErrorCode());
            verifyNoInteractions(permissionRepository);
        }

        @Test
        @DisplayName("rejects a folder that is its own parent")
        void rejectsSelfReferencingFolder() {
            mockParent(folder, folder);

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> resolver.resolveForFolder(folder));

            assertEquals("Folder hierarchy contains a cycle", exception.getMessage());
            verify(folderRepository).findById(folder.getId());
            verifyNoInteractions(permissionRepository);
        }

        @Test
        @DisplayName("rejects a cycle between ancestors")
        void rejectsAncestorCycle() {
            Folder parent = folder("Parent");
            Folder root = folder("Root");
            mockParent(folder, parent);
            mockParent(parent, root);
            root.setParentFolder(parent.getId().toString());

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> resolver.resolveForFolder(folder));

            assertEquals("Folder hierarchy contains a cycle", exception.getMessage());
            verify(folderRepository, times(2)).findById(parent.getId());
            verify(folderRepository).findById(root.getId());
            verifyNoInteractions(permissionRepository);
        }
    }
}
