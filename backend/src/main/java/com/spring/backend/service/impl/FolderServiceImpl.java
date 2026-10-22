package com.spring.backend.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spring.backend.common.utils.StorageNameUtils;
import com.spring.backend.dto.folder.BulkDeleteRequest;
import com.spring.backend.dto.folder.BulkMoveRequest;
import com.spring.backend.dto.folder.CopyFolderRequest;
import com.spring.backend.dto.folder.CreateFolderRequest;
import com.spring.backend.dto.folder.DocumentSummaryResponse;
import com.spring.backend.dto.folder.FolderContentsResponse;
import com.spring.backend.dto.folder.FolderPathResponse;
import com.spring.backend.dto.folder.FolderResponse;
import com.spring.backend.dto.folder.FolderSummaryResponse;
import com.spring.backend.dto.folder.FolderTreeResponse;
import com.spring.backend.dto.folder.UpdateFolderRequest;
import com.spring.backend.entity.Document;
import com.spring.backend.entity.Folder;
import com.spring.backend.entity.User;
import com.spring.backend.exception.AppException;
import com.spring.backend.exception.ErrorCode;
import com.spring.backend.repository.DocumentRepository;
import com.spring.backend.repository.FolderRepository;
import com.spring.backend.repository.UserRepository;
import com.spring.backend.service.FolderAccessService;
import com.spring.backend.service.FolderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class FolderServiceImpl implements FolderService {

  private final FolderRepository folderRepository;
  private final DocumentRepository documentRepository;
  private final UserRepository userRepository;
  private final FolderAccessService folderAccessService;

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
    if (request.getParentFolderName() != null && !request.getParentFolderName().isBlank()) {
      parent = folderAccessService.requireFolderByName(request.getParentFolderName(), ownerId);
      folderAccessService.requireEdit(parent, ownerId);
    }

    User owner = userRepository.findById(ownerId)
      .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

    // 3. Sinh tên duy nhất trong cùng cấp (abc -> abc (1))
    String parentFolderName = parent == null ? null : parent.getName();
    String uniqueName = StorageNameUtils.generateUniqueName(
      findSiblingNames(parentFolderName, ownerId), request.getName());

    // 4. Lưu folder mới
    Folder folder = Folder.builder()
      .name(uniqueName)
      .owner(owner)
      .parentFolder(parentFolderName)
      .build();

    Folder saved = folderRepository.save(folder);
    log.debug("Created folder {} (owner={}, parentName={})", saved.getId(), ownerId, parentFolderName);

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
    List<Folder> roots = folderRepository.findByOwner_IdAndParentFolderIsNull(userId);
    return roots.stream().map(this::toFolderResponse).toList();
  }

  @Override
  public FolderContentsResponse getContents(UUID folderId, UUID userId, int pageNo, int pageSize, String sortBy, String sortDir) {
    // 1. Lấy folder (404) + check quyền VIEW (403)
    Folder folder = folderAccessService.requireFolder(folderId);
    folderAccessService.requireView(folder, userId);

    String parentFolderName = folder.getName();
    int size = Math.max(pageSize, 1);
    int page = Math.max(pageNo, 0);
    Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir));

    // 2. Lấy subfolders và documents
    List<Folder> subFolders = folderRepository.findByOwner_IdAndParentFolder(userId, parentFolderName, pageable).getContent();
    List<Document> documents = documentRepository.findByFolder_Id(folder.getId(), pageable).getContent();

    return FolderContentsResponse.builder()
      .folder(new FolderContentsResponse.FolderInfo(folder.getId(), folder.getName()))
      .subFolders(subFolders.stream().map(this::toFolderSummary).toList())
      .documents(documents.stream().map(this::toDocumentSummary).toList())
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
      String parentName = current.getParentFolder();
      if (parentName == null) {
        break;
      }
      current = folderRepository.findByOwner_IdAndNameAndParentFolder(userId, parentName, getGrandParentName(current))
        .orElse(null);
    }

    return new FolderPathResponse(path);
  }

  @Override
  public FolderTreeResponse getTree(UUID userId, String parentFolderName) {
    List<Folder> folders;
    if (parentFolderName == null || parentFolderName.isBlank()) {
      folders = folderRepository.findByOwner_IdAndParentFolderIsNull(userId);
    } else {
      folders = folderRepository.findByOwner_IdAndParentFolder(userId, parentFolderName);
    }

    List<FolderTreeResponse.FolderTreeNode> nodes = folders.stream()
      .map(f -> buildTreeNode(f, userId))
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
    boolean moveToRoot = request.getParentFolderName() != null && request.getParentFolderName().equals("/ROOT");
    boolean moveToParent = request.getParentFolderName() != null && !request.getParentFolderName().equals("/ROOT");

    // 3. Không có gì để thay đổi -> 400
    if (!rename && !moveToRoot && !moveToParent) {
      throw new AppException(ErrorCode.VALIDATION_ERROR);
    }

    // 4. Gửi đồng thời moveToRoot và parentFolderName -> mơ hồ -> 400
    if (moveToRoot && moveToParent) {
      throw new AppException(ErrorCode.VALIDATION_ERROR);
    }

    // 5. Xác định parent đích (null = gốc) và cần quyền EDIT trên đích khi di chuyển
    Folder targetParent = null;
    String targetParentName = null;
    if (moveToParent) {
      targetParent = folderAccessService.requireFolderByName(request.getParentFolderName(), userId);
      folderAccessService.requireEdit(targetParent, userId);
      targetParentName = targetParent.getName();

      // Chống cycle: đích không được là chính nó hoặc hậu duệ của nó
      if (isDescendantOf(targetParent, folder.getId(), userId)) {
        throw new AppException(ErrorCode.VALIDATION_ERROR);
      }
    } else if (moveToRoot) {
      targetParentName = null;
    }

    // 6. Validate tên mới (nếu đổi tên)
    String desiredName = rename ? request.getName() : folder.getName();
    if (rename && !StorageNameUtils.isValidStorageName(desiredName)) {
      throw new AppException(ErrorCode.INVALID_FOLDER_NAME,
        Map.of("name", String.valueOf(desiredName)));
    }

    // 7. Cấp dùng để kiểm tra trùng tên: cấp đích khi di chuyển, ngược lại là cấp hiện tại
    String nameParentName;
    if (moveToRoot) {
      nameParentName = null;
    } else if (moveToParent) {
      nameParentName = targetParentName;
    } else {
      nameParentName = folder.getParentFolder();
    }

    // 8. Tự sinh tên duy nhất trong cấp đích (loại trừ chính folder này: abc -> abc (1))
    String uniqueName = StorageNameUtils.generateUniqueName(
      findSiblingNamesExcluding(nameParentName, userId, folder.getId()), desiredName);

    // 9. Cập nhật và lưu
    String oldName = folder.getName();
    String oldParentName = folder.getParentFolder();
    folder.setName(uniqueName);
    folder.setParentFolder(targetParentName);

    Folder saved = folderRepository.save(folder);

    // 10. Nếu đổi tên hoặc di chuyển, cần cập nhật parent_folder trên các con cháu
    if (!oldName.equals(uniqueName) || !java.util.Objects.equals(oldParentName, targetParentName)) {
      updateDescendantsParentFolder(oldName, saved.getName(), userId);
    }

    log.debug("Updated folder {} (name={}, parent={})", saved.getId(), saved.getName(), saved.getParentFolder());

    return toFolderResponse(saved);
  }

  @Override
  @Transactional
  public void deleteHard(UUID folderId, UUID userId) {
    // 1. Lấy folder (404) + chỉ OWNER mới được xóa (403; quyền EDIT được chia sẻ không cho xóa)
    Folder folder = folderAccessService.requireFolder(folderId);
    folderAccessService.requireOwner(folder, userId);

    // 2. Xóa cứng đệ quy toàn bộ subFolder + document con trong cùng 1 transaction
    deleteRecursively(folder, userId);
    log.debug("Hard-deleted folder {} (owner={}) with all descendants", folderId, userId);
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

    // Copy recursively
    String newName = StorageNameUtils.generateUniqueName(
      findSiblingNames(target.getName(), userId), source.getName() + "_copy");

    Folder newFolder = copyFolderRecursive(source, target.getName(), newName, userId);
    return toFolderResponse(newFolder);
  }

  @Override
  @Transactional
  public void bulkMove(BulkMoveRequest request, UUID userId) {
    Folder target = folderAccessService.requireFolder(request.getTargetFolderId());
    folderAccessService.requireEdit(target, userId);

    for (UUID folderId : request.getIds()) {
      Folder folder = folderAccessService.requireFolder(folderId);
      folderAccessService.requireEdit(folder, userId);

      // Check cycle
      if (isDescendantOf(target, folder.getId(), userId)) {
        throw new AppException(ErrorCode.VALIDATION_ERROR);
      }

      String oldName = folder.getName();
      folder.setParentFolder(target.getName());
      folderRepository.save(folder);
      updateDescendantsParentFolder(oldName, target.getName(), userId);
    }
  }

  // ========== Helper methods ==========

  private List<String> findSiblingNames(String parentFolderName, UUID ownerId) {
    List<Folder> siblings;
    if (parentFolderName == null) {
      siblings = folderRepository.findByOwner_IdAndParentFolderIsNull(ownerId);
    } else {
      siblings = folderRepository.findByOwner_IdAndParentFolder(ownerId, parentFolderName);
    }
    return siblings.stream().map(Folder::getName).toList();
  }

  private List<String> findSiblingNamesExcluding(String parentFolderName, UUID ownerId, UUID excludeFolderId) {
    List<Folder> siblings;
    if (parentFolderName == null) {
      siblings = folderRepository.findByOwner_IdAndParentFolderIsNull(ownerId);
    } else {
      siblings = folderRepository.findByOwner_IdAndParentFolder(ownerId, parentFolderName);
    }
    return siblings.stream()
      .filter(sibling -> !sibling.getId().equals(excludeFolderId))
      .map(Folder::getName)
      .toList();
  }

  private String getGrandParentName(Folder folder) {
    String parentName = folder.getParentFolder();
    if (parentName == null) return null;
    return folderRepository.findByOwner_IdAndNameAndParentFolder(folder.getOwner().getId(), parentName, null)
      .map(Folder::getParentFolder)
      .orElse(null);
  }

  private boolean isDescendantOf(Folder candidate, UUID ancestorId, UUID userId) {
    Folder current = candidate;
    while (current != null) {
      if (current.getId().equals(ancestorId)) {
        return true;
      }
      String parentName = current.getParentFolder();
      if (parentName == null) break;
      current = folderRepository.findByOwner_IdAndNameAndParentFolder(userId, parentName, getGrandParentName(current))
        .orElse(null);
    }
    return false;
  }

  private void deleteRecursively(Folder folder, UUID userId) {
    // Delete child folders first
    List<Folder> children = folderRepository.findByOwner_IdAndParentFolder(userId, folder.getName());
    for (Folder child : children) {
      deleteRecursively(child, userId);
    }
    // Delete documents in this folder
    documentRepository.deleteAll(documentRepository.findByFolder_Id(folder.getId()));
    // Delete this folder
    folderRepository.delete(folder);
  }

  private void updateDescendantsParentFolder(String oldParentName, String newParentName, UUID userId) {
    List<Folder> children = folderRepository.findByOwner_IdAndParentFolder(userId, oldParentName);
    for (Folder child : children) {
      child.setParentFolder(newParentName);
      folderRepository.save(child);
      // Recursively update deeper descendants
      updateDescendantsParentFolder(child.getName(), newParentName, userId);
    }
  }

  private Folder copyFolderRecursive(Folder source, String targetParentName, String newName, UUID userId) {
    // Create new folder
    Folder newFolder = Folder.builder()
      .name(newName)
      .owner(source.getOwner())
      .parentFolder(targetParentName)
      .build();
    Folder saved = folderRepository.save(newFolder);

    // Copy documents in this folder
    List<Document> documents = documentRepository.findByFolder_Id(source.getId());
    for (Document doc : documents) {
      // Note: In real implementation, you'd copy the MinIO object here
      // For now, we'll just create a new document record pointing to the same object
      Document newDoc = Document.builder()
        .name(doc.getName())
        .folder(saved)
        .owner(doc.getOwner())
        .objectKey(doc.getObjectKey())
        .size(doc.getSize())
        .mimeType(doc.getMimeType())
        .build();
      documentRepository.save(newDoc);
    }

    // Recursively copy subfolders
    List<Folder> children = folderRepository.findByOwner_IdAndParentFolder(userId, source.getName());
    for (Folder child : children) {
      String childNewName = StorageNameUtils.generateUniqueName(
        findSiblingNames(saved.getName(), userId), child.getName() + "_copy");
      copyFolderRecursive(child, saved.getName(), childNewName, userId);
    }

    return saved;
  }

  private FolderTreeResponse.FolderTreeNode buildTreeNode(Folder folder, UUID userId) {
    List<Folder> children = folderRepository.findByOwner_IdAndParentFolder(userId, folder.getName());
    boolean hasChildren = !children.isEmpty();

    List<FolderTreeResponse.FolderTreeNode> childNodes = new ArrayList<>();
    if (hasChildren) {
      // Only load first level for lazy loading
      // Client will request children when expanding
    }

    return FolderTreeResponse.FolderTreeNode.builder()
      .id(folder.getId().toString())
      .name(folder.getName())
      .hasChildren(hasChildren)
      .children(childNodes)
      .build();
  }

  private Sort buildSort(String sortBy, String sortDir) {
    return sortDir.equalsIgnoreCase(Sort.Direction.DESC.name())
      ? Sort.by(sortBy).descending()
      : Sort.by(sortBy).ascending();
  }

  private FolderResponse toFolderResponse(Folder folder) {
    return FolderResponse.builder()
      .id(folder.getId())
      .name(folder.getName())
      .parentFolderName(folder.getParentFolder())
      .ownerId(folder.getOwner() == null ? null : folder.getOwner().getId())
      .updatedAt(folder.getUpdatedAt())
      .createdAt(folder.getCreatedAt())
      .build();
  }

  private FolderSummaryResponse toFolderSummary(Folder folder) {
    return FolderSummaryResponse.builder()
      .id(folder.getId())
      .name(folder.getName())
      .hasChildren(folderRepository.existsByOwner_IdAndParentFolder(folder.getOwner().getId(), folder.getName()))
      .build();
  }

  private DocumentSummaryResponse toDocumentSummary(Document document) {
    return DocumentSummaryResponse.builder()
      .id(document.getId())
      .name(document.getName())
      .size(document.getSize())
      .mimeType(document.getMimeType())
      .createdAt(document.getCreatedAt())
      .updatedAt(document.getUpdatedAt())
      .build();
  }
}