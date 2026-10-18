package com.spring.backend.service.impl;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spring.backend.dto.document.PresignedUrlResponse;
import com.spring.backend.entity.Document;
import com.spring.backend.exception.AppException;
import com.spring.backend.exception.ErrorCode;
import com.spring.backend.repository.DocumentRepository;
import com.spring.backend.service.DocumentService;
import com.spring.backend.service.FolderAccessService;
import com.spring.backend.service.StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class DocumentServiceImpl implements DocumentService {

  // Thời hạn presigned URL: 300 giây theo đặc tả
  private static final long PRESIGN_EXPIRES_SECONDS = 300L;

  private final DocumentRepository documentRepository;
  private final FolderAccessService folderAccessService;
  private final StorageService storageService;

  @Override
  public PresignedUrlResponse getDownloadUrl(UUID documentId, UUID userId) {
    // 1. Lấy document (404) + check quyền VIEW/EDIT trên folder chứa nó (403, kế thừa)
    Document document = requireDocument(documentId);
    folderAccessService.requireView(document.getFolder(), userId);

    // 2. Tạo presigned URL tải xuống, buộc trình duyệt tải file với tên gốc
    String contentDisposition = buildAttachmentDisposition(document.getName());
    String url = storageService.presignGet(
      document.getObjectKey(), PRESIGN_EXPIRES_SECONDS, contentDisposition);

    return toResponse(url);
  }

  @Override
  public PresignedUrlResponse getPreviewUrl(UUID documentId, UUID userId) {
    // 1. Lấy document (404) + check quyền VIEW/EDIT trên folder chứa nó (403, kế thừa)
    Document document = requireDocument(documentId);
    folderAccessService.requireView(document.getFolder(), userId);

    // 2. Chỉ cho preview với mimeType hỗ trợ (pdf, image/*) -> ngược lại 400
    if (!isPreviewable(document.getMimeType())) {
      throw new AppException(ErrorCode.VALIDATION_ERROR,
        Map.of("mimeType", String.valueOf(document.getMimeType())));
    }

    // 3. Tạo presigned URL xem trước trực tiếp trên trình duyệt (inline)
    String url = storageService.presignGet(
      document.getObjectKey(), PRESIGN_EXPIRES_SECONDS, "inline");

    return toResponse(url);
  }

  private Document requireDocument(UUID documentId) {
    return documentRepository.findById(documentId)
      .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));
  }

  /**
   * mimeType được xem trước trực tiếp: application/pdf và mọi image/*.
   */
  private boolean isPreviewable(String mimeType) {
    if (mimeType == null) {
      return false;
    }
    String normalized = mimeType.toLowerCase(Locale.ROOT);
    return normalized.equals("application/pdf") || normalized.startsWith("image/");
  }

  /**
   * Header Content-Disposition kiểu attachment kèm tên file gốc.
   * Loại bỏ dấu " để tránh phá vỡ cú pháp header.
   */
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
}
