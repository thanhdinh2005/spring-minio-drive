package com.spring.backend.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.spring.backend.common.utils.StorageNameUtils;
import com.spring.backend.dto.document.CopyDocumentRequest;
import com.spring.backend.dto.document.DocumentResponse;
import com.spring.backend.dto.document.PresignedUrlResponse;
import com.spring.backend.dto.document.UpdateDocumentRequest;
import com.spring.backend.dto.document.UploadDocumentRequest;
import com.spring.backend.dto.folder.BulkDeleteRequest;
import com.spring.backend.entity.Document;
import com.spring.backend.entity.Folder;
import com.spring.backend.entity.User;
import com.spring.backend.exception.AppException;
import com.spring.backend.exception.ErrorCode;
import com.spring.backend.repository.DocumentRepository;
import com.spring.backend.repository.FolderRepository;
import com.spring.backend.repository.UserRepository;
import com.spring.backend.service.DocumentService;
import com.spring.backend.service.FolderAccessService;
import com.spring.backend.service.StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.model.CompletedPart;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class DocumentServiceImpl implements DocumentService {

  private static final long PRESIGN_EXPIRES_SECONDS = 300L;
  private static final long PART_SIZE = 5L * 1024 * 1024;
  private static final Set<String> SORTABLE_FIELDS =
    Set.of("id", "name", "size", "mimeType", "createdAt", "updatedAt");
  private static final String DEFAULT_SORT_FIELD = "updatedAt";

  private final DocumentRepository documentRepository;
  private final UserRepository userRepository;
  private final FolderAccessService folderAccessService;
  private final StorageService storageService;

  @Value("${storage.allowed-mime-types:application/pdf,image/jpeg,image/png,image/gif,image/webp,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-powerpoint,application/vnd.openxmlformats-officedocument.presentationml.presentation,application/zip,application/x-zip-compressed,text/plain,text/csv}")
  private List<String> allowedMimeTypes;

  @Value("${storage.max-file-size:10485760}")
  private long maxFileSize;

  @Override
  public PresignedUrlResponse getDownloadUrl(UUID documentId, UUID userId) {
    Document document = requireDocument(documentId);
    folderAccessService.requireView(document.getFolder(), userId);

    String contentDisposition = buildAttachmentDisposition(document.getName());
    String url = storageService.presignGet(
      document.getObjectKey(), PRESIGN_EXPIRES_SECONDS, contentDisposition);

    return toResponse(url);
  }

  @Override
  public PresignedUrlResponse getPreviewUrl(UUID documentId, UUID userId) {
    Document document = requireDocument(documentId);
    folderAccessService.requireView(document.getFolder(), userId);

    if (!isPreviewable(document.getMimeType())) {
      throw new AppException(ErrorCode.VALIDATION_ERROR,
        Map.of("mimeType", String.valueOf(document.getMimeType())));
    }

    String url = storageService.presignGet(
      document.getObjectKey(), PRESIGN_EXPIRES_SECONDS, "inline");

    return toResponse(url);
  }

  @Override
  public PresignedUrlResponse getPublicDownloadUrl(UUID documentId, UUID publicRootFolderId) {
    Document document = requireDocument(documentId);
    if (!isWithin(document.getFolder(), publicRootFolderId)) {
      throw new AppException(ErrorCode.DOCUMENT_NOT_FOUND);
    }

    String contentDisposition = buildAttachmentDisposition(document.getName());
    String url = storageService.presignGet(
      document.getObjectKey(), PRESIGN_EXPIRES_SECONDS, contentDisposition);

    return toResponse(url);
  }

  @Override
  @Transactional
  public DocumentResponse upload(UUID folderId, UploadDocumentRequest request, UUID userId) {
    MultipartFile file = request.getFile();

    // 1. Validate file
    if (file == null || file.isEmpty()) {
      throw new AppException(ErrorCode.VALIDATION_ERROR, Map.of("file", "File is required"));
    }

    // 2. Check folder exists + EDIT permission
    Folder folder = folderAccessService.requireFolder(folderId);
    folderAccessService.requireEdit(folder, userId);

    // 3. Validate mimeType
    String mimeType = file.getContentType();
    if (mimeType == null || !isAllowedMimeType(mimeType)) {
      throw new AppException(ErrorCode.VALIDATION_ERROR,
        Map.of("mimeType", "File type not allowed: " + mimeType));
    }

    // 4. Validate file size
    long fileSize = file.getSize();
    if (fileSize > maxFileSize) {
      throw new AppException(ErrorCode.DOCUMENT_TOO_LARGE,
        Map.of("maxSize", String.valueOf(maxFileSize), "actualSize", String.valueOf(fileSize)));
    }

    // 5. Check storage quota từ SUM(documents.size) của owner
    User owner = userRepository.findById(userId)
      .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

    long currentUsage = documentRepository.sumSizeByOwnerId(userId);
    long storageLimit = owner.getStorageLimit();
    if (currentUsage + fileSize > storageLimit) {
      throw new AppException(ErrorCode.DOCUMENT_TOO_LARGE,
        Map.of("storageLimit", String.valueOf(storageLimit),
               "currentUsage", String.valueOf(currentUsage),
               "fileSize", String.valueOf(fileSize)));
    }

    // 6. Generate objectKey: {userId}/{folderId}/{uuid}_{sanitizedFileName}
    String sanitizedName = StorageNameUtils.sanitizeFileName(file.getOriginalFilename());
    String objectKey = String.format("%s/%s/%s_%s", userId, folderId, UUID.randomUUID(), sanitizedName);

    // 7. Upload to MinIO
    try {
      if (fileSize <= storageService.getMultipartThreshold()) {
        String presignedUrl = storageService.presignPut(
          objectKey, Duration.ofMinutes(10).getSeconds(), mimeType);
        putBytes(presignedUrl, file.getBytes(), mimeType);
      } else {
        uploadMultipart(objectKey, mimeType, file);
      }
    } catch (IOException | RuntimeException e) {
      log.error("Failed to upload file to storage: {}", objectKey, e);
      // Dọn object mồ côi ngay (đồng bộ): transaction sẽ rollback nên not dựa vào afterCommit
      cleanupOrphanObject(objectKey);
      throw new AppException(ErrorCode.STORAGE_ERROR);
    }

    // 8. Save document metadata
    Document document = Document.builder()
      .name(sanitizedName)
      .folder(folder)
      .owner(owner)
      .objectKey(objectKey)
      .size(fileSize)
      .mimeType(mimeType)
      .build();

    Document saved = documentRepository.save(document);

    log.debug("Uploaded document {} (owner={}, folder={}, size={})", saved.getId(), userId, folderId, fileSize);

    return toDocumentResponse(saved);
  }

  @Override
  public DocumentResponse getById(UUID documentId, UUID userId) {
    Document document = requireDocument(documentId);
    folderAccessService.requireView(document.getFolder(), userId);
    return toDocumentResponse(document);
  }

  @Override
  @Transactional
  public DocumentResponse update(UUID documentId, UpdateDocumentRequest request, UUID userId) {
    Document document = requireDocument(documentId);
    folderAccessService.requireEdit(document.getFolder(), userId);

    boolean rename = request.getName() != null && !request.getName().isBlank();
    boolean move = request.getFolderId() != null;

    if (!rename && !move) {
      throw new AppException(ErrorCode.VALIDATION_ERROR);
    }

    Folder targetFolder = document.getFolder();
    if (move) {
      targetFolder = folderAccessService.requireFolder(request.getFolderId());
      folderAccessService.requireEdit(targetFolder, userId);
      if (targetFolder.getId().equals(document.getFolder().getId())) {
        throw new AppException(ErrorCode.VALIDATION_ERROR,
          Map.of("folderId", "Document is already in this folder"));
      }
    }

    if (rename) {
      String newName = request.getName();
      if (!StorageNameUtils.isValidStorageName(newName)) {
        throw new AppException(ErrorCode.INVALID_FOLDER_NAME, Map.of("name", newName));
      }
      if (documentRepository.existsByNameAndFolder_IdAndIdNot(newName, targetFolder.getId(), documentId)) {
        throw new AppException(ErrorCode.VALIDATION_ERROR,
          Map.of("name", "File with this name already exists"));
      }
      document.setName(newName);
    }

    if (move) {
      document.setFolder(targetFolder);
    }

    Document saved = documentRepository.save(document);
    return toDocumentResponse(saved);
  }

  @Override
  @Transactional
  public void delete(UUID documentId, UUID userId) {
    Document document = requireDocument(documentId);
    folderAccessService.requireEdit(document.getFolder(), userId);

    String objectKey = document.getObjectKey();
    documentRepository.delete(document);

    // Xoá object sau khi commit để rollback không làm mất file vẫn còn tham chiếu
    deleteOnCompletion(objectKey);
    log.debug("Deleted document {} (by={})", documentId, userId);
  }

  @Override
  @Transactional
  public void bulkDelete(BulkDeleteRequest request, UUID userId) {
    for (UUID documentId : request.getIds()) {
      delete(documentId, userId);
    }
  }

  @Override
  @Transactional
  public DocumentResponse copy(UUID documentId, CopyDocumentRequest request, UUID userId) {
    Document source = requireDocument(documentId);
    folderAccessService.requireView(source.getFolder(), userId);

    Folder targetFolder = request.getTargetFolderId() != null
      ? folderAccessService.requireFolder(request.getTargetFolderId())
      : source.getFolder();
    folderAccessService.requireEdit(targetFolder, userId);

    User owner = userRepository.findById(userId)
      .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

    if (documentRepository.sumSizeByOwnerId(userId) + source.getSize() > owner.getStorageLimit()) {
      throw new AppException(ErrorCode.DOCUMENT_TOO_LARGE,
        Map.of("storageLimit", String.valueOf(owner.getStorageLimit()),
               "fileSize", String.valueOf(source.getSize())));
    }

    List<String> existingNames = documentRepository.findByFolder_Id(targetFolder.getId())
      .stream().map(Document::getName).toList();
    String newName = StorageNameUtils.generateUniqueName(existingNames, source.getName() + "_copy");

    if (!storageService.objectExists(source.getObjectKey())) {
      log.error("Cannot copy document {}: source object {} is missing", documentId, source.getObjectKey());
      throw new AppException(ErrorCode.STORAGE_ERROR);
    }

    String newObjectKey = String.format("%s/%s/%s_%s", userId, targetFolder.getId(), UUID.randomUUID(),
      StorageNameUtils.sanitizeFileName(newName));
    try {
      storageService.copyObject(source.getObjectKey(), newObjectKey);
    } catch (RuntimeException e) {
      log.error("Failed to copy file in storage: {} -> {}", source.getObjectKey(), newObjectKey, e);
      throw new AppException(ErrorCode.STORAGE_ERROR);
    }

    Document newDoc = Document.builder()
      .name(newName)
      .folder(targetFolder)
      .owner(owner)
      .objectKey(newObjectKey)
      .size(source.getSize())
      .mimeType(source.getMimeType())
      .build();

    Document saved = documentRepository.save(newDoc);
    return toDocumentResponse(saved);
  }

  @Override
  public Page<DocumentResponse> filterDocuments(UUID userId, UUID folderId, String name, String mimeType,
                                                int pageNo, int pageSize, String sortBy, String sortDir) {
    int size = Math.max(pageSize, 1);
    int page = Math.max(pageNo, 0);
    Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir));
    Page<Document> pages = documentRepository.filterDocuments(userId, folderId,
      normalizeFilter(name), normalizeFilter(mimeType), pageable);
    return pages.map(this::toDocumentResponse);
  }

  // ========== Helper methods ==========

  private Document requireDocument(UUID documentId) {
    return documentRepository.findById(documentId)
      .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));
  }

  private boolean isWithin(Folder candidate, UUID rootFolderId) {
    Set<UUID> visited = new HashSet<>();
    Folder current = candidate;
    while (current != null && visited.add(current.getId())) {
      if (current.getId().equals(rootFolderId)) {
        return true;
      }
      current = current.getParent();
    }
    return false;
  }

  private boolean isAllowedMimeType(String mimeType) {
    String normalized = mimeType.toLowerCase(Locale.ROOT);
    return allowedMimeTypes.stream().anyMatch(allowed -> {
      if (allowed.endsWith("/*")) {
        String prefix = allowed.substring(0, allowed.length() - 1);
        return normalized.startsWith(prefix);
      }
      return allowed.equalsIgnoreCase(normalized);
    });
  }

  private boolean isPreviewable(String mimeType) {
    if (mimeType == null) {
      return false;
    }
    String normalized = mimeType.toLowerCase(Locale.ROOT);
    return normalized.equals("application/pdf") || normalized.startsWith("image/");
  }

  private static String normalizeFilter(String value) {
    return (value == null || value.isBlank()) ? null : value.trim();
  }

  private Sort buildSort(String sortBy, String sortDir) {
    String field = sortBy != null && SORTABLE_FIELDS.contains(sortBy) ? sortBy : DEFAULT_SORT_FIELD;
    Sort.Direction direction = sortDir != null && sortDir.equalsIgnoreCase("asc")
      ? Sort.Direction.ASC : Sort.Direction.DESC;
    return Sort.by(direction, field);
  }

  private void putBytes(String presignedUrl, byte[] body, String contentType) throws IOException {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request = HttpRequest.newBuilder()
      .uri(java.net.URI.create(presignedUrl))
      .header("Content-Type", contentType)
      .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
      .build();

    try {
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        throw new IOException("Upload failed with status: " + response.statusCode() + " - " + response.body());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Upload interrupted", e);
    }
  }

  private void uploadMultipart(String objectKey, String contentType, MultipartFile file) throws IOException {
    String uploadId = storageService.initiateMultipartUpload(objectKey, contentType);
    try {
      uploadParts(uploadId, objectKey, contentType, file);
    } catch (IOException | RuntimeException e) {
      storageService.abortMultipartUpload(uploadId, objectKey);
      throw e;
    }
  }

  private void uploadParts(String uploadId, String objectKey, String contentType, MultipartFile file) throws IOException {
    long fileSize = file.getSize();
    int totalParts = (int) ((fileSize + PART_SIZE - 1) / PART_SIZE);
    List<CompletedPart> parts = new ArrayList<>(totalParts);
    HttpClient client = HttpClient.newHttpClient();

    // Đọc từng part tuần tự từ stream, không nạp cả file vào RAM
    try (InputStream stream = file.getInputStream()) {
      for (int i = 0; i < totalParts; i++) {
        int partNumber = i + 1;
        byte[] partData = stream.readNBytes((int) Math.min(PART_SIZE, fileSize - (long) i * PART_SIZE));

        String presignedUrl = storageService.presignUploadPart(
          uploadId, objectKey, partNumber, Duration.ofMinutes(10).getSeconds());
        HttpRequest request = HttpRequest.newBuilder()
          .uri(java.net.URI.create(presignedUrl))
          .header("Content-Type", contentType)
          .PUT(HttpRequest.BodyPublishers.ofByteArray(partData))
          .build();

        try {
          HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
          if (response.statusCode() >= 400) {
            throw new IOException("Part " + partNumber + " upload failed: " + response.statusCode());
          }
          // CompleteMultipartUpload cần đúng ETag từng part; thiếu thì fail sớm thay vì gửi parts rỗng
          String eTag = response.headers().firstValue("ETag")
            .orElseThrow(() -> new IOException("Part " + partNumber + " response has no ETag header"));
          parts.add(CompletedPart.builder()
            .partNumber(partNumber)
            .eTag(eTag.replace("\"", ""))
            .build());
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IOException("Part upload interrupted", e);
        }
      }
    }

    storageService.completeMultipartUpload(uploadId, objectKey, parts);
  }

  private void cleanupOrphanObject(String objectKey) {
    try {
      storageService.deleteObject(objectKey);
    } catch (RuntimeException e) {
      log.error("Failed to clean up orphan object {}", objectKey, e);
    }
  }

  private void deleteOnCompletion(String objectKey) {
    Runnable delete = () -> {
      try {
        storageService.deleteObject(objectKey);
      } catch (RuntimeException e) {
        log.error("Failed to delete object {}", objectKey, e);
      }
    };

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCompletion(int status) {
          // Chỉ xoá object khi transaction commit; rollback thì giữ file vì bản ghi DB vẫn còn tham chiếu
          if (status == TransactionSynchronization.STATUS_COMMITTED) {
            delete.run();
          }
        }
      });
    } else {
      delete.run();
    }
  }

  private String buildAttachmentDisposition(String fileName) {
    String safeName = fileName == null ? "download" : fileName.replace("\"", "");
    return "attachment; filename=\"" + safeName + "\"";
  }

  private PresignedUrlResponse toResponse(String url) {
    return PresignedUrlResponse.builder()
      .url(url)
      .expiresIn(PRESIGN_EXPIRES_SECONDS)
      .build();
  }

  private DocumentResponse toDocumentResponse(Document document) {
    return DocumentResponse.builder()
      .id(document.getId())
      .name(document.getName())
      .size(document.getSize())
      .mimeType(document.getMimeType())
      .objectKey(document.getObjectKey())
      .folderId(document.getFolder().getId())
      .ownerId(document.getOwner().getId())
      .createdAt(document.getCreatedAt())
      .updatedAt(document.getUpdatedAt())
      .build();
  }
}
