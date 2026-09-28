package com.spring.backend.service;

import com.spring.backend.dto.folder.FolderDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spring.backend.entity.Folder;
import com.spring.backend.exception.AppException;
import com.spring.backend.exception.ErrorCode;
import com.spring.backend.repository.FolderRepository;
import lombok.RequiredArgsConstructor;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepository folderRepository;

    @Transactional(readOnly = true)
    public FolderDto getFolderById(UUID id) {
        Optional<Folder> folderResult = folderRepository.findById(id);
        if (folderResult.isEmpty()) {
            throw new AppException(ErrorCode.FOLDER_NOT_FOUND);
        }

        Folder folder = folderResult.get();
        return FolderDto.builder()
                .id(folder.getId())
                .name(folder.getName())
                .ownerId(folder.getOwner().getId())
                .parentFolderId(folder.getParentFolder() == null
                        ? null
                        : folder.getParentFolder().getId())
                .createdAt(folder.getCreatedAt())
                .updatedAt(folder.getUpdatedAt())
                .build();
    }
}
