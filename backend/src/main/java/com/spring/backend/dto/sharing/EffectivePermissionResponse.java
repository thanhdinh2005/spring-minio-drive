package com.spring.backend.dto.sharing;

import java.util.List;
import java.util.UUID;

import com.spring.backend.entity.enums.PermissionType;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EffectivePermissionResponse {
    private UUID userId;
    private String userName;
    private String userEmail;
    private PermissionType effectivePermission;
    private List<PermissionSourceResponse> sources;
}
