package com.spring.backend.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.spring.backend.common.AppResponse;
import com.spring.backend.dto.document.DocumentUpdateDto;
import com.spring.backend.security.CustomUserDetails;
import com.spring.backend.service.DocumentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /** 2.4 GET /api/documents/{id}/download-url */
    @GetMapping("/{id}/download-url")
    public ResponseEntity<?> downloadUrl(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails me) {
        return ResponseEntity.ok(AppResponse.success(documentService.downloadUrl(id, me.getId())));
    }

    /** 2.5 GET /api/documents/{id}/preview-url */
    @GetMapping("/{id}/preview-url")
    public ResponseEntity<?> previewUrl(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails me) {
        return ResponseEntity.ok(AppResponse.success(documentService.previewUrl(id, me.getId())));
    }

    /** 2.6 PATCH /api/documents/{id} */
    @PatchMapping("/{id}")
    public ResponseEntity<?> patch(
            @PathVariable UUID id,
            @RequestBody DocumentUpdateDto dto,
            @AuthenticationPrincipal CustomUserDetails me) {
        return ResponseEntity.ok(AppResponse.success(documentService.patch(id, dto, me.getId())));
    }

    /** 2.7 DELETE /api/documents/{id} — xóa cứng, chỉ owner */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails me) {
        documentService.delete(id, me.getId());
        return ResponseEntity.ok(AppResponse.success(null));
    }
}
