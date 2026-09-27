package com.spring.backend.dto.folder;

import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @AllArgsConstructor  @NoArgsConstructor @Builder 
public class FolderUpdateDto {
    @NotNull()
    private UUID id;

    @NotEmpty()
    @NotNull()
    private String name;

    private UUID parentId;
}
