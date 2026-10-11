package com.spring.backend.service;

import java.util.List;
import java.util.UUID;

import com.spring.backend.dto.sharing.SharePermissionRequest;
import com.spring.backend.dto.sharing.SharePermissionResponse;

public interface SharingService {
    SharePermissionResponse grantOrUpdatePermission(
        UUID folderId,
        UUID recipientId,
        SharePermissionRequest request,
        UUID actorId
    );

    List<SharePermissionResponse> getDirectShares(
        UUID folderId,
        UUID actorId
    );
}
