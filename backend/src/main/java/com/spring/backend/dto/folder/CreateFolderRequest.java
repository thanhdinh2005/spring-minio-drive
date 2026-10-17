package com.spring.backend.dto.folder;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFolderRequest {

  @NotBlank(message = "Folder name must not be blank")
  private String name;

  // null = tạo folder ở thư mục gốc
  private UUID parentFolderId;
}
