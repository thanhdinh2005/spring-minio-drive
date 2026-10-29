package com.spring.backend.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;

import com.spring.backend.common.AppResponse;
import com.spring.backend.dto.sharing.PublicLinkResponse;
import com.spring.backend.security.CustomUserDetails;
import com.spring.backend.service.SharingService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/folders")
public class PublicLinkController {

    private final SharingService sharingService;

    @PostMapping("/{folderId}/public-link")
    public ResponseEntity<AppResponse<PublicLinkResponse>> createOrGetPublicLink(
            @PathVariable("folderId") UUID folderId,
            @AuthenticationPrincipal CustomUserDetails me
    ) {
        PublicLinkResponse result =
                sharingService.createOrGetPublicLink(
                        folderId,
                        me.getId()
                );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AppResponse.created(result));
    }

    @DeleteMapping("/{folderId}/public-link")
public ResponseEntity<AppResponse<Void>> revokePublicLink(
        @PathVariable("folderId") UUID folderId,
        @AuthenticationPrincipal CustomUserDetails me
) {
    sharingService.revokePublicLink(
            folderId,
            me.getId()
    );

    return ResponseEntity.ok()
            .body(AppResponse.<Void>success(null));
}
}