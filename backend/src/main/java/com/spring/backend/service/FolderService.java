package com.spring.backend.service;

import java.util.List;
import java.util.UUID;

import com.spring.backend.common.PageResponse;
import com.spring.backend.dto.folder.FolderCreateDto;
import com.spring.backend.dto.folder.FolderDto;
import com.spring.backend.dto.folder.FolderUpdateDto;

public interface FolderService {
    public FolderDto create(FolderCreateDto dto, UUID ownerId);

    public FolderDto update(FolderUpdateDto dto, UUID ownerId);

    public void deleteSoft(List<UUID> ids, UUID ownerId);

    public FolderDto findById(UUID id);

    public PageResponse<FolderDto> findAllByOwnerIdInRoot(int pageNo, int pageSize, String sortBy, String sortDir, UUID ownerId);

    public PageResponse<FolderDto> findAllByOwnerIdAndParentFolderId(int pageNo, int pageSize, String sortBy, String sortDir, UUID ownerId, UUID parentFolderId);
}
