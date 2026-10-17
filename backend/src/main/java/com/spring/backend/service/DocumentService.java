package com.spring.backend.service;

import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

import com.spring.backend.dto.document.DocumentDto;
import com.spring.backend.dto.document.DocumentUpdateDto;
import com.spring.backend.dto.document.PresignedUrlDto;

public interface DocumentService {
    /** 2.3 POST /api/folders/{id}/documents (upload multipart) */
    DocumentDto upload(UUID folderId, MultipartFile file, UUID userId);

    /** 2.4 GET /api/documents/{id}/download-url */
    PresignedUrlDto downloadUrl(UUID documentId, UUID userId);

    /** 2.5 GET /api/documents/{id}/preview-url */
    PresignedUrlDto previewUrl(UUID documentId, UUID userId);

    /** 2.6 PATCH /api/documents/{id} — đổi tên / di chuyển */
    DocumentDto patch(UUID documentId, DocumentUpdateDto dto, UUID userId);

    /** 2.7 DELETE /api/documents/{id} — xóa cứng */
    void delete(UUID documentId, UUID userId);
}
