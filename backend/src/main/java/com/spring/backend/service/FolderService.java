package com.spring.backend.service;

import java.util.UUID;

import com.spring.backend.dto.folder.CreateFolderRequest;
import com.spring.backend.dto.folder.FolderResponse;

public interface FolderService {

  FolderResponse create(CreateFolderRequest request, UUID ownerId);
}
