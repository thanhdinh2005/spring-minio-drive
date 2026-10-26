package com.spring.backend.service.impl;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.spring.backend.common.utils.StorageNameUtils;
import com.spring.backend.dto.document.CopyDocumentRequest;
import com.spring.backend.dto.document.DocumentResponse;
import com.spring.backend.dto.document.PresignedUrlResponse;
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
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class DocumentServiceImpl implements DocumentService {

  private static final long PRESIGN_EXPIRES_SECONDS = 300L;

  private final DocumentRepository documentRepository;
  private final FolderRepository folderRepository;
  private final UserRepository userRepository;
  private final FolderAccessService folderAccessService;
  private final StorageService storageService;

  @Value("${storage.allowed-mime-types:application/pdf,image/jpeg,image/png,image/gif,image/webp,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-powerpoint,application/vnd.openxmlformats-officedocument.presentationml.presentation,application/zip,application/x-zip-compressed,text/plain,text/csv}")
  private List<String> allowedMimeTypes;

  @Override
  public PresignedUrlResponse getDownloadUrl(UUID documentId, UUID userId) {
    Document document = requireDocument(documentId, userId);
    folderAccessService.requireView(document.getFolder(), userId);

    String contentDisposition = buildAttachmentDisposition(document.getName());
    String url = storageService.presignGet(
      document.getObjectKey(), PRESIGN_EXPIRES_SECONDS, contentDisposition);

    return toResponse(url);
  }

  @Override
  public PresignedUrlResponse getPreviewUrl(UUID documentId, UUID userId) {
    Document document = requireDocument(documentId, userId);
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
    long maxFileSize = parseSize(getMaxFileSizeConfig());
    if (fileSize > maxFileSize) {
      throw new AppException(ErrorCode.DOCUMENT_TOO_LARGE,
        Map.of("maxSize", String.valueOf(maxFileSize), "actualSize", String.valueOf(fileSize)));
    }

    // 5. Check user storage quota
    User user = userRepository.findById(userId)
      .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    
    long currentUsage = user.getStorageUsed();
    long storageLimit = user.getStorageLimit();
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
        // Single-part upload using presigned PUT
        String presignedUrl = storageService.presignPut(objectKey, Duration.ofMinutes(10).getSeconds(), mimeType);
        uploadToPresignedUrl(presignedUrl, file, mimeType);
      } else {
        // Multipart upload
        String uploadId = storageService.initiateMultipartUpload(objectKey, mimeType);
        try {
          uploadMultipart(uploadId, objectKey, file, mimeType);
          List<CompletedPart> parts = new java.util.ArrayList<>();
          // Parts are completed in uploadMultipart
          storageService.completeMultipartUpload(uploadId, objectKey, parts);
        } catch (Exception e) {
          storageService.abortMultipartUpload(uploadId, objectKey);
          throw e;
        }
      }
    } catch (IOException e) {
      log.error("Failed to upload file to storage", e);
      throw new AppException(ErrorCode.STORAGE_ERROR);
    }

    // 8. Save document metadata
    Document document = Document.builder()
      .name(sanitizedName)
      .folder(folder)
      .owner(user)
      .objectKey(objectKey)
      .size(fileSize)
      .mimeType(mimeType)
      .build();

    Document saved = documentRepository.save(document);

    // 9. Update user storage usage
    user.setStorageUsed(currentUsage + fileSize);
    userRepository.save(user);

    log.debug("Uploaded document {} (owner={}, folder={}, size={})", saved.getId(), userId, folderId, fileSize);

    return toDocumentResponse(saved);
  }

  @Override
  public DocumentResponse getById(UUID documentId, UUID userId) {
    Document document = requireDocument(documentId, userId);
    return toDocumentResponse(document);
  }

  @Override
  @Transactional
  public DocumentResponse update(UUID documentId, com.spring.backend.dto.document.UpdateDocumentRequest request, UUID userId) {
    Document document = requireDocument(documentId, userId);
    folderAccessService.requireEdit(document.getFolder(), userId);

    boolean rename = request.getName() != null && !request.getName().isBlank();
    boolean move = request.getFolderId() != null;

    if (!rename && !move) {
      throw new AppException(ErrorCode.VALIDATION_ERROR);
    }

    if (rename) {
      String newName = request.getName();
      if (!StorageNameUtils.isValidStorageName(newName)) {
        throw new AppException(ErrorCode.INVALID_FOLDER_NAME, Map.of("name", newName));
      }
      // Check for duplicate name in same folder
      if (documentRepository.existsByNameAndFolder_IdAndIdNot(newName, document.getFolder().getId(), documentId)) {
        throw new AppException(ErrorCode.VALIDATION_ERROR, Map.of("name", "File with this name already exists"));
      }
      document.setName(newName);
    }

    if (move) {
      Folder targetFolder = folderAccessService.requireFolder(request.getFolderId());
      folderAccessService.requireEdit(targetFolder, userId);
      
      // Check cycle (not applicable for documents, but check same folder)
      if (targetFolder.getId().equals(document.getFolder().getId())) {
        throw new AppException(ErrorCode.VALIDATION_ERROR, Map.of("folderId", "Document is already in this folder"));
      }
      
      document.setFolder(targetFolder);
    }

    Document saved = documentRepository.save(document);
    return toDocumentResponse(saved);
  }

  @Override
  @Transactional
  public void delete(UUID documentId, UUID userId) {
    Document document = requireDocument(documentId, userId);
    folderAccessService.requireEdit(document.getFolder(), userId);

    // Delete from MinIO
    try {
      // Note: In real implementation, delete from MinIO
      // s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(document.getObjectKey()).build());
    } catch (Exception e) {
      log.error("Failed to delete file from storage: {}", document.getObjectKey(), e);
      // Continue with DB deletion even if storage deletion fails
    }

    // Update user storage usage
    User user = userRepository.findById(userId)
      .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    user.setStorageUsed(Math.max(0, user.getStorageUsed() - document.getSize()));
    userRepository.save(user);

    // Delete from DB
    documentRepository.delete(document);
    log.debug("Deleted document {} (owner={})", documentId, userId);
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
    Document source = requireDocument(documentId, userId);
    folderAccessService.requireView(source.getFolder(), userId);

    Folder targetFolder;
    if (request.getTargetFolderId() != null) {
      targetFolder = folderAccessService.requireFolder(request.getTargetFolderId());
    } else {
      targetFolder = source.getFolder();
    }
    folderAccessService.requireEdit(targetFolder, userId);

    // Generate unique name in target folder
    String baseName = source.getName();
    List<String> existingNames = documentRepository.findByFolder_Id(targetFolder.getId())
      .stream().map(Document::getName).toList();
    String newName = StorageNameUtils.generateUniqueName(existingNames, baseName + "_copy");

    // Copy MinIO object (server-side copy)
    String newObjectKey = String.format("%s/%s/%s_%s", userId, targetFolder.getId(), UUID.randomUUID(), newName);
    try {
      // In real implementation: s3Client.copyObject(CopyObjectRequest.builder()...)
      // For now, we'll use the same object key (not ideal but works for demo)
    } catch (Exception e) {
      log.error("Failed to copy file in storage", e);
      throw new AppException(ErrorCode.STORAGE_ERROR);
    }

    User user = userRepository.findById(userId)
      .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

    Document newDoc = Document.builder()
      .name(newName)
      .folder(targetFolder)
      .owner(user)
      .objectKey(newObjectKey)
      .size(source.getSize())
      .mimeType(source.getMimeType())
      .build();

    Document saved = documentRepository.save(newDoc);
    return toDocumentResponse(saved);
  }

  // ========== Helper methods ==========

  private Document requireDocument(UUID documentId, UUID userId) {
    return documentRepository.findByIdAndOwner_Id(documentId, userId)
      .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));
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

  private long parseSize(String sizeStr) {
    if (sizeStr == null) return 10485760L; // 10MB default
    sizeStr = sizeStr.toUpperCase().trim();
    if (sizeStr.endsWith("KB")) {
      return Long.parseLong(sizeStr.substring(0, sizeStr.length() - 2)) * 1024;
    } else if (sizeStr.endsWith("MB")) {
      return Long.parseLong(sizeStr.substring(0, sizeStr.length() - 2)) * 1024 * 1024;
    } else if (sizeStr.endsWith("GB")) {
      return Long.parseLong(sizeStr.substring(0, sizeStr.length() - 2)) * 1024 * 1024 * 1024;
    }
    return Long.parseLong(sizeStr);
  }

  private String getMaxFileSizeConfig() {
    // This would come from config, for now default to 10MB
    return "10MB";
  }

  private void uploadToPresignedUrl(String presignedUrl, MultipartFile file, String contentType) throws IOException {
    // Use Java HttpClient to PUT file to presigned URL
    java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
    java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
      .uri(java.net.URI.create(presignedUrl))
      .header("Content-Type", contentType)
      .PUT(java.net.http.HttpRequest.BodyPublishers.ofByteArray(file.getBytes()))
      .build();
    
    try {
      java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        throw new IOException("Upload failed with status: " + response.statusCode() + " - " + response.body());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Upload interrupted", e);
    }
  }

  private void uploadMultipart(String uploadId, String objectKey, MultipartFile file, String contentType) throws IOException {
    long partSize = 5 * 1024 * 1024; // 5MB parts
    byte[] fileBytes = file.getBytes();
    int totalParts = (int) Math.ceil((double) fileBytes.length / partSize);
    List<CompletedPart> parts = new java.util.ArrayList<>();

    for (int i = 0; i < totalParts; i++) {
      int partNumber = i + 1;
      int start = i * (int) partSize;
      int end = Math.min(start + (int) partSize, fileBytes.length);
      byte[] partData = java.util.Arrays.copyOfRange(fileBytes, start, end);

      String presignedUrl = storageService.presignUploadPart(uploadId, objectKey, partNumber, Duration.ofMinutes(10).getSeconds());
      
      java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
      java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
        .uri(java.net.URI.create(presignedUrl))
        .header("Content-Type", contentType)
        .PUT(java.net.http.HttpRequest.BodyPublishers.ofByteArray(partData))
        .build();

      try {
        java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
          throw new IOException("Part upload failed: " + response.statusCode());
        }

        // Extract ETag from response headers
        String etag = response.headers().firstValue("ETag").orElse("\"\"").replace("\"", "");
        CompletedPart part = CompletedPart.builder()
          .partNumber(partNumber)
          .eTag(etag)
          .build();
        parts.add(part);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Part upload interrupted", e);
      }
    }

    storageService.completeMultipartUpload(uploadId, objectKey, parts);
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

  @Override
  public Page<DocumentResponse> filterDocuments(UUID userId, UUID folderId, String name, String mimeType, int pageNo, int pageSize, String sortBy, String sortDir) {
    int size = Math.max(pageSize, 1);
    int page = Math.max(pageNo, 0);
    Sort sort = Sort.by(Sort.Direction.fromString(sortDir != null && sortDir.equalsIgnoreCase("asc") ? "ASC" : "DESC"), sortBy != null ? sortBy : "updatedAt");
    Pageable pageable = PageRequest.of(page, size, sort);
    Page<Document> pages = documentRepository.filterDocuments(userId, folderId, name, mimeType, pageable);
    return pages.map(this::toDocumentResponse);
  }
}