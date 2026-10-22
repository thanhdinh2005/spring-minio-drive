package com.spring.backend.service;

import com.spring.backend.dto.folder.BulkDeleteRequest;
import com.spring.backend.dto.folder.BulkMoveRequest;
import com.spring.backend.dto.folder.CopyFolderRequest;
import com.spring.backend.dto.folder.CreateFolderRequest;
import com.spring.backend.dto.folder.FolderContentsResponse;
import com.spring.backend.dto.folder.FolderPathResponse;
import com.spring.backend.dto.folder.FolderResponse;
import com.spring.backend.dto.folder.FolderTreeResponse;
import com.spring.backend.dto.folder.UpdateFolderRequest;

import java.util.List;
import java.util.UUID;

public interface FolderService {

  FolderResponse create(CreateFolderRequest request, UUID ownerId);

  FolderResponse getById(UUID folderId, UUID userId);

  List<FolderResponse> getRootFolders(UUID userId);

  FolderContentsResponse getContents(UUID folderId, UUID userId, int pageNo, int pageSize, String sortBy, String sortDir);

  FolderPathResponse getPath(UUID folderId, UUID userId);

  FolderTreeResponse getTree(UUID userId, String parentFolderName);

  FolderResponse update(UUID folderId, UpdateFolderRequest request, UUID userId);

  void deleteHard(UUID folderId, UUID userId);

  void bulkDelete(BulkDeleteRequest request, UUID userId);

  FolderResponse copy(UUID folderId, CopyFolderRequest request, UUID userId);

  void bulkMove(BulkMoveRequest request, UUID userId);
}
