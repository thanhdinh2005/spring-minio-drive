package com.spring.backend.dto.folder;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body cho PATCH /api/folders/{id}.
 * Cả 2 field đều optional: field nào null/absent thì giữ nguyên,
 * riêng parentFolderId phải đi kèm {@code moveToRoot} khi muốn chuyển về gốc
 * (vì JSON null không phân biệt được absent vs explicit-null).
 */
@Data @AllArgsConstructor @NoArgsConstructor @Builder
public class FolderUpdateDto {
    private String name;

    private UUID parentFolderId;

    /** = true → chuyển folder về root (bỏ qua parentFolderId). */
    private Boolean moveToRoot;
}
