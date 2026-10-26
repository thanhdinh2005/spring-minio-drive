package com.spring.backend.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.spring.backend.common.AppResponse;
import com.spring.backend.dto.sharing.SharingDetailsResponse;
import com.spring.backend.security.CustomUserDetails;
import com.spring.backend.service.SharingService;

import lombok.RequiredArgsConstructor;

/**
 * API bổ sung ngoài API Specs Module 3.
 * Xem các quyền chia sẻ và nguồn trực tiếp/kế thừa.
 */
@RestController
@RequiredArgsConstructor
public class SharingDetailsController {

    private final SharingService sharingService;

    @GetMapping("/folders/{folderId}/sharing-details")
    public ResponseEntity<AppResponse<SharingDetailsResponse>>
            getFolderSharingDetails(
                    @PathVariable("folderId") UUID folderId,
                    @AuthenticationPrincipal CustomUserDetails me
            ) {
        return ResponseEntity.ok(
                AppResponse.success(
                        sharingService.getFolderSharingDetails(
                                folderId,
                                me.getId()
                        )
                )
        );
    }

    @GetMapping("/documents/{documentId}/sharing-details")
    public ResponseEntity<AppResponse<SharingDetailsResponse>>
            getDocumentSharingDetails(
                    @PathVariable("documentId") UUID documentId,
                    @AuthenticationPrincipal CustomUserDetails me
            ) {
        return ResponseEntity.ok(
                AppResponse.success(
                        sharingService.getDocumentSharingDetails(
                                documentId,
                                me.getId()
                        )
                )
        );
    }
}