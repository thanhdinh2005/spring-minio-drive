package com.spring.backend.service.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spring.backend.common.utils.StorageNameUtils;
import com.spring.backend.dto.folder.CreateFolderRequest;
import com.spring.backend.dto.folder.DocumentSummaryResponse;
import com.spring.backend.dto.folder.FolderContentsResponse;
import com.spring.backend.dto.folder.FolderResponse;
import com.spring.backend.dto.folder.FolderSummaryResponse;
import com.spring.backend.entity.Document;
import com.spring.backend.entity.Folder;
import com.spring.backend.entity.User;
import com.spring.backend.exception.AppException;
import com.spring.backend.exception.ErrorCode;
import com.spring.backend.repository.DocumentRepository;
import com.spring.backend.repository.FolderRepository;
import com.spring.backend.repository.UserRepository;
import com.spring.backend.service.FolderAccessService;
import com.spring.backend.service.FolderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class FolderServiceImpl implements FolderService {

  private final FolderRepository folderRepository;
  private final DocumentRepository documentRepository;
  private final UserRepository userRepository;
  private final FolderAccessService folderAccessService;

  @Override
  @Transactional
  public FolderResponse create(CreateFolderRequest request, UUID ownerId) {
    // 1. Validate tên folder
    if (!StorageNameUtils.isValidStorageName(request.getName())) {
      throw new AppException(ErrorCode.INVALID_FOLDER_NAME,
        Map.of("name", String.valueOf(request.getName())));
    }

    // 2. Nếu có parent: kiểm tra tồn tại (404) + quyền EDIT trên parent (403)
    Folder parent = null;
    if (request.getParentFolderId() != null) {
      parent = folderAccessService.requireFolder(request.getParentFolderId());
      folderAccessService.requireEdit(parent, ownerId);
    }

    User owner = userRepository.findById(ownerId)
      .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

    // 3. Sinh tên duy nhất trong cùng cấp (abc -> abc (1))
    String parentFolderId = parent == null ? null : parent.getId().toString();
    String uniqueName = StorageNameUtils.generateUniqueName(
      findSiblingNames(parent, ownerId), request.getName());

    // 4. Lưu folder mới
    Folder folder = Folder.builder()
      .name(uniqueName)
      .owner(owner)
      .parentFolder(parentFolderId)
      .build();

    Folder saved = folderRepository.save(folder);
    log.debug("Created folder {} (owner={}, parent={})", saved.getId(), ownerId, parentFolderId);

    return toFolderResponse(saved);
  }

  @Override
  public FolderContentsResponse getContents(
    UUID folderId,
    UUID userId,
    int pageNo,
    int pageSize,
    String sortBy,
    String sortDir
  ) {
    // 1. Lấy folder (404) + check quyền VIEW (403)
    Folder folder = folderAccessService.requireFolder(folderId);
    folderAccessService.requireView(folder, userId);

    String parentFolderId = folder.getId().toString();
    long folderTotal = folderRepository.countByParentFolder(parentFolderId);
    long documentTotal = documentRepository.countByFolder_Id(folder.getId());
    long total = folderTotal + documentTotal;

    int size = Math.max(pageSize, 1);
    int page = Math.max(pageNo, 0);
    long start = (long) page * size;
    long end = Math.min(start + size, total);

    Sort sort = buildSort(sortBy, sortDir);

    // 2. Dãy gộp = [tất cả folder con] rồi [tất cả document con].
    //    Cắt đúng cửa sổ [start, end) của trang hiện tại: phần giao với folder lấy trước,
    //    phần còn lại lấy từ document -> folder luôn được ưu tiên hiển thị trước.
    List<Folder> subFolders = List.of();
    if (start < folderTotal) {
      int folderLimit = (int) (Math.min(end, folderTotal) - start);
      if (folderLimit > 0) {
        subFolders = folderRepository
          .findByParentFolder(parentFolderId, new OffsetPageRequest(start, folderLimit, sort))
          .getContent();
      }
    }

    List<Document> documents = List.of();
    long documentStart = Math.max(start, folderTotal);
    if (end > documentStart) {
      int documentLimit = (int) (end - documentStart);
      documents = documentRepository
        .findByFolder_Id(folder.getId(),
          new OffsetPageRequest(documentStart - folderTotal, documentLimit, sort))
        .getContent();
    }

    int totalPages = (int) Math.ceil((double) total / size);

    return FolderContentsResponse.builder()
      .folder(toFolderSummary(folder))
      .subFolders(subFolders.stream().map(this::toFolderSummary).toList())
      .documents(documents.stream().map(this::toDocumentSummary).toList())
      .pageNo(page)
      .pageSize(size)
      .totalElements(total)
      .totalPages(totalPages)
      .last(end >= total)
      .build();
  }

  private Sort buildSort(String sortBy, String sortDir) {
    return sortDir.equalsIgnoreCase(Sort.Direction.DESC.name())
      ? Sort.by(sortBy).descending()
      : Sort.by(sortBy).ascending();
  }

  /**
   * Danh sách tên folder cùng cấp dùng để sinh tên duy nhất.
   * - Folder gốc: chỉ xét folder gốc của chính user.
   * - Folder con: xét tất cả folder con của parent (kể cả folder do user khác tạo khi được share).
   */
  private List<String> findSiblingNames(Folder parent, UUID ownerId) {
    List<Folder> siblings = parent == null
      ? folderRepository.findByOwner_IdAndParentFolderIsNull(ownerId)
      : folderRepository.findByParentFolder(parent.getId().toString());

    return siblings.stream().map(Folder::getName).toList();
  }

  private FolderResponse toFolderResponse(Folder folder) {
    return FolderResponse.builder()
      .id(folder.getId())
      .name(folder.getName())
      .parentFolderId(folder.getParentFolder() == null ? null : UUID.fromString(folder.getParentFolder()))
      .ownerId(folder.getOwner() == null ? null : folder.getOwner().getId())
      .updatedAt(folder.getUpdatedAt())
      .createdAt(folder.getCreatedAt())
      .build();
  }

  private FolderSummaryResponse toFolderSummary(Folder folder) {
    return FolderSummaryResponse.builder()
      .id(folder.getId())
      .name(folder.getName())
      .build();
  }

  private DocumentSummaryResponse toDocumentSummary(Document document) {
    return DocumentSummaryResponse.builder()
      .id(document.getId())
      .name(document.getName())
      .size(document.getSize())
      .mimeType(document.getMimeType())
      .build();
  }

  /**
   * Pageable hỗ trợ offset tuỳ ý (PageRequest chỉ hỗ trợ offset = page * size),
   * cần cho việc phân trang trên dãy gộp folder + document.
   */
  private record OffsetPageRequest(long offset, int limit, Sort sort) implements Pageable {

    @Override
    public int getPageNumber() {
      return (int) (offset / Math.max(limit, 1));
    }

    @Override
    public int getPageSize() {
      return limit;
    }

    @Override
    public long getOffset() {
      return offset;
    }

    @Override
    public Sort getSort() {
      return sort;
    }

    @Override
    public Pageable next() {
      return new OffsetPageRequest(offset + limit, limit, sort);
    }

    @Override
    public Pageable previousOrFirst() {
      return hasPrevious() ? new OffsetPageRequest(offset - limit, limit, sort) : first();
    }

    @Override
    public Pageable first() {
      return new OffsetPageRequest(0, limit, sort);
    }

    @Override
    public Pageable withPage(int pageNumber) {
      return new OffsetPageRequest((long) pageNumber * limit, limit, sort);
    }

    @Override
    public boolean hasPrevious() {
      return offset > 0;
    }
  }
}
