package com.spring.backend.dto.folder;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopyFolderRequest {

  @NotNull(message = "Target folder ID is required")
  private UUID targetFolderId;
}