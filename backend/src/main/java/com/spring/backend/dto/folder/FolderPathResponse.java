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
public class FolderPathResponse {

  private List<PathItem> path;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PathItem {
    private UUID id;
    private String name;
  }
}