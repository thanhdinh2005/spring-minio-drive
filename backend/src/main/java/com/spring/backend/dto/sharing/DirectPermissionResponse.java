package com.spring.backend.dto.sharing;

import java.util.UUID;

import com.spring.backend.entity.enums.PermissionType;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DirectPermissionResponse {

    private UUID id;
    private SharedWithUserResponse sharedWithUser;
    private PermissionType permissionType;

    @Getter
    @Builder
    public static class SharedWithUserResponse {

        private UUID id;
        private String email;
    }
}