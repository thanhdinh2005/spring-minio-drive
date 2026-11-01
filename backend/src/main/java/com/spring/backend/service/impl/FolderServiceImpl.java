package com.spring.backend.service.impl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.spring.backend.common.utils.StorageNameUtils;
import com.spring.backend.dto.folder.BulkDeleteRequest;
import com.spring.backend.dto.folder.BulkMoveRequest;
import com.spring.backend.dto.folder.CopyFolderRequest;
import com.spring.backend.dto.folder.CreateFolderRequest;
import com.spring.backend.dto.folder.FolderContentsResponse;
import com.spring.backend.dto.folder.FolderPathResponse;
import com.spring.backend.dto.folder.FolderResponse;
import com.spring.backend.dto.folder.FolderTreeResponse;
import com.spring.backend.dto.folder.UpdateFolderRequest;
import com.spring.backend.entity.Document;
import com.spring.backend.entity.Folder;
import com.spring.backend.entity.User;
import com.spring.backend.exception.AppException;
import com.spring.backend.exception.ErrorCode;
import com.spring.backend.repository.DocumentRepository;
import com.spring.backend.repository.FolderRepository;
import com.spring.backend.repository.PermissionRepository;
import com.spring.backend.repository.UserRepository;
import com.spring.backend.service.FolderAccessService;
import com.spring.backend.service.FolderService;
import com.spring.backend.service.StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class FolderServiceImpl implements FolderService {

  private static final Set<String> SORTABLE_FIELDS =
    Set.of("id", "name", "createdAt", "updatedAt");
  private static final String DEFAULT_SORT_FIELD = "updatedAt";

  private final FolderRepository folderRepository;
  private final DocumentRepository documentRepository;
  private final UserRepository userRepository;
  private final PermissionRepository permissionRepository;
  private final FolderAccessService folderAccessService;
  private final StorageService storageService;

  @Override
  @Transactional
  public FolderResponse create(CreateFolderRequest request, UUID ownerId) {
    // 1. Validate tên folder
    if (!StorageNameUtils.isValidStorageName(request.getName())) {
      throw new AppException(ErrorCode.INVALID_FOLDER_NAME,
        Map.of("name", String.valueOf(request.getName())));
    }

    // 2. Nếu có parent: kiểm tra tồn tại (404) + quyền EDIT trên parent (403)
    Folder parent = null;
    if (request.getParentId() != null) {
      parent = folderAccessService.requireFolder(request.getParentId());
      folderAccessService.requireEdit(parent, ownerId);
    }

    User owner = userRepository.findById(ownerId)
      .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

    // 3. Sinh tên duy nhất trong cùng cấp (abc -> abc (1))
    String uniqueName = StorageNameUtils.generateUniqueName(
      findSiblingNames(parent), request.getName());

    // 4. Lưu folder mới
    Folder folder = Folder.builder()
      .name(uniqueName)
      .owner(owner)
      .parent(parent)
      .build();

    Folder saved = folderRepository.save(folder);
    log.debug("Created folder {} (owner={}, parentId={})", saved.getId(), ownerId, request.getParentId());

    return toFolderResponse(saved);
  }

  @Override
  public FolderResponse getById(UUID folderId, UUID userId) {
    Folder folder = folderAccessService.requireFolder(folderId);
    folderAccessService.requireView(folder, userId);
    return toFolderResponse(folder);
  }

  @Override
  public List<FolderResponse> getRootFolders(UUID userId) {
    List<Folder> roots = folderRepository.findByOwner_IdAndParentIsNull(userId);
    return roots.stream().map(this::toFolderResponse).toList();
  }

  @Override
  public FolderContentsResponse getContents(UUID folderId, UUID userId, int pageNo, int pageSize, String sortBy, String sortDir) {
    // 1. Lấy folder (404) + check quyền VIEW (403)
    Folder folder = folderAccessService.requireFolder(folderId);
    folderAccessService.requireView(folder, userId);

    int size = Math.max(pageSize, 1);
    int page = Math.max(pageNo, 0);

    // 2. Folder và document cùng nằm trong một danh sách để phân trang chung;
    // không có sortBy thì giữ mặc định folder trước, document sau
    List<FolderContentsResponse.ContentItem> items;
    if (sortBy == null || sortBy.isBlank()) {
      items = new ArrayList<>();
      folderRepository.findByParent_Id(folder.getId())
        .forEach(sub -> items.add(folderItem(sub)));
      documentRepository.findByFolder_Id(folder.getId())
        .forEach(doc -> items.add(documentItem(doc)));
    } else {
      String field = SORTABLE_FIELDS.contains(sortBy) ? sortBy : DEFAULT_SORT_FIELD;
      Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
      items = sortedItems(folder, field, direction);
    }

    // 3. Cắt theo trang
    int totalElements = items.size();
    int totalPages = (totalElements + size - 1) / size;
    int from = Math.min(page * size, totalElements);
    int to = Math.min(from + size, totalElements);

    return FolderContentsResponse.builder()
      .folder(new FolderContentsResponse.FolderInfo(folder.getId(), folder.getName()))
      .items(List.copyOf(items.subList(from, to)))
      .pageNo(page)
      .pageSize(size)
      .totalElements(totalElements)
      .totalPages(totalPages)
      .last(page >= totalPages - 1)
      .build();
  }

  @Override
  public FolderPathResponse getPath(UUID folderId, UUID userId) {
    Folder folder = folderAccessService.requireFolder(folderId);
    folderAccessService.requireView(folder, userId);

    List<FolderPathResponse.PathItem> path = new ArrayList<>();
    Folder current = folder;
    while (current != null) {
      path.add(0, new FolderPathResponse.PathItem(current.getId(), current.getName()));
      current = current.getParent();
    }

    return new FolderPathResponse(path);
  }

  @Override
  public FolderTreeResponse getTree(UUID userId, UUID parentId) {
    List<Folder> folders;
    if (parentId == null) {
      folders = folderRepository.findByOwner_IdAndParentIsNull(userId);
    } else {
      folders = folderRepository.findByParent_Id(parentId);
    }

    List<FolderTreeResponse.FolderTreeNode> nodes = folders.stream()
      .map(this::buildTreeNode)
      .toList();

    return new FolderTreeResponse(nodes);
  }

  @Override
  @Transactional
  public FolderResponse update(UUID folderId, UpdateFolderRequest request, UUID userId) {
    // 1. Lấy folder (404)
    Folder folder = folderAccessService.requireFolder(folderId);

    // 2. Cần quyền EDIT trên vị trí cũ (403)
    folderAccessService.requireEdit(folder, userId);

    boolean rename = request.getName() != null && !request.getName().isBlank();
    // moveToRoot chỉ có hiệu lực khi gửi tường minh true; null/false => không về root
    boolean moveToRoot = Boolean.TRUE.equals(request.getMoveToRoot());
    boolean moveToParent = request.getParentId() != null;

    // 3. Không có gì để thay đổi -> 400
    if (!rename && !moveToRoot && !moveToParent) {
      throw new AppException(ErrorCode.VALIDATION_ERROR);
    }

    // 4. Gửi đồng thời moveToRoot và parentId -> mơ hồ -> 400
    if (moveToRoot && moveToParent) {
      throw new AppException(ErrorCode.VALIDATION_ERROR);
    }

    // 5. Mặc định giữ nguyên cha hiện tại; chỉ đổi khi được yêu cầu tường minh
    Folder targetParent = folder.getParent();
    if (moveToParent) {
      targetParent = folderAccessService.requireFolder(request.getParentId());
      folderAccessService.requireEdit(targetParent, userId);

      // Chống cycle: đích không được là chính nó hoặc hậu duệ của nó
      if (targetParent.getId().equals(folder.getId()) || isDescendantOf(targetParent, folder.getId())) {
        throw new AppException(ErrorCode.VALIDATION_ERROR);
      }
    } else if (moveToRoot) {
      targetParent = null;
    }

    // 6. Validate tên mới (nếu đổi tên)
    String desiredName = rename ? request.getName() : folder.getName();
    if (rename && !StorageNameUtils.isValidStorageName(desiredName)) {
      throw new AppException(ErrorCode.INVALID_FOLDER_NAME,
        Map.of("name", String.valueOf(desiredName)));
    }

    // 7. Cấp đích để kiểm tra trùng tên (bằng parent hiện tại nếu không di chuyển);
    //    tự sinh tên duy nhất trong cấp đó, loại trừ chính folder này (abc -> abc (1))
    String uniqueName = StorageNameUtils.generateUniqueName(
      findSiblingNamesExcluding(targetParent, folder.getId()), desiredName);

    // 9. Cập nhật và lưu
    folder.setName(uniqueName);
    folder.setParent(targetParent);

    Folder saved = folderRepository.save(folder);

    log.debug("Updated folder {} (name={}, parentId={})", saved.getId(), saved.getName(),
      saved.getParent() == null ? null : saved.getParent().getId());

    return toFolderResponse(saved);
  }

  @Override
  @Transactional
  public void deleteHard(UUID folderId, UUID userId) {
    // 1. Lấy folder (404) + chỉ OWNER mới được xóa (403; quyền EDIT được chia sẻ không cho xóa)
    Folder folder = folderAccessService.requireFolder(folderId);
    folderAccessService.requireOwner(folder, userId);

    // 2. Gom toàn bộ hậu duệ; xoá object S3 sau commit, xoá DB (permission + document + folder) trong transaction
    List<UUID> subtreeIds = collectSubtreeIds(folder);
    List<String> objectKeys = collectObjectKeys(subtreeIds);

    permissionRepository.deleteByFolder_IdIn(subtreeIds);
    // Xoá document trước để không vi phạm FK documents.folder_id khi xoá folder
    documentRepository.deleteByFolder_IdIn(subtreeIds);

    // collectSubtreeIds trả pre-order (cha trước con); xoá ngược lại (con trước cha)
    // để không vi phạm FK self-reference folders.parent_id
    List<UUID> deleteOrder = new ArrayList<>(subtreeIds);
    Collections.reverse(deleteOrder);
    for (UUID descendantId : deleteOrder) {
      folderRepository.deleteById(descendantId);
    }

    deleteObjectsOnCompletion(objectKeys);
    log.debug("Hard-deleted folder {} (owner={}) with {} folder(s) and {} object(s)",
      folderId, userId, subtreeIds.size(), objectKeys.size());
  }

  @Override
  @Transactional
  public void bulkDelete(BulkDeleteRequest request, UUID userId) {
    for (UUID folderId : request.getIds()) {
      deleteHard(folderId, userId);
    }
  }

  @Override
  @Transactional
  public FolderResponse copy(UUID folderId, CopyFolderRequest request, UUID userId) {
    Folder source = folderAccessService.requireFolder(folderId);
    folderAccessService.requireView(source, userId);

    Folder target = folderAccessService.requireFolder(request.getTargetFolderId());
    folderAccessService.requireEdit(target, userId);

    String newName = StorageNameUtils.generateUniqueName(
      findSiblingNames(target), source.getName() + "_copy");

    Folder newFolder = copyFolderRecursive(source, target, newName, userId);
    return toFolderResponse(newFolder);
  }

  @Override
  @Transactional
  public void bulkMove(BulkMoveRequest request, UUID userId) {
    Folder target = folderAccessService.requireFolder(request.getTargetFolderId());
    folderAccessService.requireEdit(target, userId);

    // Tên ở cấp đích đã bị chiếm, cộng dồn khi di chuyển nhiều folder cùng lúc
    List<String> targetNames = new ArrayList<>(findSiblingNames(target));

    for (UUID folderId : request.getIds()) {
      Folder folder = folderAccessService.requireFolder(folderId);
      folderAccessService.requireEdit(folder, userId);

      // Check cycle
      if (isDescendantOf(target, folder.getId())) {
        throw new AppException(ErrorCode.VALIDATION_ERROR);
      }

      targetNames.remove(folder.getName());
      String uniqueName = StorageNameUtils.generateUniqueName(targetNames, folder.getName());
      targetNames.add(uniqueName);

      folder.setName(uniqueName);
      folder.setParent(target);
      folderRepository.save(folder);
    }
  }

  // ========== Helper methods ==========

  private List<String> findSiblingNames(Folder parent) {
    return findSiblings(parent).stream().map(Folder::getName).toList();
  }

  private List<String> findSiblingNamesExcluding(Folder parent, UUID excludeFolderId) {
    return findSiblings(parent).stream()
      .filter(sibling -> !sibling.getId().equals(excludeFolderId))
      .map(Folder::getName)
      .toList();
  }

  // Tên phải duy nhất trong cấp đích bất kể owner, vì nhiều user có thể cùng có folder ở một cấp
  private List<Folder> findSiblings(Folder parent) {
    if (parent == null) {
      return folderRepository.findByParentIsNull();
    }
    return folderRepository.findByParent_Id(parent.getId());
  }

  private boolean isDescendantOf(Folder candidate, UUID ancestorId) {
    Folder current = candidate;
    while (current != null) {
      if (current.getId().equals(ancestorId)) {
        return true;
      }
      current = current.getParent();
    }
    return false;
  }

  private List<UUID> collectSubtreeIds(Folder root) {
    List<UUID> ids = new ArrayList<>();
    Deque<Folder> stack = new ArrayDeque<>();
    stack.push(root);
    while (!stack.isEmpty()) {
      Folder current = stack.pop();
      ids.add(current.getId());
      stack.addAll(folderRepository.findByParent_Id(current.getId()));
    }
    return ids;
  }

  private List<String> collectObjectKeys(List<UUID> folderIds) {
    if (folderIds.isEmpty()) {
      return List.of();
    }
    return documentRepository.findByFolder_IdIn(folderIds).stream()
      .map(Document::getObjectKey)
      .distinct()
      .toList();
  }

  private Folder copyFolderRecursive(Folder source, Folder targetParent, String newName, UUID userId) {
    // Người bấm copy sở hữu bản sao để quota và quyền được tính đúng
    User newOwner = userRepository.findById(userId)
      .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

    List<Document> documents = documentRepository.findByFolder_Id(source.getId());
    long copiedBytes = documents.stream().mapToLong(Document::getSize).sum();
    if (documentRepository.sumSizeByOwnerId(userId) + copiedBytes > newOwner.getStorageLimit()) {
      throw new AppException(ErrorCode.DOCUMENT_TOO_LARGE,
        Map.of("storageLimit", String.valueOf(newOwner.getStorageLimit()),
               "requiredSize", String.valueOf(copiedBytes)));
    }

    Folder newFolder = Folder.builder()
      .name(newName)
      .owner(newOwner)
      .parent(targetParent)
      .build();
    Folder saved = folderRepository.save(newFolder);

    for (Document doc : documents) {
      if (!storageService.objectExists(doc.getObjectKey())) {
        log.error("Cannot copy document {}: source object {} is missing", doc.getId(), doc.getObjectKey());
        throw new AppException(ErrorCode.STORAGE_ERROR);
      }

      String newObjectKey = String.format("%s/%s/%s_%s", userId, saved.getId(), UUID.randomUUID(),
        StorageNameUtils.sanitizeFileName(doc.getName()));
      try {
        storageService.copyObject(doc.getObjectKey(), newObjectKey);
      } catch (RuntimeException e) {
        log.error("Failed to copy file in storage: {} -> {}", doc.getObjectKey(), newObjectKey, e);
        throw new AppException(ErrorCode.STORAGE_ERROR);
      }

      documentRepository.save(Document.builder()
        .name(doc.getName())
        .folder(saved)
        .owner(newOwner)
        .objectKey(newObjectKey)
        .size(doc.getSize())
        .mimeType(doc.getMimeType())
        .build());
    }

    List<String> siblingNames = new ArrayList<>(findSiblingNames(saved));
    for (Folder child : folderRepository.findByParent_Id(source.getId())) {
      String childNewName = StorageNameUtils.generateUniqueName(siblingNames, child.getName() + "_copy");
      siblingNames.add(childNewName);
      copyFolderRecursive(child, saved, childNewName, userId);
    }

    return saved;
  }

  private List<FolderContentsResponse.ContentItem> sortedItems(Folder folder, String field, Sort.Direction direction) {
    Comparator<Folder> folderOrder = switch (field) {
      case "name" -> Comparator.comparing(Folder::getName);
      case "id" -> Comparator.comparing(Folder::getId);
      case "createdAt" -> Comparator.comparing(Folder::getCreatedAt,
        Comparator.nullsLast(Comparator.naturalOrder()));
      default -> Comparator.comparing(Folder::getUpdatedAt,
        Comparator.nullsLast(Comparator.naturalOrder()));
    };
    Comparator<Document> documentOrder = switch (field) {
      case "name" -> Comparator.comparing(Document::getName);
      case "id" -> Comparator.comparing(Document::getId);
      case "createdAt" -> Comparator.comparing(Document::getCreatedAt,
        Comparator.nullsLast(Comparator.naturalOrder()));
      default -> Comparator.comparing(Document::getUpdatedAt,
        Comparator.nullsLast(Comparator.naturalOrder()));
    };
    if (direction == Sort.Direction.DESC) {
      folderOrder = folderOrder.reversed();
      documentOrder = documentOrder.reversed();
    }

    List<FolderContentsResponse.ContentItem> items = new ArrayList<>();
    folderRepository.findByParent_Id(folder.getId()).stream()
      .sorted(folderOrder)
      .forEach(sub -> items.add(folderItem(sub)));
    documentRepository.findByFolder_Id(folder.getId()).stream()
      .sorted(documentOrder)
      .forEach(doc -> items.add(documentItem(doc)));
    return items;
  }

  private FolderContentsResponse.ContentItem folderItem(Folder folder) {
    return FolderContentsResponse.ContentItem.builder()
      .itemType("FOLDER")
      .id(folder.getId())
      .name(folder.getName())
      .hasChildren(folderRepository.existsByParent_Id(folder.getId()))
      .build();
  }

  private FolderContentsResponse.ContentItem documentItem(Document document) {
    return FolderContentsResponse.ContentItem.builder()
      .itemType("DOCUMENT")
      .id(document.getId())
      .name(document.getName())
      .hasChildren(true)
      .size(document.getSize())
      .mimeType(document.getMimeType())
      .build();
  }

  private void deleteObjectsOnCompletion(List<String> objectKeys) {
    if (objectKeys.isEmpty()) {
      return;
    }
    Runnable delete = () -> objectKeys.forEach(key -> {
      try {
        storageService.deleteObject(key);
      } catch (RuntimeException e) {
        log.error("Failed to delete object {}", key, e);
      }
    });

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCompletion(int status) {
          // Chỉ xoá object khi commit; rollback thì giữ file vì bản ghi DB vẫn còn tham chiếu
          if (status == TransactionSynchronization.STATUS_COMMITTED) {
            delete.run();
          }
        }
      });
    } else {
      delete.run();
    }
  }

  private FolderTreeResponse.FolderTreeNode buildTreeNode(Folder folder) {
    boolean hasChildren = folderRepository.existsByParent_Id(folder.getId());

    return FolderTreeResponse.FolderTreeNode.builder()
      .id(folder.getId().toString())
      .name(folder.getName())
      .hasChildren(hasChildren)
      .children(new ArrayList<>())
      .build();
  }

  private Sort buildSort(String sortBy, String sortDir) {
    String field = sortBy != null && SORTABLE_FIELDS.contains(sortBy) ? sortBy : DEFAULT_SORT_FIELD;
    Sort.Direction direction = sortDir != null && sortDir.equalsIgnoreCase("asc")
      ? Sort.Direction.ASC : Sort.Direction.DESC;
    return Sort.by(direction, field);
  }

  private FolderResponse toFolderResponse(Folder folder) {
    Folder parent = folder.getParent();
    return FolderResponse.builder()
      .id(folder.getId())
      .name(folder.getName())
      .parentId(parent == null ? null : parent.getId())
      .parentName(parent == null ? null : parent.getName())
      .ownerId(folder.getOwner() == null ? null : folder.getOwner().getId())
      .updatedAt(folder.getUpdatedAt())
      .createdAt(folder.getCreatedAt())
      .build();
  }

  @Override
  public Page<FolderResponse> filterFolders(UUID userId, String name, UUID parentId, int pageNo, int pageSize, String sortBy, String sortDir) {
    int size = Math.max(pageSize, 1);
    int page = Math.max(pageNo, 0);
    Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir));
    Page<Folder> pages = folderRepository.filterFolders(userId, normalizeFilter(name), parentId, pageable);
    return pages.map(this::toFolderResponse);
  }

  private static String normalizeFilter(String value) {
    return (value == null || value.isBlank()) ? null : value.trim();
  }
}
