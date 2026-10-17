package com.spring.backend.dto.folder;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @AllArgsConstructor @NoArgsConstructor @Builder
public class FolderCreateDto {
    @NotBlank(message = "Folder name must not be blank")
    private String name;

    private UUID parentFolderId;
}
