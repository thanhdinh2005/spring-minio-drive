package com.spring.backend.dto.folder;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderSummaryResponse {

  private UUID id;
  private String name;
  private boolean hasChildren;
}