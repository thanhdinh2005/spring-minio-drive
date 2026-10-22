
package com.spring.backend.dto.sharing;

import com.spring.backend.entity.enums.PermissionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SharePermissionResponse {

    private UUID id;
    private UUID folderId;
    private UUID sharedWithUserId;
    private PermissionType permissionType;
}
