package com.spring.backend.service;

import java.util.UUID;

import com.spring.backend.entity.Folder;
import com.spring.backend.entity.enums.PermissionType;

/**
 * Xử lý quyền truy cập folder theo cơ chế kế thừa từ cha.
 * Dùng chung cho các module thao tác trên folder/document.
 */
public interface FolderAccessService {

  // Lấy folder theo id, ném FOLDER_NOT_FOUND (404) nếu không tồn tại
  Folder requireFolder(UUID folderId);

  // Lấy folder theo tên và owner, ném FOLDER_NOT_FOUND (404) nếu không tồn tại
  Folder requireFolderByName(String folderName, UUID ownerId);

  // Suy ra quyền hiệu lực của user trên folder (có kế thừa từ tổ tiên)
  PermissionType getEffectivePermission(Folder folder, UUID userId);

  // Yêu cầu quyền VIEW, ném FOLDER_ACCESS_DENIED (403) nếu không đủ
  void requireView(Folder folder, UUID userId);

  // Yêu cầu quyền EDIT, ném FOLDER_ACCESS_DENIED (403) nếu không đủ
  void requireEdit(Folder folder, UUID userId);

  // Yêu cầu quyền OWNER, ném FOLDER_ACCESS_DENIED (403) nếu không phải owner
  void requireOwner(Folder folder, UUID userId);
}
