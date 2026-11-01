package com.spring.backend.dto.folder;

import java.util.UUID;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateFolderRequest {

  @Size(max = 255, message = "Folder name must not exceed 255 characters")
  private String name;

  private UUID parentId;

  // Boolean (nullable) để phân biệt "không gửi" (null -> giữ nguyên cha) với
  // "gửi moveToRoot=true" (về root). Primitive boolean không làm được vì thiếu field == false.
  private Boolean moveToRoot;
}
