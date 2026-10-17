package com.spring.backend.mapper;

import org.springframework.stereotype.Component;

import com.spring.backend.dto.document.DocumentDto;
import com.spring.backend.entity.Document;

@Component
public class DocumentMapper {

    public DocumentDto toDto(Document doc) {
        if (doc == null) {
            return null;
        }
        return DocumentDto.builder()
                .id(doc.getId())
                .name(doc.getName())
                .size(doc.getSize())
                .mimeType(doc.getMimeType())
                .objectKey(doc.getObjectKey())
                .folderId(doc.getFolder() != null ? doc.getFolder().getId() : null)
                .ownerId(doc.getOwner() != null ? doc.getOwner().getId() : null)
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }
}
