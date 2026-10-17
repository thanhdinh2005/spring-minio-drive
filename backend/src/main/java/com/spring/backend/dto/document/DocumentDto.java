package com.spring.backend.dto.document;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DocumentDto {
    private UUID id;
    private String name;
    private Long size;
    private String mimeType;
    private String objectKey;
    private UUID folderId;
    private UUID ownerId;
    private Instant createdAt;
    private Instant updatedAt;
}
