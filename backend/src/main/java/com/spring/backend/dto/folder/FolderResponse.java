package com.spring.backend.dto.folder;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FolderResponse {
  private UUID id;
  private String name;
  private UUID parentFolderId;
  private UUID ownerId;
  private Instant updatedAt;
  private Instant createdAt;
}
