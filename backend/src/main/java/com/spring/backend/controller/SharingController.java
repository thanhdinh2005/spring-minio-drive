
package com.spring.backend.controller;

import com.spring.backend.dto.sharing.DirectPermissionResponse;
import com.spring.backend.dto.sharing.SharePermissionRequest;
import com.spring.backend.dto.sharing.SharePermissionResponse;
import com.spring.backend.service.SharingService;

// Dùng đúng package của dự án nếu các class này nằm ở nơi khác
import com.spring.backend.security.CustomUserDetails;
import com.spring.backend.common.AppResponse;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/*
 * SHARING CONTROLLER
 *
 * Chức năng 1: Cấp quyền chia sẻ folder
 * POST /api/folders/{id}/permissions
 *
 * Chức năng 2: Xem danh sách quyền trực tiếp
 * GET /api/folders/{id}/permissions
 *
 * Yêu cầu:
 * - Người dùng phải đăng nhập (JWT).
 * - Chỉ owner của folder được quản lý quyền chia sẻ.
 *
 * Chạy test:
 * mvnw.cmd -Dtest=SharingIntegrationTest test
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/folders/{folderId}/permissions")
public class SharingController {

    private final SharingService sharingService;

    /*
     * CHỨC NĂNG 1: CẤP QUYỀN CHIA SẺ
     *
     * Request:
     * {
     *   "sharedWithUserId": "uuid",
     *   "permissionType": "VIEW"
     * }
     *
     * Response: HTTP 201 Created
     */
    @PostMapping
    public ResponseEntity<AppResponse<SharePermissionResponse>> grantPermission(
            @PathVariable UUID folderId,
            @Valid @RequestBody SharePermissionRequest request,
            @AuthenticationPrincipal CustomUserDetails me) {

        SharePermissionResponse result =
            sharingService.grantPermission(
                folderId,
                request,
                me.getId()
            );

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(AppResponse.created(result));
    }

    /*
     * CHỨC NĂNG 2: XEM DANH SÁCH QUYỀN TRỰC TIẾP
     *
     * Chỉ lấy các quyền được cấp trực tiếp
     * trên folder đang chọn.
     *
     * Không lấy quyền kế thừa từ folder cha.
     * Không lấy public link.
     *
     * Response: HTTP 200 OK
     */
    @GetMapping
    public ResponseEntity<AppResponse<List<DirectPermissionResponse>>>
            getDirectShares(
                    @PathVariable UUID folderId,
                    @AuthenticationPrincipal CustomUserDetails me
            ) {

        List<DirectPermissionResponse> result =
                sharingService.getDirectShares(
                        folderId,
                        me.getId()
                );

        return ResponseEntity.ok(AppResponse.success(result));
    }
}
