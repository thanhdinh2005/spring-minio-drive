package com.spring.backend.dto.folder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
public class FolderCreateRequest {

  @NotBlank(message = "Tên thư mục không được để trống")
  private String name;

  private UUID parentFolderId;
}
