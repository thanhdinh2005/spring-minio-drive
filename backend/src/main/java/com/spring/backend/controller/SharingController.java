package com.spring.backend.controller;

import com.spring.backend.common.AppResponse;
import com.spring.backend.dto.sharing.SharePermissionRequest;
import com.spring.backend.security.CustomUserDetails;
import com.spring.backend.service.SharingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.UUID;

@RestController
@RequestMapping("/folder/{folderId}/shares")
@RequiredArgsConstructor
public class SharingController {

    private final SharingService sharingService;

    @PutMapping("/{userId}")
    public ResponseEntity<?> grantOrUpdatePermission(
            @PathVariable("folderId") UUID folderId,
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody SharePermissionRequest request,//Chuyển JSON thành DTO rồi kiểm tra validation
            @AuthenticationPrincipal CustomUserDetails me //Lấy user mà JWT filter đã xác thực
    ) {
        return ResponseEntity.ok(
                AppResponse.success(
                        sharingService.grantOrUpdatePermission(
                                folderId,
                                userId,
                                request,
                                me.getId()
                        )
                )
        );
    }

    @GetMapping
    public ResponseEntity<?> getDirectShares(
            @PathVariable("folderId") UUID folderId,
            @AuthenticationPrincipal CustomUserDetails me
    ) {
        return ResponseEntity.ok(
                AppResponse.success(
                        sharingService.getDirectShares(
                                folderId,
                                me.getId()
                        )
                )
        );
    }
}