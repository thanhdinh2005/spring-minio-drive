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
public class FolderResponse {

  private UUID id;
  private String name;
  private String parentFolderName;
  private UUID ownerId;
  private Instant createdAt;
  private Instant updatedAt;
}