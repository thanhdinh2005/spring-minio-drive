package com.spring.backend.service.impl;

import com.spring.backend.dto.folder.FolderCreateRequest;
import com.spring.backend.dto.folder.FolderDto;
import com.spring.backend.entity.Folder;
import com.spring.backend.entity.User;
import com.spring.backend.entity.enums.Plan;
import com.spring.backend.entity.enums.Role;
import com.spring.backend.entity.enums.Status;
import com.spring.backend.exception.AppException;
import com.spring.backend.exception.ErrorCode;
import com.spring.backend.repository.FolderRepository;
import com.spring.backend.repository.UserRepository;
import com.spring.backend.service.FolderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FolderServiceImplTest {

    @InjectMocks
    private FolderService folderService;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private UserRepository userRepository;

    private UUID userId;
    private User mockUser;
    private UUID parentFolderId;
    private Folder mockParentFolder;
    private Instant now;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        parentFolderId = UUID.randomUUID();
        now = Instant.now();

        mockUser = User.builder()
                .id(userId)
                .email("test@email.com")
                .passwordHash("password")
                .displayName("test user")
                .status(Status.ACTIVE)
                .role(Role.USER)
                .plan(Plan.FREE)
                .build();

        mockParentFolder = Folder.builder()
                .id(parentFolderId)
                .name("Parent Folder")
                .owner(mockUser)
                .build();
    }

    @Nested
    @DisplayName("createFolder")
    class CreateFolderTests {

        @Test
        @DisplayName("should create root folder successfully when parentFolderId is null")
        void shouldCreateRootFolder_whenParentFolderIdIsNull() {
            // Given
            FolderCreateRequest request = FolderCreateRequest.builder()
                    .name("Tài liệu dự án")
                    .parentFolderId(null)
                    .build();

            given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));
            given(folderRepository.save(any(Folder.class))).willAnswer(invocation -> {
                Folder folder = invocation.getArgument(0);
                folder.setId(UUID.randomUUID());
                folder.setCreatedAt(now);
                folder.setUpdatedAt(now);
                return folder;
            });

            // When
            FolderDto result = folderService.createFolder(request, userId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Tài liệu dự án");
            assertThat(result.getOwnerId()).isEqualTo(userId);
            assertThat(result.getParentFolderId()).isNull();
            assertThat(result.getCreatedAt()).isEqualTo(now);
            assertThat(result.getUpdatedAt()).isEqualTo(now);

            verify(userRepository).findById(userId);
            verify(folderRepository).save(any(Folder.class));
        }

        @Test
        @DisplayName("should create sub folder successfully when user is owner of parent")
        void shouldCreateSubFolder_whenUserIsOwnerOfParent() {
            // Given
            FolderCreateRequest request = FolderCreateRequest.builder()
                    .name("Con 1")
                    .parentFolderId(parentFolderId)
                    .build();

            given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));
            given(folderRepository.findById(parentFolderId)).willReturn(Optional.of(mockParentFolder));
            given(folderRepository.save(any(Folder.class))).willAnswer(invocation -> {
                Folder folder = invocation.getArgument(0);
                folder.setId(UUID.randomUUID());
                folder.setCreatedAt(now);
                folder.setUpdatedAt(now);
                return folder;
            });

            // When
            FolderDto result = folderService.createFolder(request, userId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Con 1");
            assertThat(result.getOwnerId()).isEqualTo(userId);
            assertThat(result.getParentFolderId()).isEqualTo(parentFolderId);

            verify(folderRepository).findById(parentFolderId);
            verify(folderRepository).save(any(Folder.class));
        }

        @Test
        @DisplayName("should throw FOLDER_NOT_FOUND when parent folder does not exist")
        void shouldThrowFolderNotFound_whenParentFolderDoesNotExist() {
            // Given
            FolderCreateRequest request = FolderCreateRequest.builder()
                    .name("Con 1")
                    .parentFolderId(parentFolderId)
                    .build();

            given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));
            given(folderRepository.findById(parentFolderId)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> folderService.createFolder(request, userId))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> {
                        AppException appEx = (AppException) ex;
                        assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.FOLDER_NOT_FOUND);
                    });

            verify(folderRepository, never()).save(any(Folder.class));
        }

        @Test
        @DisplayName("should throw FOLDER_ACCESS_DENIED when user is not owner of parent folder")
        void shouldThrowFolderAccessDenied_whenUserIsNotOwnerOfParent() {
            // Given
            UUID anotherUserId = UUID.randomUUID();
            User anotherUser = User.builder()
                    .id(anotherUserId)
                    .email("another@email.com")
                    .passwordHash("password")
                    .displayName("another user")
                    .status(Status.ACTIVE)
                    .role(Role.USER)
                    .plan(Plan.FREE)
                    .build();

            // Parent folder owned by anotherUser, not mockUser
            mockParentFolder.setOwner(anotherUser);

            FolderCreateRequest request = FolderCreateRequest.builder()
                    .name("Con 1")
                    .parentFolderId(parentFolderId)
                    .build();

            given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));
            given(folderRepository.findById(parentFolderId)).willReturn(Optional.of(mockParentFolder));

            // When & Then
            assertThatThrownBy(() -> folderService.createFolder(request, userId))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> {
                        AppException appEx = (AppException) ex;
                        assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.FOLDER_ACCESS_DENIED);
                    });

            verify(folderRepository, never()).save(any(Folder.class));
        }

        @Test
        @DisplayName("should throw USER_NOT_FOUND when user does not exist")
        void shouldThrowUserNotFound_whenUserDoesNotExist() {
            // Given
            FolderCreateRequest request = FolderCreateRequest.builder()
                    .name("Tài liệu dự án")
                    .parentFolderId(null)
                    .build();

            given(userRepository.findById(userId)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> folderService.createFolder(request, userId))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> {
                        AppException appEx = (AppException) ex;
                        assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
                    });

            verify(folderRepository, never()).save(any(Folder.class));
        }
    }
}
