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
  private List<FolderSummaryResponse> subFolders;
  private List<DocumentSummaryResponse> documents;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class FolderInfo {
    private UUID id;
    private String name;
  }
}