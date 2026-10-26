package com.spring.backend.dto.sharing;

import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SharingDetailsResponse {
    private UUID resourceId;
    private String resourceName;
    private UUID ownerId;
    private List<EffectivePermissionResponse> users;
}
