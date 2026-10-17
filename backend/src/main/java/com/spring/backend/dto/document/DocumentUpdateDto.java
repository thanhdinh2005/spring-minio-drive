package com.spring.backend.dto.document;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body cho PATCH /api/documents/{id}: đổi tên và/hoặc di chuyển folder.
 * Field null/absent → giữ nguyên.
 */
@Data @AllArgsConstructor @NoArgsConstructor @Builder
public class DocumentUpdateDto {
    private String name;
    private UUID folderId;
}
