package com.spring.backend.service.impl;

import com.spring.backend.common.PageResponse;
import com.spring.backend.dto.folder.FolderCreateDto;
import com.spring.backend.dto.folder.FolderDto;
import com.spring.backend.dto.folder.FolderUpdateDto;
import com.spring.backend.entity.Folder;
import com.spring.backend.entity.User;
import com.spring.backend.entity.enums.Plan;
import com.spring.backend.entity.enums.Role;
import com.spring.backend.entity.enums.Status;
import com.spring.backend.exception.AppException;
import com.spring.backend.exception.ErrorCode;
import com.spring.backend.mapper.FolderMapper;
import com.spring.backend.repository.FolderRepository;
import com.spring.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class FolderServiceImplTest {

  @InjectMocks
  private FolderServiceImpl folderService;

  @Mock private FolderRepository folderRepository;
  @Mock private UserRepository userRepository;
  @Spy private FolderMapper folderMapper = new FolderMapper();

  private UUID ownerId;
  private User owner;
  private Folder folder;

  @BeforeEach
  void setUp() {
    ownerId = UUID.randomUUID();
    owner = User.builder()
      .id(ownerId)
      .email("owner@email.com")
      .passwordHash("hash")
      .displayName("owner")
      .role(Role.USER)
      .status(Status.ACTIVE)
      .plan(Plan.FREE)
      .build();

    folder = Folder.builder()
      .id(UUID.randomUUID())
      .name("Docs")
      .owner(owner)
      .build();
  }

  @Nested
  @DisplayName("create")
  class CreateTests {

    @Test
    @DisplayName("should create root folder when parentId is null")
    void shouldCreateRootFolder_whenParentIdNull() {
      FolderCreateDto dto = FolderCreateDto.builder().name("Docs").parentId(null).build();

      given(folderRepository.findByOwnerIdAndParentFolderIsNull(ownerId)).willReturn(List.of());
      given(userRepository.findById(ownerId)).willReturn(Optional.of(owner));
      given(folderRepository.save(any(Folder.class))).willAnswer(inv -> inv.getArgument(0));

      FolderDto result = folderService.create(dto, ownerId);

      assertThat(result.getName()).isEqualTo("Docs");
      assertThat(result.getOwnerId()).isEqualTo(ownerId);
      assertThat(result.getParentFolderId()).isNull();
      verify(folderRepository).save(any(Folder.class));
    }

    @Test
    @DisplayName("should generate unique name when name already exists")
    void shouldGenerateUniqueName_whenNameExists() {
      FolderCreateDto dto = FolderCreateDto.builder().name("Docs").parentId(null).build();
      Folder existing = Folder.builder().id(UUID.randomUUID()).name("Docs").owner(owner).build();

      given(folderRepository.findByOwnerIdAndParentFolderIsNull(ownerId)).willReturn(List.of(existing));
      given(userRepository.findById(ownerId)).willReturn(Optional.of(owner));
      given(folderRepository.save(any(Folder.class))).willAnswer(inv -> inv.getArgument(0));

      FolderDto result = folderService.create(dto, ownerId);

      assertThat(result.getName()).isEqualTo("Docs (1)");
    }

    @Test
    @DisplayName("should create child folder when parent exists")
    void shouldCreateChildFolder_whenParentExists() {
      UUID parentId = UUID.randomUUID();
      Folder parent = Folder.builder().id(parentId).name("Parent").owner(owner).build();
      FolderCreateDto dto = FolderCreateDto.builder().name("Child").parentId(parentId).build();

      given(folderRepository.findByOwnerIdAndParentFolderId(ownerId, parentId)).willReturn(List.of());
      given(folderRepository.findById(parentId)).willReturn(Optional.of(parent));
      given(userRepository.findById(ownerId)).willReturn(Optional.of(owner));
      given(folderRepository.save(any(Folder.class))).willAnswer(inv -> inv.getArgument(0));

      FolderDto result = folderService.create(dto, ownerId);

      assertThat(result.getName()).isEqualTo("Child");
      assertThat(result.getParentFolderId()).isEqualTo(parentId);
    }

    @Test
    @DisplayName("should throw INVALID_FOLDER_NAME when name is invalid")
    void shouldThrow_whenNameInvalid() {
      FolderCreateDto dto = FolderCreateDto.builder().name("a/b").parentId(null).build();

      assertThatThrownBy(() -> folderService.create(dto, ownerId))
        .isInstanceOf(AppException.class)
        .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_FOLDER_NAME));
    }

    @Test
    @DisplayName("should throw FOLDER_NOT_FOUND when parent does not exist")
    void shouldThrow_whenParentNotFound() {
      UUID parentId = UUID.randomUUID();
      FolderCreateDto dto = FolderCreateDto.builder().name("Child").parentId(parentId).build();

      given(folderRepository.findByOwnerIdAndParentFolderId(ownerId, parentId)).willReturn(List.of());
      given(folderRepository.findById(parentId)).willReturn(Optional.empty());

      assertThatThrownBy(() -> folderService.create(dto, ownerId))
        .isInstanceOf(AppException.class)
        .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.FOLDER_NOT_FOUND));
    }

    @Test
    @DisplayName("should throw USER_NOT_FOUND when owner does not exist")
    void shouldThrow_whenOwnerNotFound() {
      FolderCreateDto dto = FolderCreateDto.builder().name("Docs").parentId(null).build();

      given(folderRepository.findByOwnerIdAndParentFolderIsNull(ownerId)).willReturn(List.of());
      given(userRepository.findById(ownerId)).willReturn(Optional.empty());

      assertThatThrownBy(() -> folderService.create(dto, ownerId))
        .isInstanceOf(AppException.class)
        .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }
  }

  @Nested
  @DisplayName("update")
  class UpdateTests {

    @Test
    @DisplayName("should update name when owner matches")
    void shouldUpdate_whenOwnerMatches() {
      FolderUpdateDto dto = FolderUpdateDto.builder().id(folder.getId()).name("Renamed").parentId(null).build();

      given(folderRepository.findById(folder.getId())).willReturn(Optional.of(folder));
      given(folderRepository.save(any(Folder.class))).willAnswer(inv -> inv.getArgument(0));

      FolderDto result = folderService.update(dto, ownerId);

      assertThat(result.getName()).isEqualTo("Renamed");
    }

    @Test
    @DisplayName("should throw FOLDER_NOT_FOUND when folder does not exist")
    void shouldThrow_whenFolderNotFound() {
      FolderUpdateDto dto = FolderUpdateDto.builder().id(UUID.randomUUID()).name("X").build();

      given(folderRepository.findById(any(UUID.class))).willReturn(Optional.empty());

      assertThatThrownBy(() -> folderService.update(dto, ownerId))
        .isInstanceOf(AppException.class)
        .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.FOLDER_NOT_FOUND));
    }

    @Test
    @DisplayName("should throw FOLDER_ACCESS_DENIED when owner differs")
    void shouldThrow_whenOwnerDiffers() {
      FolderUpdateDto dto = FolderUpdateDto.builder().id(folder.getId()).name("X").build();

      given(folderRepository.findById(folder.getId())).willReturn(Optional.of(folder));

      assertThatThrownBy(() -> folderService.update(dto, UUID.randomUUID()))
        .isInstanceOf(AppException.class)
        .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.FOLDER_ACCESS_DENIED));
    }

    @Test
    @DisplayName("should throw FOLDER_NOT_FOUND when new parent does not exist")
    void shouldThrow_whenNewParentNotFound() {
      UUID parentId = UUID.randomUUID();
      FolderUpdateDto dto = FolderUpdateDto.builder().id(folder.getId()).name("X").parentId(parentId).build();

      given(folderRepository.findById(folder.getId())).willReturn(Optional.of(folder));
      given(folderRepository.findById(parentId)).willReturn(Optional.empty());

      assertThatThrownBy(() -> folderService.update(dto, ownerId))
        .isInstanceOf(AppException.class)
        .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.FOLDER_NOT_FOUND));
    }
  }

  @Nested
  @DisplayName("deleteSoft / findById")
  class DeleteFindTests {

    @Test
    @DisplayName("should call repository soft delete with ids and owner")
    void shouldDelegateSoftDelete() {
      List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());

      folderService.deleteSoft(ids, ownerId);

      ArgumentCaptor<List<UUID>> idsCaptor = ArgumentCaptor.forClass(List.class);
      ArgumentCaptor<Instant> timeCaptor = ArgumentCaptor.forClass(Instant.class);
      verify(folderRepository).softDeleteMultipleFolders(idsCaptor.capture(), timeCaptor.capture(), eq(ownerId));
      assertThat(idsCaptor.getValue()).isEqualTo(ids);
      assertThat(timeCaptor.getValue()).isNotNull();
    }

    @Test
    @DisplayName("should return folder when found by id")
    void shouldReturnFolder_whenFound() {
      given(folderRepository.findById(folder.getId())).willReturn(Optional.of(folder));

      assertThat(folderService.findById(folder.getId())).isEqualTo(folderMapper.toDto(folder));
    }

    @Test
    @DisplayName("should return null when not found by id")
    void shouldReturnNull_whenNotFound() {
      given(folderRepository.findById(any(UUID.class))).willReturn(Optional.empty());

      assertThat(folderService.findById(UUID.randomUUID())).isNull();
    }
  }

  @Nested
  @DisplayName("pagination")
  class PaginationTests {

    @Test
    @DisplayName("should list root folders with descending sort")
    void shouldListRootFolders() {
      Pageable pageable = PageRequest.of(0, 10, Sort.by("updatedAt").descending());
      given(folderRepository.findByOwnerIdAndParentFolderIsNull(pageable, ownerId))
        .willReturn(new PageImpl<>(List.of(folder), pageable, 1));

      PageResponse<FolderDto> result =
        folderService.findAllByOwnerIdInRoot(0, 10, "updatedAt", "desc", ownerId);

      assertThat(result.getContent()).containsExactly(folderMapper.toDto(folder));
      assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("should list child folders with ascending sort")
    void shouldListChildFolders() {
      UUID parentId = UUID.randomUUID();
      Pageable pageable = PageRequest.of(0, 10, Sort.by("name").ascending());
      given(folderRepository.findByOwnerIdAndParentFolderId(pageable, ownerId, parentId))
        .willReturn(new PageImpl<>(List.of(folder), pageable, 1));

      PageResponse<FolderDto> result =
        folderService.findAllByOwnerIdAndParentFolderId(0, 10, "name", "asc", ownerId, parentId);

      assertThat(result.getContent()).containsExactly(folderMapper.toDto(folder));
      assertThat(result.getTotalElements()).isEqualTo(1);
    }
  }
}
