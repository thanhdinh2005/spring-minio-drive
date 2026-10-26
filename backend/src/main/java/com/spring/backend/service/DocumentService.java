package com.spring.backend.service;

import com.spring.backend.dto.document.CopyDocumentRequest;
import com.spring.backend.dto.document.DocumentResponse;
import com.spring.backend.dto.document.PresignedUrlResponse;
import com.spring.backend.dto.document.UploadDocumentRequest;
import com.spring.backend.dto.folder.BulkDeleteRequest;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentService {

  // Lấy presigned URL để tải tài liệu (mọi mimeType)
  PresignedUrlResponse getDownloadUrl(UUID documentId, UUID userId);

  // Lấy presigned URL để xem trước (chỉ với mimeType hỗ trợ: application/pdf, image/*)
  PresignedUrlResponse getPreviewUrl(UUID documentId, UUID userId);

  // Upload file vào folder (multipart)
  DocumentResponse upload(UUID folderId, UploadDocumentRequest request, UUID userId);

  DocumentResponse getById(UUID documentId, UUID userId);

  DocumentResponse update(UUID documentId, com.spring.backend.dto.document.UpdateDocumentRequest request, UUID userId);

  void delete(UUID documentId, UUID userId);

  void bulkDelete(BulkDeleteRequest request, UUID userId);

  DocumentResponse copy(UUID documentId, CopyDocumentRequest request, UUID userId);

  Page<DocumentResponse> filterDocuments(UUID userId, UUID folderId, String name, String mimeType, int pageNo, int pageSize, String sortBy, String sortDir);
}
