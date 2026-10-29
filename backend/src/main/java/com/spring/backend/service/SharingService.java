
package com.spring.backend.service;

import com.spring.backend.dto.sharing.DirectPermissionResponse;
import com.spring.backend.dto.sharing.SharePermissionRequest;
import com.spring.backend.dto.sharing.SharePermissionResponse;
import com.spring.backend.dto.sharing.UpdatePermissionRequest;
import com.spring.backend.dto.sharing.SharedWithMeResponse;
import java.util.List;
import java.util.UUID;
import com.spring.backend.dto.sharing.SharingDetailsResponse;
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

    List<SharedWithMeResponse> getSharedWithMe(UUID actorId);
    void revokePermission(
            UUID permissionId,
            UUID actorId
    );
}
