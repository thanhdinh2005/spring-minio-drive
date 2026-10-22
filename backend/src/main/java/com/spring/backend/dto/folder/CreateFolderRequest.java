package com.spring.backend.dto.folder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFolderRequest {

  @NotBlank(message = "Folder name is required")
  @Size(max = 255, message = "Folder name must not exceed 255 characters")
  private String name;

  private String parentFolderName;
}