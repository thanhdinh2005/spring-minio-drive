package com.spring.backend.dto.folder;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class FolderDto {
    private UUID id;
    private String name;
    private UUID ownerId;
    private UUID parentFolderId;
    private Instant createdAt;
    private Instant updatedAt;
}