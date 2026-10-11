package com.spring.backend.service.impl;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import com.spring.backend.dto.sharing.SharePermissionRequest;
import com.spring.backend.dto.sharing.SharePermissionResponse;
import com.spring.backend.entity.Folder;
import com.spring.backend.entity.Permission;
import com.spring.backend.entity.User;
import com.spring.backend.entity.enums.PermissionType;
import com.spring.backend.exception.AppException;
import com.spring.backend.exception.ErrorCode;
import com.spring.backend.repository.FolderRepository;
import com.spring.backend.repository.PermissionRepository;
import com.spring.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

/*
 * Chạy tại thư mục backend bằng Windows CMD:
 *
 * Chạy toàn bộ test trong file:
 * mvnw.cmd -Dtest=SharingServiceImplTest test
 *
 * Chạy riêng một method:
 * mvnw.cmd -Dtest=SharingServiceImplTest#grantPermission_whenNotOwner_deniesAccess test
 *
 * Chạy toàn bộ test của dự án:
 * mvnw.cmd test
 *
 * Đây là unit test:
 * - Không cần PostgreSQL hoặc backend đang chạy.
 * - Repository được giả lập bằng Mockito.
 * - Không kiểm tra SQL, EntityGraph, Flyway hoặc transaction thật.
 */
@ExtendWith(MockitoExtension.class)
class SharingServiceImplTest {

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SharingServiceImpl sharingService;

    private UUID ownerId;
    private UUID recipientId;
    private UUID folderId;
    private UUID permissionId;

    private User owner;
    private User recipient;
    private Folder folder;

    @BeforeEach
    void setUp() {
        // Mỗi test nhận bộ dữ liệu mới, tránh ảnh hưởng lẫn nhau.
        ownerId = UUID.randomUUID();
        recipientId = UUID.randomUUID();
        folderId = UUID.randomUUID();
        permissionId = UUID.randomUUID();

        owner = User.builder()
                .id(ownerId)
                .displayName("Owner")
                .email("owner@test.com")
                .build();

        recipient = User.builder()
                .id(recipientId)
                .displayName("Bình")
                .email("binh@test.com")
                .build();

        folder = Folder.builder()
                .id(folderId)
                .name("Sharing Test")
                .owner(owner)
                .isDeleted(0)
                .build();
    }

    // ===== Cấp mới và cập nhật =====

    @ParameterizedTest
    @EnumSource(
            value = PermissionType.class,
            names = {"VIEW", "EDIT"}
    )
    void grantPermission_whenNoExistingShare_createsPermission(
            PermissionType type
    ) {
        // Arrange
        mockExistingFolder();

        when(userRepository.findById(recipientId))
                .thenReturn(Optional.of(recipient));

        when(permissionRepository
                .findByFolder_IdAndSharedWithUser_Id(folderId, recipientId))
                .thenReturn(Optional.empty());

        // Giả lập database gán ID cho bản ghi mới.
        when(permissionRepository.saveAndFlush(any(Permission.class)))
                .thenAnswer(invocation -> {
                    Permission permission = invocation.getArgument(0);
                    permission.setId(permissionId);
                    return permission;
                });

        // Act
        SharePermissionResponse response =
                sharingService.grantOrUpdatePermission(
                        folderId,
                        recipientId,
                        request(type),
                        ownerId
                );

        // Assert: kiểm tra DTO trả về.
        assertResponse(response, type);

        // Kiểm tra đối tượng thực sự được đưa vào save().
        ArgumentCaptor<Permission> captor =
                ArgumentCaptor.forClass(Permission.class);

        verify(permissionRepository).saveAndFlush(captor.capture());

        Permission saved = captor.getValue();

        assertSame(folder, saved.getFolder());
        assertSame(recipient, saved.getSharedWithUser());
        assertSame(owner, saved.getGrantedBy());
        assertEquals(type, saved.getPermissionType());
        assertNull(saved.getPublicToken());
    }

    @ParameterizedTest
    @EnumSource(
            value = PermissionType.class,
            names = {"VIEW", "EDIT"}
    )
    void grantPermission_whenShareExists_updatesSamePermission(
            PermissionType newType
    ) {
        // Kiểm tra cả VIEW -> EDIT và EDIT -> VIEW.
        PermissionType oldType = newType == PermissionType.EDIT
                ? PermissionType.VIEW
                : PermissionType.EDIT;

        Permission existing = permission(oldType);

        mockExistingFolder();

        when(userRepository.findById(recipientId))
                .thenReturn(Optional.of(recipient));

        when(permissionRepository
                .findByFolder_IdAndSharedWithUser_Id(folderId, recipientId))
                .thenReturn(Optional.of(existing));

        when(permissionRepository.saveAndFlush(any(Permission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SharePermissionResponse response =
                sharingService.grantOrUpdatePermission(
                        folderId,
                        recipientId,
                        request(newType),
                        ownerId
                );

        assertResponse(response, newType);
        assertEquals(newType, existing.getPermissionType());

        // Phải lưu đúng đối tượng cũ, không tạo bản ghi mới.
        verify(permissionRepository).saveAndFlush(same(existing));
    }

    @Test
    void grantPermission_whenSamePermission_keepsExistingRecord() {
        Permission existing = permission(PermissionType.VIEW);

        mockExistingFolder();

        when(userRepository.findById(recipientId))
                .thenReturn(Optional.of(recipient));

        when(permissionRepository
                .findByFolder_IdAndSharedWithUser_Id(folderId, recipientId))
                .thenReturn(Optional.of(existing));

        when(permissionRepository.saveAndFlush(any(Permission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SharePermissionResponse response =
                sharingService.grantOrUpdatePermission(
                        folderId,
                        recipientId,
                        request(PermissionType.VIEW),
                        ownerId
                );

        assertEquals(permissionId, response.getPermissionId());
        assertEquals(PermissionType.VIEW, response.getPermission());

        verify(permissionRepository).saveAndFlush(same(existing));
    }

    // ===== Các trường hợp từ chối cấp quyền =====

    @Test
    void grantPermission_whenFolderMissing_throwsNotFound() {
        when(folderRepository.findById(folderId))
                .thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> sharingService.grantOrUpdatePermission(
                        folderId,
                        recipientId,
                        request(PermissionType.VIEW),
                        ownerId
                )
        );

        assertEquals(ErrorCode.FOLDER_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(userRepository, permissionRepository);
    }

    @Test
    void grantPermission_whenFolderDeleted_throwsNotFound() {
        folder.setIsDeleted(1);
        mockExistingFolder();

        AppException exception = assertThrows(
                AppException.class,
                () -> sharingService.grantOrUpdatePermission(
                        folderId,
                        recipientId,
                        request(PermissionType.VIEW),
                        ownerId
                )
        );

        assertEquals(ErrorCode.FOLDER_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(userRepository, permissionRepository);
    }

    @Test
    void grantPermission_whenNotOwner_deniesAccess() {
        mockExistingFolder();

        AppException exception = assertThrows(
                AppException.class,
                () -> sharingService.grantOrUpdatePermission(
                        folderId,
                        recipientId,
                        request(PermissionType.VIEW),
                        recipientId
                )
        );

        assertEquals(
                ErrorCode.CANNOT_SHARE_WITHOUT_OWNERSHIP,
                exception.getErrorCode()
        );
        verifyNoInteractions(userRepository, permissionRepository);
    }

    @ParameterizedTest
    @EnumSource(
            value = PermissionType.class,
            names = {"NONE", "OWNER"}
    )
    void grantPermission_whenPermissionInvalid_rejectsRequest(
            PermissionType type
    ) {
        mockExistingFolder();

        AppException exception = assertThrows(
                AppException.class,
                () -> sharingService.grantOrUpdatePermission(
                        folderId,
                        recipientId,
                        request(type),
                        ownerId
                )
        );

        assertEquals(
                ErrorCode.INVALID_SHARE_PERMISSION,
                exception.getErrorCode()
        );
        verifyNoInteractions(userRepository, permissionRepository);
    }

    @Test
    void grantPermission_whenPermissionNull_rejectsRequest() {
        mockExistingFolder();

        AppException exception = assertThrows(
                AppException.class,
                () -> sharingService.grantOrUpdatePermission(
                        folderId,
                        recipientId,
                        request(null),
                        ownerId
                )
        );

        assertEquals(
                ErrorCode.INVALID_SHARE_PERMISSION,
                exception.getErrorCode()
        );
        verifyNoInteractions(userRepository, permissionRepository);
    }

    @Test
    void grantPermission_whenRequestNull_rejectsRequest() {
        mockExistingFolder();

        AppException exception = assertThrows(
                AppException.class,
                () -> sharingService.grantOrUpdatePermission(
                        folderId,
                        recipientId,
                        null,
                        ownerId
                )
        );

        assertEquals(
                ErrorCode.INVALID_SHARE_PERMISSION,
                exception.getErrorCode()
        );
        verifyNoInteractions(userRepository, permissionRepository);
    }

    @Test
    void grantPermission_whenRecipientIsOwner_rejectsRequest() {
        mockExistingFolder();

        AppException exception = assertThrows(
                AppException.class,
                () -> sharingService.grantOrUpdatePermission(
                        folderId,
                        ownerId,
                        request(PermissionType.VIEW),
                        ownerId
                )
        );

        assertEquals(
                ErrorCode.CANNOT_SHARE_WITH_OWNER,
                exception.getErrorCode()
        );
        verifyNoInteractions(userRepository, permissionRepository);
    }

    @Test
    void grantPermission_whenRecipientMissing_throwsNotFound() {
        mockExistingFolder();

        when(userRepository.findById(recipientId))
                .thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> sharingService.grantOrUpdatePermission(
                        folderId,
                        recipientId,
                        request(PermissionType.VIEW),
                        ownerId
                )
        );

        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(permissionRepository);
    }

    // ===== Xem danh sách quyền trực tiếp =====

    @Test
    void getDirectShares_whenOwner_returnsMappedShares() {
        mockExistingFolder();

        when(permissionRepository
                .findByFolder_IdAndSharedWithUserIsNotNullOrderByCreatedAtDesc(
                        folderId
                ))
                .thenReturn(List.of(permission(PermissionType.EDIT)));

        List<SharePermissionResponse> responses =
                sharingService.getDirectShares(folderId, ownerId);

        assertEquals(1, responses.size());
        assertResponse(responses.get(0), PermissionType.EDIT);

        verify(permissionRepository)
                .findByFolder_IdAndSharedWithUserIsNotNullOrderByCreatedAtDesc(
                        folderId
                );
        verify(permissionRepository, never()).save(any(Permission.class));
    }

    @Test
    void getDirectShares_whenNoShares_returnsEmptyList() {
        mockExistingFolder();

        when(permissionRepository
                .findByFolder_IdAndSharedWithUserIsNotNullOrderByCreatedAtDesc(
                        folderId
                ))
                .thenReturn(List.of());

        List<SharePermissionResponse> responses =
                sharingService.getDirectShares(folderId, ownerId);

        assertTrue(responses.isEmpty());
        verify(permissionRepository, never()).save(any(Permission.class));
    }

    @Test
    void getDirectShares_whenNotOwner_deniesAccess() {
        mockExistingFolder();

        AppException exception = assertThrows(
                AppException.class,
                () -> sharingService.getDirectShares(folderId, recipientId)
        );

        assertEquals(
                ErrorCode.CANNOT_SHARE_WITHOUT_OWNERSHIP,
                exception.getErrorCode()
        );
        verifyNoInteractions(permissionRepository);
    }

    @Test
    void getDirectShares_whenFolderMissing_throwsNotFound() {
        when(folderRepository.findById(folderId))
                .thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> sharingService.getDirectShares(folderId, ownerId)
        );

        assertEquals(ErrorCode.FOLDER_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(permissionRepository);
    }

    @Test
    void getDirectShares_whenFolderDeleted_throwsNotFound() {
        folder.setIsDeleted(1);
        mockExistingFolder();

        AppException exception = assertThrows(
                AppException.class,
                () -> sharingService.getDirectShares(folderId, ownerId)
        );

        assertEquals(ErrorCode.FOLDER_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(permissionRepository);
    }

    // ===== Hàm hỗ trợ chuẩn bị dữ liệu và kiểm tra =====

    private void mockExistingFolder() {
        when(folderRepository.findById(folderId))
                .thenReturn(Optional.of(folder));
    }

    private SharePermissionRequest request(PermissionType type) {
        SharePermissionRequest request = new SharePermissionRequest();
        request.setPermission(type);
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

    private void assertResponse(
            SharePermissionResponse response,
            PermissionType type
    ) {
        assertEquals(permissionId, response.getPermissionId());
        assertEquals(folderId, response.getFolderId());
        assertEquals(recipientId, response.getSharedWithUserId());
        assertEquals("Bình", response.getSharedWithUserName());
        assertEquals("binh@test.com", response.getSharedWithUserEmail());
        assertEquals(type, response.getPermission());
        assertEquals(ownerId, response.getGrantedById());
    }
    @Test
void grantPermission_whenDuplicateConstraintFails_returnsConflict() {
    mockExistingFolder();

    when(userRepository.findById(recipientId))
            .thenReturn(Optional.of(recipient));

    when(permissionRepository
            .findByFolder_IdAndSharedWithUser_Id(folderId, recipientId))
            .thenReturn(Optional.empty());

    ConstraintViolationException databaseError =
            new ConstraintViolationException(
                    "Duplicate folder-user permission",
                    new SQLException("Duplicate key", "23505"),
                    "uq_permissions_folder_user"
            );

    when(permissionRepository.saveAndFlush(any(Permission.class)))
            .thenThrow(new DataIntegrityViolationException(
                    "Duplicate permission",
                    databaseError
            ));

    AppException exception = assertThrows(
            AppException.class,
            () -> sharingService.grantOrUpdatePermission(
                    folderId,
                    recipientId,
                    request(PermissionType.VIEW),
                    ownerId
            )
    );

    assertEquals(
            ErrorCode.SHARE_PERMISSION_CONFLICT,
            exception.getErrorCode()
    );
}

@Test
void grantPermission_whenOtherDatabaseError_doesNotConvertToConflict() {
    mockExistingFolder();

    when(userRepository.findById(recipientId))
            .thenReturn(Optional.of(recipient));

    when(permissionRepository
            .findByFolder_IdAndSharedWithUser_Id(folderId, recipientId))
            .thenReturn(Optional.empty());

    DataIntegrityViolationException databaseError =
            new DataIntegrityViolationException("Other database error");

    when(permissionRepository.saveAndFlush(any(Permission.class)))
            .thenThrow(databaseError);

    DataIntegrityViolationException exception = assertThrows(
            DataIntegrityViolationException.class,
            () -> sharingService.grantOrUpdatePermission(
                    folderId,
                    recipientId,
                    request(PermissionType.VIEW),
                    ownerId
            )
    );

    assertSame(databaseError, exception);
}
}