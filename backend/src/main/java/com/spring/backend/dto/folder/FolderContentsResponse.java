package com.spring.backend.dto.folder;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderContentsResponse {

  private FolderInfo folder;
  // Folder và document trộn chung trong một trang, mỗi item cho biết itemType (FOLDER | DOCUMENT)
  private List<ContentItem> items;
  private int pageNo;
  private int pageSize;
  private long totalElements;
  private int totalPages;
  private boolean last;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class FolderInfo {
    private UUID id;
    private String name;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ContentItem {
    private String itemType;
    private UUID id;
    private String name;
    // Folder: có folder con hay không. Document: có file trong storage.
    private boolean hasChildren;
    // Chỉ có với DOCUMENT
    private Long size;
    private String mimeType;
    // Chỉ có khi xem qua public link: path tương đối để duyệt/tải, tính từ base public link
    // Folder con: "folders/{id}" -> GET {public-link}/{token}/folders/{id}
    // Document:  "documents/{id}" -> GET {public-link}/{token}/documents/{id}/download-url
    private String itemUrl;
    // Chỉ có với DOCUMENT qua public link: presigned URL (hết hạn sau 300s)
    private String downloadUrl;
    private String previewUrl;
  }
}
