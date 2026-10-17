package com.spring.backend.service;

import java.util.UUID;

import com.spring.backend.dto.folder.CreateFolderRequest;
import com.spring.backend.dto.folder.FolderContentsResponse;
import com.spring.backend.dto.folder.FolderResponse;

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
}
