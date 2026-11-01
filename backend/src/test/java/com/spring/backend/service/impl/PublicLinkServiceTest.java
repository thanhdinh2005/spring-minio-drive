package com.spring.backend.service.impl;

import com.spring.backend.dto.sharing.PublicLinkResponse;
import com.spring.backend.entity.*;
import com.spring.backend.entity.enums.PermissionType;
import com.spring.backend.exception.*;
import com.spring.backend.repository.*;
import com.spring.backend.service.StorageService;
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
    @Mock StorageService storageService;
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

    private void readableLink() {
        Permission existing = link();
        when(permissionRepository.findByPublicTokenAndSharedWithUserIsNullAndPermissionType("existing-token", PermissionType.VIEW))
            .thenReturn(Optional.of(existing));
    }

    @Test
    void returnsEmptyContentsForEmptyFolder() {
        readableLink();
        when(folderRepository.findByParent_Id(folder.getId())).thenReturn(List.of());
        when(documentRepository.findByFolder_Id(folder.getId())).thenReturn(List.of());
        var result = service.getPublicFolderContents("existing-token");
        assertEquals(folder.getId(), result.getFolder().getId());
        assertEquals(folder.getName(), result.getFolder().getName());
        assertTrue(result.getItems().isEmpty());
        assertEquals(0, result.getTotalElements());
        assertTrue(result.isLast());
    }

    @Test
    void mapsChildFlagsAndDocumentMetadata() {
        readableLink();
        Folder child = Folder.builder().id(UUID.randomUUID()).name("Child").owner(owner).build();
        Folder leaf = Folder.builder().id(UUID.randomUUID()).name("Leaf").owner(owner).build();
        Document doc = Document.builder().id(UUID.randomUUID()).name("Report.pdf").folder(folder)
            .owner(owner).objectKey("private-key").size(123L).mimeType("application/pdf").build();
        when(folderRepository.findByParent_Id(folder.getId())).thenReturn(List.of(child, leaf));
        when(folderRepository.existsByParent_Id(child.getId())).thenReturn(true);
        when(folderRepository.existsByParent_Id(leaf.getId())).thenReturn(false);
        when(documentRepository.findByFolder_Id(folder.getId())).thenReturn(List.of(doc));
        var result = service.getPublicFolderContents("existing-token");
        assertEquals(3, result.getItems().size());
        var first = result.getItems().get(0);
        assertEquals("FOLDER", first.getItemType());
        assertEquals(child.getId(), first.getId());
        assertTrue(first.isHasChildren());
        assertFalse(result.getItems().get(1).isHasChildren());
        var actual = result.getItems().get(2);
        assertEquals("DOCUMENT", actual.getItemType());
        assertEquals(doc.getId(), actual.getId());
        assertEquals(doc.getName(), actual.getName());
        assertEquals(doc.getSize(), actual.getSize());
        assertEquals(doc.getMimeType(), actual.getMimeType());
    }

    @Test
    void publicDocumentsCarryPresignedUrlsAndItemPaths() {
        readableLink();
        Document pdf = Document.builder().id(UUID.randomUUID()).name("Report.pdf").folder(folder)
            .owner(owner).objectKey("private/report").size(10L).mimeType("application/pdf").build();
        Document zip = Document.builder().id(UUID.randomUUID()).name("bundle.zip").folder(folder)
            .owner(owner).objectKey("private/bundle").size(20L).mimeType("application/zip").build();
        when(folderRepository.findByParent_Id(folder.getId())).thenReturn(List.of());
        when(documentRepository.findByFolder_Id(folder.getId())).thenReturn(List.of(pdf, zip));
        when(storageService.presignGet(anyString(), eq(300L), anyString()))
            .thenReturn("https://storage/signed");

        var result = service.getPublicFolderContents("existing-token");

        var pdfItem = result.getItems().get(0);
        assertEquals("documents/" + pdf.getId(), pdfItem.getItemUrl());
        assertEquals("https://storage/signed", pdfItem.getDownloadUrl());
        assertEquals("https://storage/signed", pdfItem.getPreviewUrl());
        var zipItem = result.getItems().get(1);
        assertNotNull(zipItem.getDownloadUrl());
        assertNull(zipItem.getPreviewUrl());
        verify(storageService).presignGet(eq("private/report"), eq(300L),
            contains("attachment; filename=\"Report.pdf\""));
        verify(storageService).presignGet(eq("private/bundle"), eq(300L),
            contains("attachment; filename=\"bundle.zip\""));
    }

    @Test
    void subFoldersExposeFolderItemPathAndRootDoesNot() {
        readableLink();
        Folder child = Folder.builder().id(UUID.randomUUID()).name("Child").owner(owner).parent(folder).build();
        when(folderRepository.findByParent_Id(folder.getId())).thenReturn(List.of(child));
        when(documentRepository.findByFolder_Id(folder.getId())).thenReturn(List.of());

        var result = service.getPublicFolderContents("existing-token");

        assertEquals("folders/" + child.getId(), result.getItems().get(0).getItemUrl());
        assertEquals(folder.getId(), result.getFolder().getId());
    }

    @Test
    void browsesDescendantFolderWithSameToken() {
        readableLink();
        Folder child = Folder.builder().id(UUID.randomUUID()).name("Child").owner(owner).parent(folder).build();
        Folder grandchild = Folder.builder().id(UUID.randomUUID()).name("Grandchild").owner(owner).parent(child).build();
        when(folderRepository.findById(grandchild.getId())).thenReturn(Optional.of(grandchild));
        when(folderRepository.findByParent_Id(grandchild.getId())).thenReturn(List.of());
        when(documentRepository.findByFolder_Id(grandchild.getId())).thenReturn(List.of());

        var result = service.getPublicSubFolderContents("existing-token", grandchild.getId());

        assertEquals(grandchild.getId(), result.getFolder().getId());
    }

    @Test
    void rejectsFolderOutsidePublicTree() {
        readableLink();
        User other = User.builder().id(UUID.randomUUID()).email("other@test.com").build();
        Folder foreign = Folder.builder().id(UUID.randomUUID()).name("Foreign").owner(other).build();
        when(folderRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        error(ErrorCode.PUBLIC_LINK_NOT_FOUND,
            () -> service.getPublicSubFolderContents("existing-token", foreign.getId()));
        verifyNoInteractions(storageService);
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
