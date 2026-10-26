package com.spring.backend.dto.folder;

import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkDeleteRequest {

  @NotEmpty(message = "At least one ID is required")
  @Size(max = 100, message = "Cannot delete more than 100 items at once")
  private List<UUID> ids;
}