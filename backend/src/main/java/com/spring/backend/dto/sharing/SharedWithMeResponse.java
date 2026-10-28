package com.spring.backend.dto.sharing;
import java.util.UUID;

import com.spring.backend.entity.enums.PermissionType;

import lombok.Builder;
import lombok.Getter;

@Getter 
@Builder 
public class SharedWithMeResponse {
    private UUID folderId;
    private String name;
    private PermissionType permissionType;
    private String sharedBy;
}
