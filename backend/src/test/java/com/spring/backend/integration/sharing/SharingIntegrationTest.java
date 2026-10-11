package com.spring.backend.integration.sharing;

import com.spring.backend.dto.sharing.SharePermissionRequest;
import com.spring.backend.dto.sharing.SharePermissionResponse;
import com.spring.backend.entity.Folder;
import com.spring.backend.entity.Permission;
import com.spring.backend.entity.User;
import com.spring.backend.entity.enums.PermissionType;
import com.spring.backend.entity.enums.Plan;
import com.spring.backend.entity.enums.Role;
import com.spring.backend.entity.enums.Status;
import com.spring.backend.exception.AppException;
import com.spring.backend.exception.ErrorCode;
import com.spring.backend.integration.AbstractIntegrationTest;
import com.spring.backend.repository.FolderRepository;
import com.spring.backend.repository.PermissionRepository;
import com.spring.backend.repository.UserRepository;
import com.spring.backend.service.SharingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Chạy tại thư mục backend bằng Windows CMD:
 *
 * mvnw.cmd -Dtest=SharingIntegrationTest test
 *
 * Chạy cả unit test và integration test của chia sẻ:
 *
 * mvnw.cmd -Dtest=SharingServiceImplTest,SharingIntegrationTest test
 *
 * Yêu cầu Docker Desktop đang chạy.
 * AbstractIntegrationTest tự tạo PostgreSQL và RustFS tạm.
 * Không dùng database app_db local.
 *
 * Phạm vi: Service + Repository + database + Flyway.
 * Chưa kiểm tra HTTP Controller hoặc JWT.
 */
@Transactional
class SharingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SharingService sharingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    private User owner;
    private User recipient;
    private Folder folder;

    @BeforeEach
    void setUp() {
        owner = createUser("Owner");
        recipient = createUser("Recipient");

        // Tên duy nhất vì V2 hiện có UNIQUE toàn cục cho folder.name.
        folder = folderRepository.saveAndFlush(
                Folder.builder()
                        .name("Sharing-" + UUID.randomUUID())
                        .owner(owner)
                        .isDeleted(0)
                        .build()
        );
    }

    @Test
    void grantThenUpdate_keepsSamePermissionAndOneRecord() {
        SharePermissionResponse created =
                sharingService.grantOrUpdatePermission(
                        folder.getId(),
                        recipient.getId(),
                        request(PermissionType.VIEW),
                        owner.getId()
                );

        permissionRepository.flush();

        assertNotNull(created.getPermissionId());
        assertEquals(PermissionType.VIEW, created.getPermission());

        SharePermissionResponse updated =
                sharingService.grantOrUpdatePermission(
                        folder.getId(),
                        recipient.getId(),
                        request(PermissionType.EDIT),
                        owner.getId()
                );

        permissionRepository.flush();

        assertEquals(created.getPermissionId(), updated.getPermissionId());
        assertEquals(PermissionType.EDIT, updated.getPermission());

        List<SharePermissionResponse> shares =
                sharingService.getDirectShares(
                        folder.getId(),
                        owner.getId()
                );

        assertEquals(1, shares.size());
        assertEquals(created.getPermissionId(),
                shares.get(0).getPermissionId());
        assertEquals(PermissionType.EDIT,
                shares.get(0).getPermission());

        Permission stored = permissionRepository
                .findByFolder_IdAndSharedWithUser_Id(
                        folder.getId(),
                        recipient.getId()
                )
                .orElseThrow();

        assertEquals(created.getPermissionId(), stored.getId());
        assertEquals(PermissionType.EDIT, stored.getPermissionType());
    }

    @Test
    void getDirectShares_returnsUserShareAndExcludesPublicLink() {
        sharingService.grantOrUpdatePermission(
                folder.getId(),
                recipient.getId(),
                request(PermissionType.VIEW),
                owner.getId()
        );

        permissionRepository.saveAndFlush(
                Permission.builder()
                        .folder(folder)
                        .grantedBy(owner)
                        .permissionType(PermissionType.VIEW)
                        .publicToken(UUID.randomUUID().toString())
                        .build()
        );

        List<SharePermissionResponse> shares =
                sharingService.getDirectShares(
                        folder.getId(),
                        owner.getId()
                );

        assertEquals(1, shares.size());

        SharePermissionResponse response = shares.get(0);

        assertEquals(folder.getId(), response.getFolderId());
        assertEquals(recipient.getId(), response.getSharedWithUserId());
        assertEquals(recipient.getDisplayName(),
                response.getSharedWithUserName());
        assertEquals(recipient.getEmail(),
                response.getSharedWithUserEmail());
        assertEquals(owner.getId(), response.getGrantedById());
        assertEquals(PermissionType.VIEW, response.getPermission());
    }

    @Test
    void database_rejectsDuplicateFolderUserPermission() {
        sharingService.grantOrUpdatePermission(
                folder.getId(),
                recipient.getId(),
                request(PermissionType.VIEW),
                owner.getId()
        );

        permissionRepository.flush();

        Permission duplicate = Permission.builder()
                .folder(folder)
                .sharedWithUser(recipient)
                .grantedBy(owner)
                .permissionType(PermissionType.EDIT)
                .build();

        // saveAndFlush buộc gửi INSERT xuống database ngay.
        assertThrows(
                DataIntegrityViolationException.class,
                () -> permissionRepository.saveAndFlush(duplicate)
        );

        // Không truy vấn tiếp: transaction đã lỗi và sẽ rollback.
    }

    @Test
    void nonOwner_cannotGrantOrReadShares() {
        AppException grantError = assertThrows(
                AppException.class,
                () -> sharingService.grantOrUpdatePermission(
                        folder.getId(),
                        recipient.getId(),
                        request(PermissionType.VIEW),
                        recipient.getId()
                )
        );

        assertEquals(
                ErrorCode.CANNOT_SHARE_WITHOUT_OWNERSHIP,
                grantError.getErrorCode()
        );

        AppException readError = assertThrows(
                AppException.class,
                () -> sharingService.getDirectShares(
                        folder.getId(),
                        recipient.getId()
                )
        );

        assertEquals(
                ErrorCode.CANNOT_SHARE_WITHOUT_OWNERSHIP,
                readError.getErrorCode()
        );
    }

    private User createUser(String displayName) {
        return userRepository.saveAndFlush(
                User.builder()
                        .email(UUID.randomUUID() + "@test.com")
                        .passwordHash("integration-test-placeholder")
                        .displayName(displayName)
                        .role(Role.USER)
                        .status(Status.ACTIVE)
                        .plan(Plan.FREE)
                        .storageUsed(0L)
                        .storageLimit(104857600L)
                        .build()
        );
    }

    private SharePermissionRequest request(PermissionType type) {
        SharePermissionRequest request = new SharePermissionRequest();
        request.setPermission(type);
        return request;
    }
}