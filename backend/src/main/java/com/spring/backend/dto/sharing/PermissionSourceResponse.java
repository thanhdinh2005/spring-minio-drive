package com.spring.backend.dto.sharing;

import java.util.UUID;

import com.spring.backend.entity.enums.PermissionType;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PermissionSourceResponse {
    private UUID permissionId;
    private UUID folderId;
    private String folderName;//ten folder cap quyen
    private PermissionType permission;
    private boolean inherited;//false neu quyen dc cap cho folder dang chon, true neu quyen cua folde nay dc ke thua
    private UUID grantedById;
}
