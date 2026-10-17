package com.spring.backend.service.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spring.backend.common.utils.StorageNameUtils;
import com.spring.backend.dto.folder.CreateFolderRequest;
import com.spring.backend.dto.folder.FolderResponse;
import com.spring.backend.entity.Folder;
import com.spring.backend.entity.User;
import com.spring.backend.entity.enums.PermissionType;
import com.spring.backend.exception.AppException;
import com.spring.backend.exception.ErrorCode;
import com.spring.backend.repository.FolderRepository;
import com.spring.backend.repository.PermissionRepository;
import com.spring.backend.repository.UserRepository;
import com.spring.backend.service.FolderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class FolderServiceImpl implements FolderService {

  private final FolderRepository folderRepository;
  private final PermissionRepository permissionRepository;
  private final UserRepository userRepository;

  @Override
  @Transactional
  public FolderResponse create(CreateFolderRequest request, UUID ownerId) {
    // 1. Validate tên folder
    if (!StorageNameUtils.isValidStorageName(request.getName())) {
      throw new AppException(ErrorCode.INVALID_FOLDER_NAME,
        Map.of("name", String.valueOf(request.getName())));
    }

    // 2. Nếu có parent: kiểm tra tồn tại + quyền EDIT trên parent
    Folder parent = null;
    if (request.getParentFolderId() != null) {
      parent = folderRepository.findById(request.getParentFolderId())
        .orElseThrow(() -> new AppException(ErrorCode.FOLDER_NOT_FOUND));

      if (!resolvePermission(parent, ownerId).canEdit()) {
        throw new AppException(ErrorCode.FOLDER_ACCESS_DENIED);
      }
    }

    User owner = userRepository.findById(ownerId)
      .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

    // 3. Sinh tên duy nhất trong cùng cấp (abc -> abc (1))
    String parentFolderId = parent == null ? null : parent.getId().toString();
    String uniqueName = StorageNameUtils.generateUniqueName(
      findSiblingNames(parent, ownerId), request.getName());

    // 4. Lưu folder mới
    Folder folder = Folder.builder()
      .name(uniqueName)
      .owner(owner)
      .parentFolder(parentFolderId)
      .build();

    Folder saved = folderRepository.save(folder);
    log.debug("Created folder {} (owner={}, parent={})", saved.getId(), ownerId, parentFolderId);

    return toResponse(saved);
  }

  /**
   * Danh sách tên folder cùng cấp dùng để sinh tên duy nhất.
   * - Folder gốc: chỉ xét folder gốc của chính user.
   * - Folder con: xét tất cả folder con của parent (kể cả folder do user khác tạo khi được share).
   */
  private List<String> findSiblingNames(Folder parent, UUID ownerId) {
    List<Folder> siblings = parent == null
      ? folderRepository.findByOwner_IdAndParentFolderIsNull(ownerId)
      : folderRepository.findByParentFolder(parent.getId().toString());

    return siblings.stream().map(Folder::getName).toList();
  }

  /**
   * Suy ra quyền hiệu lực của user trên folder theo cơ chế kế thừa từ cha:
   * - user là owner của folder (hoặc của tổ tiên) -> OWNER
   * - ngược lại lấy Permission gần nhất theo chuỗi parent_folder
   */
  private PermissionType resolvePermission(Folder folder, UUID userId) {
    PermissionType effective = PermissionType.NONE;
    Folder current = folder;

    while (current != null) {
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

  private Folder findParent(Folder folder) {
    String parentFolderId = folder.getParentFolder();
    if (parentFolderId == null) {
      return null;
    }
    return folderRepository.findById(UUID.fromString(parentFolderId)).orElse(null);
  }

  private FolderResponse toResponse(Folder folder) {
    return FolderResponse.builder()
      .id(folder.getId())
      .name(folder.getName())
      .parentFolderId(folder.getParentFolder() == null ? null : UUID.fromString(folder.getParentFolder()))
      .ownerId(folder.getOwner() == null ? null : folder.getOwner().getId())
      .updatedAt(folder.getUpdatedAt())
      .createdAt(folder.getCreatedAt())
      .build();
  }
}
