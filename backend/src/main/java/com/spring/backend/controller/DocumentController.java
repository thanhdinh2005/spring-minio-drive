package com.spring.backend.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.spring.backend.common.AppResponse;
import com.spring.backend.dto.document.PresignedUrlResponse;
import com.spring.backend.security.CustomUserDetails;
import com.spring.backend.service.DocumentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {

  private final DocumentService documentService;

  @GetMapping("/{id}/download-url")
  public ResponseEntity<AppResponse<PresignedUrlResponse>> getDownloadUrl(
    @PathVariable UUID id,
    @AuthenticationPrincipal CustomUserDetails me
  ) {
    PresignedUrlResponse response = documentService.getDownloadUrl(id, me.getId());
    return ResponseEntity.ok(AppResponse.success(response));
  }

  @GetMapping("/{id}/preview-url")
  public ResponseEntity<AppResponse<PresignedUrlResponse>> getPreviewUrl(
    @PathVariable UUID id,
    @AuthenticationPrincipal CustomUserDetails me
  ) {
    PresignedUrlResponse response = documentService.getPreviewUrl(id, me.getId());
    return ResponseEntity.ok(AppResponse.success(response));
  }
}
