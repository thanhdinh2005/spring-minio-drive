package com.spring.backend.service.impl;

import com.spring.backend.dto.sharing.PublicLinkResponse;
import com.spring.backend.entity.*;
import com.spring.backend.entity.enums.PermissionType;
import com.spring.backend.exception.*;
import com.spring.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit tests only: mocks do not verify database locking. */
@ExtendWith(MockitoExtension.class)
class PublicLinkServiceTest {
    @Mock FolderRepository folderRepository;
    @Mock PermissionRepository permissionRepository;
    @Mock DocumentRepository documentRepository;
    @Mock UserRepository userRepository;
    @Mock SharingPermissionResolver sharingPermissionResolver;
    @InjectMocks SharingServiceImpl service;

    private User owner;
    private Folder folder;
    private static final String BASE = "http://localhost:8081/api/public/folders";

    @BeforeEach
    void setUp() {
        owner = User.builder().id(UUID.randomUUID()).email("owner@test.com").build();
        folder = Folder.builder().id(UUID.randomUUID()).name("Project").owner(owner).build();
        ReflectionTestUtils.setField(service, "publicFolderBaseUrl", BASE);
    }

    private Permission link() {
        return Permission.builder().id(UUID.randomUUID()).folder(folder).grantedBy(owner)
            .permissionType(PermissionType.VIEW).publicToken("existing-token").build();
    }

    private void ownedFolder() {
        when(folderRepository.findByIdForUpdate(folder.getId())).thenReturn(Optional.of(folder));
    }

    private void error(ErrorCode code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(AppException.class, action).getErrorCode());
    }

    @Test
    void createsViewOnlyPublicPermissionAndUrl() {
        ownedFolder();
        when(permissionRepository.findByFolder_IdAndSharedWithUserIsNullAndPublicTokenIsNotNull(folder.getId()))
            .thenReturn(Optional.empty());
        when(permissionRepository.saveAndFlush(any(Permission.class)))
            .thenAnswer(call -> call.getArgument(0));

        PublicLinkResponse result = service.createOrGetPublicLink(folder.getId(), owner.getId());

        ArgumentCaptor<Permission> captor = ArgumentCaptor.forClass(Permission.class);
        verify(permissionRepository).saveAndFlush(captor.capture());
        Permission saved = captor.getValue();
        assertSame(folder, saved.getFolder());
        assertSame(owner, saved.getGrantedBy());
        assertNull(saved.getSharedWithUser());
        assertEquals(PermissionType.VIEW, saved.getPermissionType());
        assertTrue(saved.getPublicToken().matches("[A-Za-z0-9_-]{43}"));
        assertEquals(32, Base64.getUrlDecoder().decode(saved.getPublicToken()).length);
        assertEquals(saved.getPublicToken(), result.getPublicToken());
        assertEquals(BASE + "/" + saved.getPublicToken(), result.getPublicUrl());
    }

    @Test
    void reusesExistingTokenWithoutSavingAndNormalizesBaseUrl() {
        ownedFolder();
        Permission existing = link();
        ReflectionTestUtils.setField(service, "publicFolderBaseUrl", BASE + "///");
        when(permissionRepository.findByFolder_IdAndSharedWithUserIsNullAndPublicTokenIsNotNull(folder.getId()))
            .thenReturn(Optional.of(existing));
        PublicLinkResponse result = service.createOrGetPublicLink(folder.getId(), owner.getId());
        assertEquals(existing.getPublicToken(), result.getPublicToken());
        assertEquals(BASE + "/" + existing.getPublicToken(), result.getPublicUrl());
        verify(permissionRepository, never()).saveAndFlush(any(Permission.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void creationAndRevocationRejectMissingFolder(boolean revoke) {
        when(folderRepository.findByIdForUpdate(folder.getId())).thenReturn(Optional.empty());
        error(ErrorCode.FOLDER_NOT_FOUND, () -> {
            if (revoke) service.revokePublicLink(folder.getId(), owner.getId());
            else service.createOrGetPublicLink(folder.getId(), owner.getId());
        });
        verifyNoInteractions(permissionRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void creationAndRevocationRejectNonOwnerAndNullActor(boolean revoke) {
        ownedFolder();
        for (UUID actor : new UUID[]{UUID.randomUUID(), null}) {
            error(ErrorCode.CANNOT_SHARE_WITHOUT_OWNERSHIP, () -> {
                if (revoke) service.revokePublicLink(folder.getId(), actor);
                else service.createOrGetPublicLink(folder.getId(), actor);
            });
        }
        verifyNoInteractions(permissionRepository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsBlankTokensBeforeQuerying(String token) {
        error(ErrorCode.PUBLIC_LINK_NOT_FOUND, () -> service.getPublicFolderContents(token));
        verifyNoInteractions(permissionRepository, folderRepository, documentRepository);
    }

    @Test
    void rejectsUnknownToken() {
        when(permissionRepository.findByPublicTokenAndSharedWithUserIsNullAndPermissionType("missing", PermissionType.VIEW))
            .thenReturn(Optional.empty());
        error(ErrorCode.PUBLIC_LINK_NOT_FOUND, () -> service.getPublicFolderContents("missing"));
        verifyNoInteractions(folderRepository, documentRepository);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 2})
    void rejectsAmbiguousFolderBeforeReadingContents(long count) {
        Permission existing = link();
        when(permissionRepository.findByPublicTokenAndSharedWithUserIsNullAndPermissionType(existing.getPublicToken(), PermissionType.VIEW))
            .thenReturn(Optional.of(existing));
        when(folderRepository.countByOwner_IdAndName(owner.getId(), folder.getName())).thenReturn(count);
        error(ErrorCode.PUBLIC_LINK_NOT_FOUND, () -> service.getPublicFolderContents(existing.getPublicToken()));
        verify(folderRepository, never()).findByOwner_IdAndParentFolder(any(), any());
        verifyNoInteractions(documentRepository);
    }

    private void readableLink() {
        Permission existing = link();
        when(permissionRepository.findByPublicTokenAndSharedWithUserIsNullAndPermissionType("existing-token", PermissionType.VIEW))
            .thenReturn(Optional.of(existing));
        when(folderRepository.countByOwner_IdAndName(owner.getId(), folder.getName())).thenReturn(1L);
    }

    @Test
    void returnsEmptyContentsForEmptyFolder() {
        readableLink();
        when(folderRepository.findByOwner_IdAndParentFolder(owner.getId(), folder.getName())).thenReturn(List.of());
        when(documentRepository.findByFolder_Id(folder.getId())).thenReturn(List.of());
        var result = service.getPublicFolderContents("existing-token");
        assertEquals(folder.getId(), result.getFolder().getId());
        assertEquals(folder.getName(), result.getFolder().getName());
        assertTrue(result.getSubFolders().isEmpty());
        assertTrue(result.getDocuments().isEmpty());
    }

    @Test
    void mapsChildFlagsAndDocumentMetadata() {
        readableLink();
        Folder child = Folder.builder().id(UUID.randomUUID()).name("Child").owner(owner).build();
        Folder leaf = Folder.builder().id(UUID.randomUUID()).name("Leaf").owner(owner).build();
        Document doc = Document.builder().id(UUID.randomUUID()).name("Report.pdf").folder(folder)
            .owner(owner).objectKey("private-key").size(123L).mimeType("application/pdf").build();
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        doc.setCreatedAt(created);
        doc.setUpdatedAt(created.plusSeconds(60));
        when(folderRepository.findByOwner_IdAndParentFolder(owner.getId(), folder.getName())).thenReturn(List.of(child, leaf));
        when(folderRepository.existsByOwner_IdAndParentFolder(owner.getId(), child.getName())).thenReturn(true);
        when(folderRepository.existsByOwner_IdAndParentFolder(owner.getId(), leaf.getName())).thenReturn(false);
        when(documentRepository.findByFolder_Id(folder.getId())).thenReturn(List.of(doc));
        var result = service.getPublicFolderContents("existing-token");
        assertEquals(2, result.getSubFolders().size());
        assertEquals(child.getId(), result.getSubFolders().get(0).getId());
        assertTrue(result.getSubFolders().get(0).isHasChildren());
        assertFalse(result.getSubFolders().get(1).isHasChildren());
        var actual = result.getDocuments().get(0);
        assertEquals(doc.getId(), actual.getId());
        assertEquals(doc.getName(), actual.getName());
        assertEquals(doc.getSize(), actual.getSize());
        assertEquals(doc.getMimeType(), actual.getMimeType());
        assertEquals(created, actual.getCreatedAt());
        assertEquals(doc.getUpdatedAt(), actual.getUpdatedAt());
    }

    @Test
    void revokesExactPublicPermission() {
        ownedFolder();
        Permission existing = link();
        when(permissionRepository.findByFolder_IdAndSharedWithUserIsNullAndPublicTokenIsNotNull(folder.getId()))
            .thenReturn(Optional.of(existing));
        service.revokePublicLink(folder.getId(), owner.getId());
        verify(permissionRepository).delete(same(existing));
    }

    @Test
    void revocationRejectsMissingLinkWithoutDeleting() {
        ownedFolder();
        when(permissionRepository.findByFolder_IdAndSharedWithUserIsNullAndPublicTokenIsNotNull(folder.getId()))
            .thenReturn(Optional.empty());
        error(ErrorCode.PUBLIC_LINK_NOT_FOUND, () -> service.revokePublicLink(folder.getId(), owner.getId()));
        verify(permissionRepository, never()).delete(any(Permission.class));
    }
}
