package com.spring.backend.service.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SharingPermissionResolver {

    private final FolderRepository folderRepository;
    private final PermissionRepository permissionRepository;
    //Kiểu dữ liệu trả về: một danh sách, mỗi phần tử chứa thông tin một người, quyền tổng hợp và các nguồn quyền.
    public List<EffectivePermissionResponse> resolveForFolder(
            Folder folder
    ) {
        return resolve(folder, folder.getId());
    }

    public List<EffectivePermissionResponse> resolveForDocument(
            Document document
    ) {
        // File không có quyền chia sẻ trực tiếp.
        return resolve(document.getFolder(), null);
    }

    private List<EffectivePermissionResponse> resolve(
            Folder startFolder,
            UUID selectedFolderId
    ) {
        //// Gọi hàm thu thập ID folder hiện tại và tổ tiên.
        List<UUID> folderIds = collectAncestorIds(startFolder);

        List<Permission> permissions = permissionRepository
                .findByFolder_IdInAndSharedWithUserIsNotNullOrderByCreatedAtDesc(
                        folderIds
                );

        Map<UUID, User> recipients = new LinkedHashMap<>();

        Map<UUID, PermissionType> effectivePermissions =
                new LinkedHashMap<>();

        Map<UUID, List<PermissionSourceResponse>> sourcesByUser =
                new LinkedHashMap<>();

        for (Permission permission : permissions) {
            PermissionType type = permission.getPermissionType();

            if (type != PermissionType.VIEW
                    && type != PermissionType.EDIT) {
                continue;
            }
            // l
            User recipient = permission.getSharedWithUser();
            UUID userId = recipient.getId();
            Folder sourceFolder = permission.getFolder();

            recipients.putIfAbsent(userId, recipient);

            PermissionType current = effectivePermissions
                    .getOrDefault(userId, PermissionType.NONE);

            effectivePermissions.put(
                    userId,
                    PermissionType.max(current, type)
            );

            PermissionSourceResponse source =
                    PermissionSourceResponse.builder()
                            .permissionId(permission.getId())
                            .folderId(sourceFolder.getId())
                            .folderName(sourceFolder.getName())
                            .permission(type)
                            .inherited(
                                    !sourceFolder.getId()
                                            .equals(selectedFolderId)
                            )
                            .grantedById(
                                    permission.getGrantedBy().getId()
                            )
                            .build();

            sourcesByUser
                    .computeIfAbsent(
                            userId,
                            ignored -> new ArrayList<>()
                    )
                    .add(source);
        }

        List<EffectivePermissionResponse> result =
                new ArrayList<>();

        for (Map.Entry<UUID, User> entry : recipients.entrySet()) {
            UUID userId = entry.getKey();
            User recipient = entry.getValue();

            result.add(
                    EffectivePermissionResponse.builder()
                            .userId(userId)
                            .userName(recipient.getDisplayName())
                            .userEmail(recipient.getEmail())
                            .effectivePermission(
                                    effectivePermissions.get(userId)
                            )
                            .sources(
                                    List.copyOf(sourcesByUser.get(userId))
                            )
                            .build()
            );
        }

        return List.copyOf(result);
    }

    private List<UUID> collectAncestorIds(Folder startFolder) {
        List<UUID> folderIds = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();

        Folder current = startFolder;

        while (current != null) {
            UUID folderId = current.getId();

            if (!visited.add(folderId)) {
                throw new IllegalStateException(
                        "Folder hierarchy contains a cycle"
                );
            }

            folderIds.add(folderId);

            String parentId = current.getParentFolder();

            if (parentId == null) {
                break;
            }

            UUID parentUuid;

            try {
                parentUuid = UUID.fromString(parentId);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException(
                        "Folder hierarchy contains an invalid parent ID",
                        exception
                );
            }

            current = folderRepository.findById(parentUuid)
                    .orElseThrow(
                            () -> new AppException(
                                    ErrorCode.FOLDER_NOT_FOUND
                            )
                    );
        }

        return folderIds;
    }
}