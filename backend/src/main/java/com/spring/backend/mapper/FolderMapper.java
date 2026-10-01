package com.spring.backend.mapper;

import org.springframework.stereotype.Component;

import com.spring.backend.dto.folder.FolderDto;
import com.spring.backend.entity.Folder;

@Component
public class FolderMapper {

    public FolderDto toDto(Folder folder) {
        if (folder == null) {
            return null;
        }
        return FolderDto.builder()
                .id(folder.getId())
                .name(folder.getName())
                // Gọi getId() trên lazy proxy không trigger query, an toàn khi serialize
                .ownerId(folder.getOwner() != null ? folder.getOwner().getId() : null)
                .parentFolderId(folder.getParentFolder() != null ? folder.getParentFolder().getId() : null)
                .createdAt(folder.getCreatedAt())
                .updatedAt(folder.getUpdatedAt())
                .build();
    }
}
