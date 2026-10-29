package com.spring.backend.service.impl;
import com.spring.backend.dto.sharing.DirectPermissionResponse;
import com.spring.backend.dto.sharing.SharePermissionRequest;
import com.spring.backend.dto.sharing.SharePermissionResponse;
import com.spring.backend.dto.sharing.SharedWithMeResponse;
import com.spring.backend.entity.Folder;
import com.spring.backend.entity.Permission;
import com.spring.backend.entity.User;
import com.spring.backend.entity.enums.PermissionType;
import com.spring.backend.exception.AppException;
import com.spring.backend.exception.ErrorCode;
import com.spring.backend.dto.sharing.UpdatePermissionRequest;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.hibernate.exception.ConstraintViolationException;
import com.spring.backend.repository.FolderRepository;
import com.spring.backend.repository.PermissionRepository;
import com.spring.backend.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/*
 * SHARING SERVICE UNIT TEST
 *
 * Kiểm tra POST cấp quyền, GET quyền trực tiếp, PATCH đổi quyền và DELETE thu hồi quyền.
 *
 * Sử dụng Mockito để giả lập Repository.
 * Không cần Docker Desktop hoặc database thật.
 *
 * Chạy riêng:
 * mvnw.cmd -Dtest=SharingServiceImplTest test
 *
 * Chạy cả unit + integration:
 * mvnw.cmd -Dtest=SharingServiceImplTest,SharingIntegrationTest test
 */
@ExtendWith(MockitoExtension.class)
class SharingServiceImplTest {

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @InjectMocks
    private SharingServiceImpl sharingService;

    private UUID folderId;
    private UUID ownerId;
    private UUID recipientId;
    private UUID permissionId;

    private User owner;
    private User recipient;
    private Folder folder;

    @BeforeEach
    void setUp() {

        folderId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        recipientId = UUID.randomUUID();
        permissionId = UUID.randomUUID();

        owner = User.builder()
            .id(ownerId)
            .email("owner@test.com")
            .displayName("Owner")
            .build();

        recipient = User.builder()
            .id(recipientId)
            .email("recipient@test.com")
            .displayName("Recipient")
            .build();

        folder = Folder.builder()
            .id(folderId)
            .name("Test Folder")
            .owner(owner)
            .build();
    }

    // ======================================
    // HELPER METHODS
    // ======================================

    private SharePermissionRequest request(
            UUID userId,
            PermissionType type) {

        SharePermissionRequest request =
            new SharePermissionRequest();

        request.setSharedWithUserId(userId);
        request.setPermissionType(type);

        return request;
    }

    private Permission permission(PermissionType type) {

        return Permission.builder()
            .id(permissionId)
            .folder(folder)
            .sharedWithUser(recipient)
            .grantedBy(owner)
            .permissionType(type)
            .build();
    }

    private void mockOwnedFolder() {

        when(folderRepository.findById(folderId))
            .thenReturn(Optional.of(folder));
    }

    private void mockValidRecipient() {

        when(userRepository.findById(recipientId))
            .thenReturn(Optional.of(recipient));
    }

    private void mockNoExistingPermission() {

        when(permissionRepository
            .findByFolder_IdAndSharedWithUser_Id(
                folderId, recipientId
            ))
            .thenReturn(Optional.empty());
    }

    private void mockSavePermission() {

        when(permissionRepository.saveAndFlush(
                any(Permission.class)
            ))
            .thenAnswer(invocation -> {

                Permission saved = invocation.getArgument(0);
                assertSame(folder, saved.getFolder());
                assertSame(recipient, saved.getSharedWithUser());
                assertSame(owner, saved.getGrantedBy());
                assertNull(saved.getPublicToken());
                saved.setId(permissionId);

                return saved;
            });
    }

    // ======================================
    // CHỨC NĂNG 1: CẤP QUYỀN
    // ======================================

    @Nested
    @DisplayName("Grant Permission Unit Tests")
    class GrantPermissionTests {

        @Test
        @DisplayName("should grant VIEW successfully")
        void shouldGrantViewSuccessfully() {

            mockOwnedFolder();
            mockValidRecipient();
            mockNoExistingPermission();
            mockSavePermission();

            SharePermissionResponse result =
                sharingService.grantPermission(
                    folderId,
                    request(recipientId, PermissionType.VIEW),
                    ownerId
                );

            assertNotNull(result);
            assertEquals(permissionId, result.getId());
            assertEquals(folderId, result.getFolderId());
            assertEquals(
                recipientId,
                result.getSharedWithUserId()
            );
            assertEquals(
                PermissionType.VIEW,
                result.getPermissionType()
            );

            verify(permissionRepository, times(1))
                .saveAndFlush(any(Permission.class));
        }

        @Test
        @DisplayName("should grant EDIT successfully")
        void shouldGrantEditSuccessfully() {

            mockOwnedFolder();
            mockValidRecipient();
            mockNoExistingPermission();
            mockSavePermission();

            SharePermissionResponse result =
                sharingService.grantPermission(
                    folderId,
                    request(recipientId, PermissionType.EDIT),
                    ownerId
                );

            assertEquals(
                PermissionType.EDIT,
                result.getPermissionType()
            );

            verify(permissionRepository)
                .saveAndFlush(any(Permission.class));
        }

        @Test
        @DisplayName("should reject missing recipient ID")
        void shouldRejectMissingRecipient() {

            mockOwnedFolder();

            SharePermissionRequest request =
                request(null, PermissionType.VIEW);

            assertError(
                ErrorCode.INVALID_SHARE_TARGET,
                () -> sharingService.grantPermission(
                    folderId, request, ownerId
                )
            );

            verify(permissionRepository, never())
                .saveAndFlush(any(Permission.class));
        }

        @Test
        @DisplayName("should reject null request")
        void shouldRejectNullRequest() {

            mockOwnedFolder();

            assertError(
                ErrorCode.INVALID_SHARE_TARGET,
                () -> sharingService.grantPermission(
                    folderId, null, ownerId
                )
            );

            verify(permissionRepository, never())
                .saveAndFlush(any(Permission.class));
        }

        @Test
        @DisplayName("should reject sharing with owner")
        void shouldRejectSharingWithOwner() {

            mockOwnedFolder();

            assertError(
                ErrorCode.INVALID_SHARE_TARGET,
                () -> sharingService.grantPermission(
                    folderId,
                    request(ownerId, PermissionType.VIEW),
                    ownerId
                )
            );

            verify(permissionRepository, never())
                .saveAndFlush(any(Permission.class));
        }

        @Test
        @DisplayName("should reject null permission type")
        void shouldRejectNullPermission() {

            mockOwnedFolder();

            assertError(
                ErrorCode.INVALID_SHARE_PERMISSION,
                () -> sharingService.grantPermission(
                    folderId,
                    request(recipientId, null),
                    ownerId
                )
            );

            verify(permissionRepository, never())
                .saveAndFlush(any(Permission.class));
        }

        @Test
        @DisplayName("should reject non-owner")
        void shouldRejectNonOwner() {

            mockOwnedFolder();

            UUID anotherUserId = UUID.randomUUID();

            assertError(
                ErrorCode.CANNOT_SHARE_WITHOUT_OWNERSHIP,
                () -> sharingService.grantPermission(
                    folderId,
                    request(recipientId, PermissionType.VIEW),
                    anotherUserId
                )
            );

            verify(userRepository, never())
                .findById(any(UUID.class));

            verify(permissionRepository, never())
                .saveAndFlush(any(Permission.class));
        }

        @Test
        @DisplayName("should reject missing folder")
        void shouldRejectMissingFolder() {

            when(folderRepository.findById(folderId))
                .thenReturn(Optional.empty());

            assertError(
                ErrorCode.FOLDER_NOT_FOUND,
                () -> sharingService.grantPermission(
                    folderId,
                    request(recipientId, PermissionType.VIEW),
                    ownerId
                )
            );

            verify(permissionRepository, never())
                .saveAndFlush(any(Permission.class));
        }

        @Test
        @DisplayName("should reject missing recipient user")
        void shouldRejectMissingUser() {

            mockOwnedFolder();

            when(userRepository.findById(recipientId))
                .thenReturn(Optional.empty());

            assertError(
                ErrorCode.USER_NOT_FOUND,
                () -> sharingService.grantPermission(
                    folderId,
                    request(recipientId, PermissionType.VIEW),
                    ownerId
                )
            );

            verify(permissionRepository, never())
                .saveAndFlush(any(Permission.class));
        }

        @Test
        @DisplayName("should reject duplicate folder-user permission")
        void shouldRejectDuplicatePermission() {

            mockOwnedFolder();
            mockValidRecipient();

            when(permissionRepository
                .findByFolder_IdAndSharedWithUser_Id(
                    folderId, recipientId
                ))
                .thenReturn(Optional.of(
                    permission(PermissionType.VIEW)
                ));

            assertError(
                ErrorCode.SHARE_PERMISSION_CONFLICT,
                () -> sharingService.grantPermission(
                    folderId,
                    request(recipientId, PermissionType.EDIT),
                    ownerId
                )
            );

            verify(permissionRepository, never())
                .saveAndFlush(any(Permission.class));
        }

        @Test
        @DisplayName("should propagate unrelated database violations")
        void shouldPropagateUnrelatedDatabaseViolation() {

            mockOwnedFolder();
            mockValidRecipient();
            mockNoExistingPermission();

            when(permissionRepository.saveAndFlush(
                    any(Permission.class)
                ))
                .thenThrow(
                    new DataIntegrityViolationException(
                        "Other database constraint failed"
                    )
                );

            assertThrows(
                DataIntegrityViolationException.class,
                () -> sharingService.grantPermission(
                    folderId,
                    request(recipientId, PermissionType.VIEW),
                    ownerId
                )
            );
        }
    }

    // ======================================
    // CHỨC NĂNG 2: XEM QUYỀN TRỰC TIẾP
    // ======================================

    @Nested
    @DisplayName("Get Direct Shares Unit Tests")
    class GetDirectSharesTests {

        @Test
        @DisplayName("should return direct shares")
        void shouldReturnDirectShares() {

            mockOwnedFolder();

            when(permissionRepository
                .findByFolder_IdAndSharedWithUserIsNotNullOrderByCreatedAtDesc(
                    folderId
                ))
                .thenReturn(List.of(
                    permission(PermissionType.VIEW)
                ));

            List<DirectPermissionResponse> result =
                sharingService.getDirectShares(
                    folderId, ownerId
                );

            assertEquals(1, result.size());
            assertEquals(permissionId, result.get(0).getId());
            assertNotNull(result.get(0).getSharedWithUser());
            assertEquals(recipient.getEmail(),
                result.get(0).getSharedWithUser().getEmail());
            assertEquals(
                recipientId,
                result.get(0).getSharedWithUser().getId()
            );
            assertEquals(
                PermissionType.VIEW,
                result.get(0).getPermissionType()
            );
        }

        @Test
        @DisplayName("should return empty list when no shares exist")
        void shouldReturnEmptyList() {

            mockOwnedFolder();

            when(permissionRepository
                .findByFolder_IdAndSharedWithUserIsNotNullOrderByCreatedAtDesc(
                    folderId
                ))
                .thenReturn(List.of());

            List<DirectPermissionResponse> result =
                sharingService.getDirectShares(
                    folderId, ownerId
                );

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should reject non-owner reading shares")
        void shouldRejectNonOwnerReadingShares() {

            mockOwnedFolder();

            UUID anotherUserId = UUID.randomUUID();

            assertError(
                ErrorCode.CANNOT_SHARE_WITHOUT_OWNERSHIP,
                () -> sharingService.getDirectShares(
                    folderId, anotherUserId
                )
            );

            verify(permissionRepository, never())
                .findByFolder_IdAndSharedWithUserIsNotNullOrderByCreatedAtDesc(
                    any(UUID.class)
                );
        }

        @Test
        @DisplayName("should reject reading shares of missing folder")
        void shouldRejectMissingFolderReadingShares() {

            when(folderRepository.findById(folderId))
                .thenReturn(Optional.empty());

            assertError(
                ErrorCode.FOLDER_NOT_FOUND,
                () -> sharingService.getDirectShares(
                    folderId, ownerId
                )
            );

            verify(permissionRepository, never())
                .findByFolder_IdAndSharedWithUserIsNotNullOrderByCreatedAtDesc(
                    any(UUID.class)
                );
        }
    }

    @Nested
    @DisplayName("Get Shared With Me Unit Tests")
    class GetSharedWithMeTests {

        @Test
        void returnsEmptyListWhenNoSharesExist() {
            mockShares(List.of());

            List<SharedWithMeResponse> result = sharingService.getSharedWithMe(recipientId);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @ParameterizedTest
        @EnumSource(value = PermissionType.class, names = {"VIEW", "EDIT"})
        void mapsPermissionToSharedWithMeResponse(PermissionType type) {
            mockShares(List.of(permission(type)));

            List<SharedWithMeResponse> result = sharingService.getSharedWithMe(recipientId);

            assertEquals(1, result.size());
            assertShare(result.get(0), folder, type, owner);
        }

        @Test
        void returnsMultipleSharesInRepositoryOrder() {
            User otherGrantor = User.builder()
                .id(UUID.randomUUID()).email("other-owner@test.com").build();
            Folder otherFolder = Folder.builder()
                .id(UUID.randomUUID()).name("Other Folder").owner(otherGrantor).build();
            Permission first = Permission.builder()
                .id(UUID.randomUUID()).folder(otherFolder).sharedWithUser(recipient)
                .grantedBy(otherGrantor).permissionType(PermissionType.EDIT).build();
            mockShares(List.of(first, permission(PermissionType.VIEW)));

            List<SharedWithMeResponse> result = sharingService.getSharedWithMe(recipientId);

            assertEquals(2, result.size());
            assertShare(result.get(0), otherFolder, PermissionType.EDIT, otherGrantor);
            assertShare(result.get(1), folder, PermissionType.VIEW, owner);
        }

        @Test
        void queriesSharesForTheCurrentActorWithSupportedTypes() {
            mockShares(List.of());

            sharingService.getSharedWithMe(recipientId);

            verify(permissionRepository)
                .findBySharedWithUser_IdAndPublicTokenIsNullAndPermissionTypeInOrderByCreatedAtDesc(
                    recipientId, List.of(PermissionType.VIEW, PermissionType.EDIT));
            verifyNoMoreInteractions(permissionRepository);
            verifyNoInteractions(folderRepository, userRepository);
        }

        private void mockShares(List<Permission> shares) {
            when(permissionRepository
                .findBySharedWithUser_IdAndPublicTokenIsNullAndPermissionTypeInOrderByCreatedAtDesc(
                    recipientId, List.of(PermissionType.VIEW, PermissionType.EDIT)))
                .thenReturn(shares);
        }

        private void assertShare(SharedWithMeResponse actual, Folder expectedFolder,
                                 PermissionType expectedType, User grantor) {
            assertAll(
                () -> assertEquals(expectedFolder.getId(), actual.getFolderId()),
                () -> assertEquals(expectedFolder.getName(), actual.getName()),
                () -> assertEquals(expectedType, actual.getPermissionType()),
                () -> assertEquals(grantor.getEmail(), actual.getSharedBy())
            );
    @DisplayName("Revoke Permission Unit Tests")
    class RevokePermissionTests {

        @ParameterizedTest
        @EnumSource(value = PermissionType.class, names = {"VIEW", "EDIT"})
        void ownerDeletesTheExactPermission(PermissionType type) {
            Permission existing = permission(type);
            when(permissionRepository.findById(permissionId)).thenReturn(Optional.of(existing));
            mockOwnedFolder();

            sharingService.revokePermission(permissionId, ownerId);

            verify(permissionRepository).findById(permissionId);
            verify(permissionRepository).delete(same(existing));
            verifyNoMoreInteractions(permissionRepository);
        }

        @Test
        void rejectsMissingPermissionWithoutDeleting() {
            when(permissionRepository.findById(permissionId)).thenReturn(Optional.empty());

            assertError(ErrorCode.PERMISSION_NOT_FOUND,
                () -> sharingService.revokePermission(permissionId, ownerId));

            verify(permissionRepository).findById(permissionId);
            verifyNoMoreInteractions(permissionRepository);
            verifyNoInteractions(folderRepository);
        }

        @Test
        void rejectsNonOwnerWithoutDeleting() {
            Permission existing = permission(PermissionType.EDIT);
            when(permissionRepository.findById(permissionId)).thenReturn(Optional.of(existing));
            mockOwnedFolder();

            assertError(ErrorCode.CANNOT_SHARE_WITHOUT_OWNERSHIP,
                () -> sharingService.revokePermission(permissionId, recipientId));

            verify(permissionRepository, never()).delete(any(Permission.class));
        }

        @Test
        void rejectsPermissionWithoutRecipientWithoutDeleting() {
            Permission existing = permission(PermissionType.VIEW);
            existing.setSharedWithUser(null);
            // No token: this exercises the missing-recipient guard independently.
            when(permissionRepository.findById(permissionId)).thenReturn(Optional.of(existing));
            mockOwnedFolder();

            assertError(ErrorCode.INVALID_SHARE_TARGET,
                () -> sharingService.revokePermission(permissionId, ownerId));

            verify(permissionRepository, never()).delete(any(Permission.class));
        }

        @Test
        void rejectsPermissionWithPublicTokenWithoutDeleting() {
            Permission existing = permission(PermissionType.VIEW);
            // Keep the recipient so the public-token guard must reject this record.
            existing.setPublicToken("public-token");
            when(permissionRepository.findById(permissionId)).thenReturn(Optional.of(existing));
            mockOwnedFolder();

            assertError(ErrorCode.INVALID_SHARE_TARGET,
                () -> sharingService.revokePermission(permissionId, ownerId));

            verify(permissionRepository, never()).delete(any(Permission.class));
        }
    }

    private void assertError(ErrorCode expected, Executable action) {
        AppException exception = assertThrows(AppException.class, action);
        assertEquals(expected, exception.getErrorCode());
    }

    private UpdatePermissionRequest updateRequest(PermissionType type) {
        UpdatePermissionRequest request = new UpdatePermissionRequest();
        request.setPermissionType(type);
        return request;
    }

    @Nested
    @DisplayName("Additional grant validation and database conflict")
    class AdditionalGrantTests {

        @ParameterizedTest
        @EnumSource(value = PermissionType.class, names = {"NONE", "OWNER"})
        void rejectsUnsupportedGrantTypes(PermissionType type) {
            mockOwnedFolder();
            assertError(ErrorCode.INVALID_SHARE_PERMISSION,
                () -> sharingService.grantPermission(
                    folderId, request(recipientId, type), ownerId));
            verifyNoInteractions(userRepository, permissionRepository);
        }

        @Test
        void mapsOnlyTheFolderUserUniqueConstraintToConflict() {
            mockOwnedFolder();
            mockValidRecipient();
            mockNoExistingPermission();
            ConstraintViolationException cause = mock(ConstraintViolationException.class);
            when(cause.getConstraintName()).thenReturn("uq_permissions_folder_user");
            when(permissionRepository.saveAndFlush(any(Permission.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate", cause));

            assertError(ErrorCode.SHARE_PERMISSION_CONFLICT,
                () -> sharingService.grantPermission(
                    folderId, request(recipientId, PermissionType.VIEW), ownerId));
        }
    }

    @Nested
    @DisplayName("Update Permission Unit Tests")
    class UpdatePermissionTests {

        @ParameterizedTest
        @EnumSource(value = PermissionType.class, names = {"VIEW", "EDIT"})
        void ownerCanChangePermissionInBothDirections(PermissionType target) {
            PermissionType previous = target == PermissionType.VIEW
                ? PermissionType.EDIT : PermissionType.VIEW;
            Permission existing = permission(previous);
            when(permissionRepository.findById(permissionId))
                .thenReturn(Optional.of(existing));
            mockOwnedFolder();
            when(permissionRepository.saveAndFlush(existing)).thenReturn(existing);

            SharePermissionResponse result = sharingService.updatePermission(
                permissionId, updateRequest(target), ownerId);

            assertEquals(permissionId, result.getId());
            assertEquals(folderId, result.getFolderId());
            assertEquals(recipientId, result.getSharedWithUserId());
            assertEquals(target, result.getPermissionType());
            assertEquals(target, existing.getPermissionType());
            assertSame(folder, existing.getFolder());
            assertSame(recipient, existing.getSharedWithUser());
            assertSame(owner, existing.getGrantedBy());
            verify(permissionRepository).saveAndFlush(existing);
            verifyNoInteractions(userRepository);
        }

        @Test
        void acceptsTheCurrentPermissionWithoutCreatingAnotherRecord() {
            Permission existing = permission(PermissionType.VIEW);
            when(permissionRepository.findById(permissionId))
                .thenReturn(Optional.of(existing));
            mockOwnedFolder();
            when(permissionRepository.saveAndFlush(existing)).thenReturn(existing);

            SharePermissionResponse result = sharingService.updatePermission(
                permissionId, updateRequest(PermissionType.VIEW), ownerId);

            assertEquals(permissionId, result.getId());
            assertEquals(PermissionType.VIEW, result.getPermissionType());
            verify(permissionRepository).saveAndFlush(existing);
        }

        @Test
        void rejectsMissingPermission() {
            when(permissionRepository.findById(permissionId)).thenReturn(Optional.empty());
            assertError(ErrorCode.PERMISSION_NOT_FOUND,
                () -> sharingService.updatePermission(
                    permissionId, updateRequest(PermissionType.EDIT), ownerId));
            verify(permissionRepository, never()).saveAndFlush(any(Permission.class));
            verifyNoInteractions(folderRepository);
        }

        @Test
        void rejectsNonOwnerAndLeavesPermissionUnchanged() {
            Permission existing = permission(PermissionType.VIEW);
            when(permissionRepository.findById(permissionId))
                .thenReturn(Optional.of(existing));
            mockOwnedFolder();

            assertError(ErrorCode.CANNOT_SHARE_WITHOUT_OWNERSHIP,
                () -> sharingService.updatePermission(
                    permissionId, updateRequest(PermissionType.EDIT), recipientId));
            assertEquals(PermissionType.VIEW, existing.getPermissionType());
            verify(permissionRepository, never()).saveAndFlush(any(Permission.class));
        }

        @ParameterizedTest
        @NullSource
        @EnumSource(value = PermissionType.class, names = {"NONE", "OWNER"})
        void rejectsUnsupportedUpdateTypes(PermissionType type) {
            Permission existing = permission(PermissionType.VIEW);
            when(permissionRepository.findById(permissionId))
                .thenReturn(Optional.of(existing));
            mockOwnedFolder();

            assertError(ErrorCode.INVALID_SHARE_PERMISSION,
                () -> sharingService.updatePermission(
                    permissionId, updateRequest(type), ownerId));
            assertEquals(PermissionType.VIEW, existing.getPermissionType());
            verify(permissionRepository, never()).saveAndFlush(any(Permission.class));
        }

        @Test
        void rejectsNullUpdateRequest() {
            when(permissionRepository.findById(permissionId))
                .thenReturn(Optional.of(permission(PermissionType.VIEW)));
            mockOwnedFolder();
            assertError(ErrorCode.INVALID_SHARE_PERMISSION,
                () -> sharingService.updatePermission(permissionId, null, ownerId));
            verify(permissionRepository, never()).saveAndFlush(any(Permission.class));
        }

        @Test
        void rejectsPublicLinkAndLeavesItViewOnly() {
            Permission link = permission(PermissionType.VIEW);
            link.setSharedWithUser(null);
            link.setPublicToken("public-token");
            when(permissionRepository.findById(permissionId)).thenReturn(Optional.of(link));
            mockOwnedFolder();

            assertError(ErrorCode.INVALID_SHARE_TARGET,
                () -> sharingService.updatePermission(
                    permissionId, updateRequest(PermissionType.EDIT), ownerId));
            assertEquals(PermissionType.VIEW, link.getPermissionType());
            verify(permissionRepository, never()).saveAndFlush(any(Permission.class));
        }
    }
}
