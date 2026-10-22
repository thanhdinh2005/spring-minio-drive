package com.spring.backend.dto.folder;

import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkMoveRequest {

  @NotEmpty(message = "At least one item ID is required")
  @Size(max = 100, message = "Cannot move more than 100 items at once")
  private List<UUID> ids;

  @NotNull(message = "Target folder ID is required")
  private UUID targetFolderId;
}