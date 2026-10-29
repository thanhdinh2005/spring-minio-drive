
package com.spring.backend.service;

import com.spring.backend.dto.sharing.DirectPermissionResponse;
import com.spring.backend.dto.sharing.SharePermissionRequest;
import com.spring.backend.dto.sharing.SharePermissionResponse;
import com.spring.backend.dto.sharing.UpdatePermissionRequest;
import com.spring.backend.dto.sharing.PublicLinkResponse;
import com.spring.backend.dto.sharing.SharingDetailsResponse;
import com.spring.backend.dto.folder.FolderContentsResponse;

import com.spring.backend.dto.sharing.SharedWithMeResponse;
import java.util.List;
import java.util.UUID;

public interface SharingService {

    SharePermissionResponse grantPermission(
        UUID folderId,
        SharePermissionRequest request,
        UUID actorId
    );

    List<DirectPermissionResponse> getDirectShares(
        UUID folderId,
        UUID actorId
    );
    SharePermissionResponse updatePermission(
            UUID permissionId,
            UpdatePermissionRequest request,
            UUID actorId
    );
    SharingDetailsResponse getFolderSharingDetails(
            UUID folderId,
            UUID actorId
    );

    SharingDetailsResponse getDocumentSharingDetails(
            UUID documentId,
            UUID actorId
    );

    PublicLinkResponse createOrGetPublicLink(
            UUID folderd,
            UUID actorId
    );

    FolderContentsResponse getPublicFolderContents(String token);

        void revokePublicLink(
                UUID folderId,
                UUID actorId
        );



    List<SharedWithMeResponse> getSharedWithMe(UUID actorId);
    void revokePermission(
            UUID permissionId,
            UUID actorId
    );
}
