package com.spring.backend.service;

import java.util.UUID;

import com.spring.backend.dto.document.PresignedUrlResponse;

/**
 * Nghiệp vụ trên tài liệu (document): tải xuống và xem trước.
 */
public interface DocumentService {

  // Lấy presigned URL để tải tài liệu (mọi mimeType)
  PresignedUrlResponse getDownloadUrl(UUID documentId, UUID userId);

  // Lấy presigned URL để xem trước (chỉ với mimeType hỗ trợ: application/pdf, image/*)
  PresignedUrlResponse getPreviewUrl(UUID documentId, UUID userId);
}
