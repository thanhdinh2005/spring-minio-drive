package com.spring.backend.service.impl;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spring.backend.entity.Folder;
import com.spring.backend.entity.enums.PermissionType;
import com.spring.backend.exception.AppException;
import com.spring.backend.exception.ErrorCode;
import com.spring.backend.repository.FolderRepository;
import com.spring.backend.repository.PermissionRepository;
import com.spring.backend.service.FolderAccessService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FolderAccessServiceImpl implements FolderAccessService {

  private final FolderRepository folderRepository;
  private final PermissionRepository permissionRepository;

  @Override
  public Folder requireFolder(UUID folderId) {
    return folderRepository.findById(folderId)
      .orElseThrow(() -> new AppException(ErrorCode.FOLDER_NOT_FOUND));
  }

  @Override
  public PermissionType getEffectivePermission(Folder folder, UUID userId) {
    PermissionType effective = PermissionType.NONE;
    Folder current = folder;

    while (current != null) {
      // Là owner của folder (hoặc của tổ tiên) -> OWNER
      if (current.getOwner() != null && userId.equals(current.getOwner().getId())) {
        return PermissionType.OWNER;
      }

      PermissionType direct = permissionRepository
        .findByFolder_IdAndSharedWithUser_Id(current.getId(), userId)
        .map(permission -> permission.getPermissionType())
        .orElse(PermissionType.NONE);

      // Ưu tiên EDIT hơn VIEW (không dùng PermissionType.max vì NONE có ordinal cao hơn EDIT)
      if (direct == PermissionType.EDIT) {
        effective = PermissionType.EDIT;
      } else if (direct == PermissionType.VIEW && effective == PermissionType.NONE) {
        effective = PermissionType.VIEW;
      }

      current = findParent(current);
    }

    return effective;
  }

  @Override
  public void requireView(Folder folder, UUID userId) {
    if (!getEffectivePermission(folder, userId).canView()) {
      throw new AppException(ErrorCode.FOLDER_ACCESS_DENIED);
    }
  }

  @Override
  public void requireEdit(Folder folder, UUID userId) {
    if (!getEffectivePermission(folder, userId).canEdit()) {
      throw new AppException(ErrorCode.FOLDER_ACCESS_DENIED);
    }
  }

  @Override
  public void requireOwner(Folder folder, UUID userId) {
    // Chỉ OWNER mới được xóa (kể cả khi có quyền EDIT được chia sẻ cũng không được xóa).
    // Dùng quyền hiệu lực nên owner của folder hoặc của tổ tiên đều được xem là OWNER.
    if (getEffectivePermission(folder, userId) != PermissionType.OWNER) {
      throw new AppException(ErrorCode.FOLDER_ACCESS_DENIED);
    }
  }

  private Folder findParent(Folder folder) {
    String parentFolderId = folder.getParentFolder();
    if (parentFolderId == null) {
      return null;
    }
    return folderRepository.findById(UUID.fromString(parentFolderId)).orElse(null);
  }
}
