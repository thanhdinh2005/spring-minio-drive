package com.spring.backend.dto.folder;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentSummaryResponse {

  private UUID id;
  private String name;
  private Long size;
  private String mimeType;
  private Instant createdAt;
  private Instant updatedAt;
}