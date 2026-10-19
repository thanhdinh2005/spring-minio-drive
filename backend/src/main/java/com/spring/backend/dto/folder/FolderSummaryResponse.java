package com.spring.backend.dto.folder;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FolderSummaryResponse {
  private UUID id;
  private String name;
}
