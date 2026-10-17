package com.spring.backend.service;

import java.util.UUID;

import com.spring.backend.dto.folder.FolderContentsDto;
import com.spring.backend.dto.folder.FolderCreateDto;
import com.spring.backend.dto.folder.FolderDto;
import com.spring.backend.dto.folder.FolderUpdateDto;

public interface FolderService {
    /** 2.1 POST /api/folders */
    FolderDto create(FolderCreateDto dto, UUID ownerId);

    /** 2.2 GET /api/folders/{id}/contents */
    FolderContentsDto getContents(UUID folderId, UUID userId);

    /** 2.6 PATCH /api/folders/{id} — đổi tên / di chuyển */
    FolderDto patch(UUID folderId, FolderUpdateDto dto, UUID userId);

    /** 2.7 DELETE /api/folders/{id} — xóa cứng cascade */
    void deleteHard(UUID folderId, UUID userId);
}
