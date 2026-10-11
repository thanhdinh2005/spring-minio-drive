package com.spring.backend.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
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
import com.spring.backend.service.SharingService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SharingServiceImpl implements SharingService {

    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;

    @Override
    @Transactional
    public SharePermissionResponse grantOrUpdatePermission(UUID folderId, UUID recipientId,
            SharePermissionRequest request, UUID actorId) {
        // TODO Auto-generated method stub
        Folder folder = requireOwnedFolder(folderId, actorId);
        if(request == null || request.getPermission() != PermissionType.VIEW
                && request.getPermission() != PermissionType.EDIT) {
            throw new AppException(ErrorCode.INVALID_SHARE_PERMISSION);
        }
        if(folder.getOwner().getId().equals(recipientId)) {
            throw new AppException(ErrorCode.CANNOT_SHARE_WITH_OWNER);
        }

        User recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Permission permission = permissionRepository.findByFolder_IdAndSharedWithUser_Id(folderId, recipientId)
                .orElseGet(() -> Permission.builder()//Đoạn code này đảm bảo rằng luôn có một đối tượng Permission được trả về(chua luu vao DB)
                        .folder(folder)
                        .sharedWithUser(recipient)
                        .grantedBy(folder.getOwner())
                        .build());
        permission.setPermissionType(request.getPermission());
        try {
    Permission saved = permissionRepository.saveAndFlush(permission);
    return toResponse(saved);
} catch (DataIntegrityViolationException exception) {
    if (isFolderUserConflict(exception)) {
        throw new AppException(ErrorCode.SHARE_PERMISSION_CONFLICT);
    }

    throw exception;
}

        // Additional logic for granting or updating permission would go here

    }

    @Override
    @Transactional
    public List<SharePermissionResponse> getDirectShares(UUID folderId, UUID actorId) {
        // TODO Auto-generated method stub
        requireOwnedFolder(folderId, actorId);
        return permissionRepository
                .findByFolder_IdAndSharedWithUserIsNotNullOrderByCreatedAtDesc(folderId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private Folder requireOwnedFolder(UUID folderId, UUID actorId) {
    Folder folder = folderRepository.findById(folderId)
            .orElseThrow(() ->
                    new AppException(ErrorCode.FOLDER_NOT_FOUND));

    // Folder đã xóa: trả lỗi không tìm thấy.
    if (folder.getIsDeleted() != 0) {
        throw new AppException(ErrorCode.FOLDER_NOT_FOUND);
    }

    // Folder còn tồn tại nhưng người gọi không phải owner:
    // trả lỗi không có quyền quản lý chia sẻ.
    if (!folder.getOwner().getId().equals(actorId)) {
        throw new AppException(
                ErrorCode.CANNOT_SHARE_WITHOUT_OWNERSHIP
        );
    }

    return folder;
}
    private SharePermissionResponse toResponse(Permission permission) {
        User recipient = permission.getSharedWithUser();
        return SharePermissionResponse.builder()
                .permissionId(permission.getId())
                .folderId(permission.getFolder().getId())
                .sharedWithUserId(recipient.getId())
                .sharedWithUserName(recipient.getDisplayName())
                .sharedWithUserEmail(recipient.getEmail())
                .permission(permission.getPermissionType())
                .grantedById(permission.getGrantedBy().getId())
                .build();
    }
    private boolean isFolderUserConflict(Throwable exception) {
    Throwable current = exception;

    while (current != null) {
        if (current instanceof ConstraintViolationException violation) {
            return "uq_permissions_folder_user"
                    .equals(violation.getConstraintName());
        }

        current = current.getCause();
    }

    return false;
}
}
