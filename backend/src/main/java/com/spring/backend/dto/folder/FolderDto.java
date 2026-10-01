package com.spring.backend.dto.folder;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@EqualsAndHashCode
public class FolderDto {
    private UUID id;
    private String name;
    private UUID ownerId;
    private UUID parentFolderId;
    private Instant createdAt;
    private Instant updatedAt;
}