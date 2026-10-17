package com.spring.backend.dto.folder;

import com.spring.backend.common.PageResponse;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FolderContentsResponse {
  private FolderSummaryResponse folder;
  private PageResponse<FolderSummaryResponse> subFolders;
  private PageResponse<DocumentSummaryResponse> documents;
}
