package com.spring.backend.dto.folder;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FolderContentsResponse {
  private FolderSummaryResponse folder;

  // Chỉ chứa các item thuộc trang hiện tại (folder được ưu tiên đứng trước document)
  private List<FolderSummaryResponse> subFolders;
  private List<DocumentSummaryResponse> documents;

  // Metadata phân trang chung cho toàn bộ folder con + document con
  private int pageNo;
  private int pageSize;
  private long totalElements;
  private int totalPages;
  private boolean last;
}
