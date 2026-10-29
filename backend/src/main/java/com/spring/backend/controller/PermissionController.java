package com.spring.backend.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import com.spring.backend.common.AppResponse;
import com.spring.backend.dto.sharing.SharePermissionResponse;
import com.spring.backend.dto.sharing.UpdatePermissionRequest;
import com.spring.backend.security.CustomUserDetails;
import com.spring.backend.service.SharingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Quản lý permission theo ID.
 *
 * PATCH là API đề xuất bổ sung ngoài API Specs Module 3.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/permissions")
public class PermissionController {

    private final SharingService sharingService;

    @PatchMapping("/{permissionId}")
    public ResponseEntity<AppResponse<SharePermissionResponse>>
            updatePermission(
                    @PathVariable("permissionId") UUID permissionId,
                    @Valid @RequestBody UpdatePermissionRequest request,
                    @AuthenticationPrincipal CustomUserDetails me
            ) {

        SharePermissionResponse result =
                sharingService.updatePermission(
                        permissionId,
                        request,
                        me.getId()
                );

        return ResponseEntity.ok(
                AppResponse.success(result)
        );
    }

    @DeleteMapping("/{permissionId}")
    public ResponseEntity<AppResponse<Void>> revokePermission(
        @PathVariable("permissionId") UUID permissionId,
        @AuthenticationPrincipal CustomUserDetails me
    ){
        sharingService.revokePermission(
                permissionId,
                me.getId()
        );
        return ResponseEntity.ok(
                AppResponse.<Void>success(null)
        );
    }


}