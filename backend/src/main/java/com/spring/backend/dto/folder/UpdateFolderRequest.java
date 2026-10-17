package com.spring.backend.dto.folder;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request cho PATCH /api/folders/{id} — đổi tên và/hoặc di chuyển.
 * - name: tên mới (null = giữ nguyên tên).
 * - parentFolderId: folder cha đích (null = không di chuyển theo field này).
 * - moveToRoot: true = chuyển về thư mục gốc (parentFolderId = null).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateFolderRequest {

  private String name;

  private UUID parentFolderId;

  private Boolean moveToRoot;
}
