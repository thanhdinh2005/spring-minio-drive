package com.spring.backend.dto.folder;

import java.util.UUID;

import lombok.Data;

@Data
public class FolderFilterRequest {
  private String name;
  private UUID parentId;
  private int pageNo = 0;
  private int pageSize = 10;
  private String sortBy = "updatedAt";
  private String sortDir = "desc";
}
