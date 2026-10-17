package com.spring.backend.service;

import com.spring.backend.dto.folder.FolderCreateRequest;
import com.spring.backend.dto.folder.FolderDto;
import com.spring.backend.entity.Folder;
import com.spring.backend.entity.User;
import com.spring.backend.exception.AppException;
import com.spring.backend.exception.ErrorCode;
import com.spring.backend.repository.FolderRepository;
import com.spring.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepository folderRepository;
    private final UserRepository userRepository;

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

    @Transactional
    public FolderDto createFolder(FolderCreateRequest request, UUID userId) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Folder parent = null;
        if (request.getParentFolderId() != null) {
            parent = folderRepository.findById(request.getParentFolderId())
                    .orElseThrow(() -> new AppException(ErrorCode.FOLDER_NOT_FOUND));
            // TODO: sau này bổ sung PermissionRepository để check Permission EDIT trên parent
            if (!parent.getOwner().getId().equals(userId)) {
                throw new AppException(ErrorCode.FOLDER_ACCESS_DENIED);
            }
        }

        Folder folder = Folder.builder()
                .name(request.getName())
                .owner(owner)
                .parentFolder(parent)
                .build();

        folder = folderRepository.save(folder);

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
