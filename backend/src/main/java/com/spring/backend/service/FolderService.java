package com.spring.backend.service;

import java.util.UUID;

import com.spring.backend.dto.folder.CreateFolderRequest;
import com.spring.backend.dto.folder.FolderContentsResponse;
import com.spring.backend.dto.folder.FolderResponse;
import com.spring.backend.dto.folder.UpdateFolderRequest;

public interface FolderService {

  FolderResponse create(CreateFolderRequest request, UUID ownerId);

  FolderContentsResponse getContents(
    UUID folderId,
    UUID userId,
    int pageNo,
    int pageSize,
    String sortBy,
    String sortDir
  );

  // Xóa cứng folder kèm toàn bộ subFolder + document con (transaction)
  void deleteHard(UUID folderId, UUID userId);

  // Đổi tên và/hoặc di chuyển folder (transaction)
  FolderResponse update(UUID folderId, UpdateFolderRequest request, UUID userId);
}
