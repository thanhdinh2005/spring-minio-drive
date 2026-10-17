package com.spring.backend.dto.folder;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DocumentSummaryResponse {
  private UUID id;
  private String name;
  private Long size;
  private String mimeType;
}
