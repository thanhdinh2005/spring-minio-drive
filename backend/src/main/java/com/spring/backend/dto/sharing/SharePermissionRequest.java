package com.spring.backend.dto.sharing;

import com.spring.backend.entity.Permission;
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

    @NotNull(message = "Permission cannot be null")
    private PermissionType permission;

    @AssertTrue(message = "Quyen chia se chi duoc la VIEW hoac EDIT")
    public boolean isValidPermissionAllowed(){
        return permission == null
                || permission == PermissionType.VIEW
                || permission == PermissionType.EDIT;
    }

}
