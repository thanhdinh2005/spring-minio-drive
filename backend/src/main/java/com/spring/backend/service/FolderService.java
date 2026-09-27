package com.spring.backend.service;

import java.util.List;
import java.util.UUID;

import com.spring.backend.common.PageResponse;
import com.spring.backend.dto.folder.FolderCreateDto;
import com.spring.backend.dto.folder.FolderUpdateDto;
import com.spring.backend.entity.Folder;

public interface FolderService {
    public Folder create(FolderCreateDto dto, UUID ownerId);

    public Folder update(FolderUpdateDto dto, UUID ownerId);

    public void deleteSoft(List<UUID> ids, UUID ownerId);

    public Folder findById(UUID id);

    public PageResponse<Folder> findAllByOwnerIdInRoot(int pageNo, int pageSize, String sortBy, String sortDir, UUID ownerId);

    public PageResponse<Folder> findAllByOwnerIdAndParentFolderId(int pageNo, int pageSize, String sortBy, String sortDir, UUID ownerId, UUID parentFolderId);
}
