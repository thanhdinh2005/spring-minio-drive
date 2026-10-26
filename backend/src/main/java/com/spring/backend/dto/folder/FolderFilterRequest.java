package com.spring.backend.dto.folder;

import lombok.Data;

@Data
public class FolderFilterRequest {
  private String name;
  private String parentFolderName;
  private int pageNo = 0;
  private int pageSize = 10;
  private String sortBy = "updatedAt";
  private String sortDir = "desc";
}
