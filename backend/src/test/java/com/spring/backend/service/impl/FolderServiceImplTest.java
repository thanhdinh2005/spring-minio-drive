package com.spring.backend.service.impl;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.spring.backend.dto.folder.FolderContentsResponse;
import com.spring.backend.entity.Document;
import com.spring.backend.entity.Folder;
import com.spring.backend.exception.AppException;
import com.spring.backend.exception.ErrorCode;
import com.spring.backend.repository.DocumentRepository;
import com.spring.backend.repository.FolderRepository;
import com.spring.backend.repository.UserRepository;
import com.spring.backend.service.FolderAccessService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

/**
 * Unit test cho logic phân trang gộp folder + document của FolderServiceImpl.getContents.
 * Không cần DB/Docker — repository và FolderAccessService được mock.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FolderServiceImpl.getContents")
class FolderServiceImplTest {

  @InjectMocks
  private FolderServiceImpl folderService;

  @Mock
  private FolderRepository folderRepository;
  @Mock
  private DocumentRepository documentRepository;
  @Mock
  private UserRepository userRepository;
  @Mock
  private FolderAccessService folderAccessService;

  private UUID ownerId;
  private UUID folderId;
  private Folder folder;

  // 15 folder con và 20 document con như ví dụ yêu cầu
  private List<Folder> folders;
  private List<Document> documents;

  @BeforeEach
  void setUp() {
    ownerId = UUID.randomUUID();
    folderId = UUID.randomUUID();
    folder = Folder.builder().id(folderId).name("Docs").build();

    folders = IntStream.range(0, 15)
      .mapToObj(i -> buildFolder("F" + i))
      .toList();

    documents = IntStream.range(0, 20)
      .mapToObj(i -> buildDocument("D" + i + ".pdf"))
      .toList();
  }

  @Test
  @DisplayName("trang 0 (size 10): 10 folder đầu, chưa có document")
  void shouldReturnFoldersOnly_whenFirstPage() {
    given(folderAccessService.requireFolder(folderId)).willReturn(folder);
    stubCounts(15, 20);
    stubFolderFinder();

    FolderContentsResponse res = folderService.getContents(folderId, ownerId, 0, 10, "updatedAt", "desc");

    assertThat(res.getFolder().getName()).isEqualTo("Docs");
    assertThat(res.getSubFolders()).hasSize(10);
    assertThat(res.getSubFolders().get(0).getName()).isEqualTo("F0");
    assertThat(res.getDocuments()).isEmpty();
    assertThat(res.getTotalElements()).isEqualTo(35);
    assertThat(res.getTotalPages()).isEqualTo(4);
    assertThat(res.getPageNo()).isZero();
    assertThat(res.isLast()).isFalse();
  }

  @Test
  @DisplayName("trang 1 (size 10): 5 folder cuối + 5 document đầu")
  void shouldMixFoldersAndDocuments_whenWindowCrossesBoundary() {
    given(folderAccessService.requireFolder(folderId)).willReturn(folder);
    stubCounts(15, 20);
    stubFolderFinder();
    stubDocumentFinder();

    FolderContentsResponse res = folderService.getContents(folderId, ownerId, 1, 10, "updatedAt", "desc");

    assertThat(res.getSubFolders()).hasSize(5);
    assertThat(res.getSubFolders().get(0).getName()).isEqualTo("F10");
    assertThat(res.getDocuments()).hasSize(5);
    assertThat(res.getDocuments().get(0).getName()).isEqualTo("D0.pdf");
    assertThat(res.getTotalElements()).isEqualTo(35);
    assertThat(res.isLast()).isFalse();
  }

  @Test
  @DisplayName("trang 2 (size 10): chỉ document")
  void shouldReturnDocumentsOnly_whenPageBeyondFolders() {
    given(folderAccessService.requireFolder(folderId)).willReturn(folder);
    stubCounts(15, 20);
    stubDocumentFinder();

    FolderContentsResponse res = folderService.getContents(folderId, ownerId, 2, 10, "updatedAt", "desc");

    assertThat(res.getSubFolders()).isEmpty();
    assertThat(res.getDocuments()).hasSize(10);
    assertThat(res.getDocuments().get(0).getName()).isEqualTo("D5.pdf");
    assertThat(res.isLast()).isFalse();
  }

  @Test
  @DisplayName("trang 3 (size 10): 5 document cuối và là trang cuối")
  void shouldMarkLastPage_whenLastWindow() {
    given(folderAccessService.requireFolder(folderId)).willReturn(folder);
    stubCounts(15, 20);
    stubDocumentFinder();

    FolderContentsResponse res = folderService.getContents(folderId, ownerId, 3, 10, "updatedAt", "desc");

    assertThat(res.getSubFolders()).isEmpty();
    assertThat(res.getDocuments()).hasSize(5);
    assertThat(res.getDocuments().get(0).getName()).isEqualTo("D15.pdf");
    assertThat(res.isLast()).isTrue();
  }

  @Test
  @DisplayName("folder rỗng: không có item, totalPages = 0")
  void shouldReturnEmpty_whenNoContent() {
    given(folderAccessService.requireFolder(folderId)).willReturn(folder);
    stubCounts(0, 0);

    FolderContentsResponse res = folderService.getContents(folderId, ownerId, 0, 10, "updatedAt", "desc");

    assertThat(res.getSubFolders()).isEmpty();
    assertThat(res.getDocuments()).isEmpty();
    assertThat(res.getTotalElements()).isZero();
    assertThat(res.getTotalPages()).isZero();
    assertThat(res.isLast()).isTrue();
  }

  @Test
  @DisplayName("folder không tồn tại → ném FOLDER_NOT_FOUND")
  void shouldThrow404_whenFolderNotFound() {
    given(folderAccessService.requireFolder(folderId))
      .willThrow(new AppException(ErrorCode.FOLDER_NOT_FOUND));

    assertThatThrownBy(() -> folderService.getContents(folderId, ownerId, 0, 10, "updatedAt", "desc"))
      .isInstanceOf(AppException.class)
      .extracting(e -> ((AppException) e).getErrorCode())
      .isEqualTo(ErrorCode.FOLDER_NOT_FOUND);
  }

  @Test
  @DisplayName("không có quyền VIEW → ném FOLDER_ACCESS_DENIED")
  void shouldThrow403_whenAccessDenied() {
    given(folderAccessService.requireFolder(folderId)).willReturn(folder);
    willThrow(new AppException(ErrorCode.FOLDER_ACCESS_DENIED))
      .given(folderAccessService).requireView(folder, ownerId);

    assertThatThrownBy(() -> folderService.getContents(folderId, ownerId, 0, 10, "updatedAt", "desc"))
      .isInstanceOf(AppException.class)
      .extracting(e -> ((AppException) e).getErrorCode())
      .isEqualTo(ErrorCode.FOLDER_ACCESS_DENIED);
  }

  private static Folder buildFolder(String name) {
    return Folder.builder().id(UUID.randomUUID()).name(name).build();
  }

  private static Document buildDocument(String name) {
    return Document.builder()
      .id(UUID.randomUUID())
      .name(name)
      .size(1024L)
      .mimeType("application/pdf")
      .build();
  }

  private void stubCounts(long folderTotal, long documentTotal) {    given(folderRepository.countByParentFolder(folderId.toString())).willReturn(folderTotal);
    given(documentRepository.countByFolder_Id(folderId)).willReturn(documentTotal);
  }

  private void stubFolderFinder() {
    given(folderRepository.findByParentFolder(eq(folderId.toString()), any(Pageable.class)))
      .willAnswer(invocation -> slice(folders, invocation.getArgument(1, Pageable.class)));
  }

  private void stubDocumentFinder() {
    given(documentRepository.findByFolder_Id(eq(folderId), any(Pageable.class)))
      .willAnswer(invocation -> slice(documents, invocation.getArgument(1, Pageable.class)));
  }

  // Cắt đúng cửa sổ [offset, offset + size) từ danh sách đầy đủ theo Pageable
  private static <T> Page<T> slice(List<T> all, Pageable pageable) {
    int from = (int) Math.min(pageable.getOffset(), all.size());
    int to = Math.min(from + pageable.getPageSize(), all.size());
    return new PageImpl<>(all.subList(from, to), PageRequest.of(0, Math.max(pageable.getPageSize(), 1)), all.size());
  }
}
