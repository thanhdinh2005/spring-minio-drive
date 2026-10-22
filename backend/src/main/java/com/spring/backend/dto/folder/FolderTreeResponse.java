package com.spring.backend.dto.folder;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderTreeResponse {

  private List<FolderTreeNode> folders;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class FolderTreeNode {
    private String id;
    private String name;
    private boolean hasChildren;
    private List<FolderTreeNode> children;
  }
}