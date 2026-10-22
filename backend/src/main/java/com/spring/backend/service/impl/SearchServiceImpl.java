package com.spring.backend.service.impl;

import com.spring.backend.dto.search.SearchResponse;
import com.spring.backend.entity.Document;
import com.spring.backend.entity.Folder;
import com.spring.backend.repository.DocumentRepository;
import com.spring.backend.repository.FolderRepository;
import com.spring.backend.service.SearchService;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchServiceImpl implements SearchService {

  private final FolderRepository folderRepository;
  private final DocumentRepository documentRepository;

  @Override
  public SearchResponse search(UUID userId, String keyword, int pageNo, int pageSize) {
    Pageable pageable = PageRequest.of(pageNo, pageSize);

    List<Folder> folders = folderRepository.searchByOwnerIdAndKeyword(userId, keyword);
    List<Document> documents = documentRepository.searchByOwnerIdAndKeyword(userId, keyword);

    // Apply pagination manually since we're searching across two tables
    int start = pageNo * pageSize;
    int end = Math.min(start + pageSize, folders.size() + documents.size());

    List<Folder> pagedFolders = folders.stream().skip(start).limit(pageSize).collect(Collectors.toList());
    int folderTaken = pagedFolders.size();
    int docStart = Math.max(0, start - folders.size());
    int docEnd = Math.min(docStart + (pageSize - folderTaken), documents.size());
    List<Document> pagedDocuments = documents.subList(docStart, docEnd);

    return SearchResponse.builder()
      .folders(pagedFolders.stream().map(this::toFolderSearchItem).collect(Collectors.toList()))
      .documents(pagedDocuments.stream().map(this::toDocumentSearchItem).collect(Collectors.toList()))
      .build();
  }

  private SearchResponse.FolderSearchItem toFolderSearchItem(Folder folder) {
    return SearchResponse.FolderSearchItem.builder()
      .id(folder.getId())
      .name(folder.getName())
      .parentFolderName(folder.getParentFolder())
      .build();
  }

  private SearchResponse.DocumentSearchItem toDocumentSearchItem(Document document) {
    return SearchResponse.DocumentSearchItem.builder()
      .id(document.getId())
      .name(document.getName())
      .size(document.getSize())
      .mimeType(document.getMimeType())
      .folderId(document.getFolder().getId())
      .folderName(document.getFolder().getName())
      .build();
  }
}