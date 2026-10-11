package com.spring.backend.dto.sharing;

import java.util.UUID;

import com.spring.backend.entity.enums.PermissionType;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SharePermissionResponse {
    private UUID permissionId;
    private UUID folderId;
    private UUID sharedWithUserId;
    private String sharedWithUserEmail;
    private String sharedWithUserName;
    private PermissionType permission;
    private UUID grantedById;
}
