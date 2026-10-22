package com.spring.backend.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.spring.backend.common.AppResponse;
import com.spring.backend.dto.document.CopyDocumentRequest;
import com.spring.backend.dto.document.DocumentResponse;
import com.spring.backend.dto.document.PresignedUrlResponse;
import com.spring.backend.dto.document.UpdateDocumentRequest;
import com.spring.backend.dto.document.UploadDocumentRequest;
import com.spring.backend.dto.folder.BulkDeleteRequest;
import com.spring.backend.security.CustomUserDetails;
import com.spring.backend.service.DocumentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {

  private final DocumentService documentService;

  @PostMapping("/{folderId}")
  public ResponseEntity<AppResponse<DocumentResponse>> upload(
    @PathVariable UUID folderId,
    @RequestPart("file") MultipartFile file,
    @AuthenticationPrincipal CustomUserDetails me
  ) {
    UploadDocumentRequest request = UploadDocumentRequest.builder().file(file).build();
    DocumentResponse response = documentService.upload(folderId, request, me.getId());
    return ResponseEntity.status(HttpStatus.CREATED)
      .body(AppResponse.success(response, HttpStatus.CREATED));
  }

  @GetMapping("/{id}")
  public ResponseEntity<AppResponse<DocumentResponse>> getById(
    @PathVariable UUID id,
    @AuthenticationPrincipal CustomUserDetails me
  ) {
    DocumentResponse response = documentService.getById(id, me.getId());
    return ResponseEntity.ok(AppResponse.success(response));
  }

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

  @PatchMapping("/{id}")
  public ResponseEntity<AppResponse<DocumentResponse>> update(
    @PathVariable UUID id,
    @Valid @RequestBody UpdateDocumentRequest request,
    @AuthenticationPrincipal CustomUserDetails me
  ) {
    DocumentResponse response = documentService.update(id, request, me.getId());
    return ResponseEntity.ok(AppResponse.success(response));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<AppResponse<Void>> delete(
    @PathVariable UUID id,
    @AuthenticationPrincipal CustomUserDetails me
  ) {
    documentService.delete(id, me.getId());
    return ResponseEntity.ok(AppResponse.success(null));
  }

  @PostMapping("/bulk-delete")
  public ResponseEntity<AppResponse<Void>> bulkDelete(
    @Valid @RequestBody BulkDeleteRequest request,
    @AuthenticationPrincipal CustomUserDetails me
  ) {
    documentService.bulkDelete(request, me.getId());
    return ResponseEntity.ok(AppResponse.success(null));
  }

  @PostMapping("/{id}/copy")
  public ResponseEntity<AppResponse<DocumentResponse>> copy(
    @PathVariable UUID id,
    @Valid @RequestBody CopyDocumentRequest request,
    @AuthenticationPrincipal CustomUserDetails me
  ) {
    DocumentResponse response = documentService.copy(id, request, me.getId());
    return ResponseEntity.ok(AppResponse.success(response));
  }
}
