package com.spring.backend.dto.folder;

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

  private String parentFolderName;
}