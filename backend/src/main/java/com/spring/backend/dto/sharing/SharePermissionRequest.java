package com.spring.backend.dto.sharing;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.spring.backend.entity.enums.PermissionType;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SharePermissionRequest {

    private UUID sharedWithUserId;

    @NotNull(message = "Permission type is required")
    private PermissionType permissionType;

    @JsonIgnore
    @AssertTrue(message = "Permission must be VIEW or EDIT")
    public boolean isPermissionAllowed() {
        return permissionType == null
                || permissionType == PermissionType.VIEW
                || permissionType == PermissionType.EDIT;
    }
}