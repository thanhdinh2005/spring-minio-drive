package com.spring.backend.dto.search;

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
public class SearchResponse {

  private List<FolderSearchItem> folders;
  private List<DocumentSearchItem> documents;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class FolderSearchItem {
    private UUID id;
    private String name;
    private String parentFolderName;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class DocumentSearchItem {
    private UUID id;
    private String name;
    private Long size;
    private String mimeType;
    private UUID folderId;
    private String folderName;
  }
}