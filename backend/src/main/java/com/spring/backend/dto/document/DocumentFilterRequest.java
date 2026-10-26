package com.spring.backend.dto.document;

import java.util.UUID;
import lombok.Data;

@Data
public class DocumentFilterRequest {
  private UUID folderId;
  private String name;
  private String mimeType;
  private int pageNo = 0;
  private int pageSize = 10;
  private String sortBy = "updated_at";
  private String sortDir = "desc";
}
