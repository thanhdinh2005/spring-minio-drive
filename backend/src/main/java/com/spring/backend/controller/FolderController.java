package com.spring.backend.controller;

import java.util.List;
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
import org.springframework.web.bind.annotation.RestController;

import com.spring.backend.common.AppResponse;
import com.spring.backend.dto.folder.BulkDeleteRequest;
import com.spring.backend.dto.folder.BulkMoveRequest;
import com.spring.backend.dto.folder.CopyFolderRequest;
import com.spring.backend.dto.folder.CreateFolderRequest;
import com.spring.backend.dto.folder.FolderContentsResponse;
import com.spring.backend.dto.folder.FolderPathResponse;
import com.spring.backend.dto.folder.FolderResponse;
import com.spring.backend.dto.folder.FolderTreeResponse;
import com.spring.backend.dto.folder.UpdateFolderRequest;
import com.spring.backend.security.CustomUserDetails;
import com.spring.backend.service.FolderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/folders")
@RequiredArgsConstructor
public class FolderController {

  private final FolderService folderService;

  @PostMapping
  public ResponseEntity<AppResponse<FolderResponse>> create(
    @Valid @RequestBody CreateFolderRequest request,
    @AuthenticationPrincipal CustomUserDetails me
  ) {
    FolderResponse response = folderService.create(request, me.getId());
    return ResponseEntity.status(HttpStatus.CREATED)
      .body(AppResponse.success(response, HttpStatus.CREATED));
  }

  @GetMapping("/{id}")
  public ResponseEntity<AppResponse<FolderResponse>> getById(
    @PathVariable UUID id,
    @AuthenticationPrincipal CustomUserDetails me
  ) {
    FolderResponse response = folderService.getById(id, me.getId());
    return ResponseEntity.ok(AppResponse.success(response));
  }

  @GetMapping("/root")
  public ResponseEntity<AppResponse<List<FolderResponse>>> getRootFolders(
    @AuthenticationPrincipal CustomUserDetails me
  ) {
    List<FolderResponse> response = folderService.getRootFolders(me.getId());
    return ResponseEntity.ok(AppResponse.success(response));
  }

  @GetMapping("/{id}/contents")
  public ResponseEntity<AppResponse<FolderContentsResponse>> getContents(
    @PathVariable UUID id,
    @RequestParam(value = "pageNo", defaultValue = "0") int pageNo,
    @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
    @RequestParam(value = "sortBy", defaultValue = "updatedAt") String sortBy,
    @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir,
    @AuthenticationPrincipal CustomUserDetails me
  ) {
    FolderContentsResponse response = folderService.getContents(
      id, me.getId(), pageNo, pageSize, sortBy, sortDir);
    return ResponseEntity.ok(AppResponse.success(response));
  }

  @GetMapping("/{id}/path")
  public ResponseEntity<AppResponse<FolderPathResponse>> getPath(
    @PathVariable UUID id,
    @AuthenticationPrincipal CustomUserDetails me
  ) {
    FolderPathResponse response = folderService.getPath(id, me.getId());
    return ResponseEntity.ok(AppResponse.success(response));
  }

  @GetMapping("/tree")
  public ResponseEntity<AppResponse<FolderTreeResponse>> getTree(
    @RequestParam(value = "parentFolderName", required = false) String parentFolderName,
    @AuthenticationPrincipal CustomUserDetails me
  ) {
    FolderTreeResponse response = folderService.getTree(me.getId(), parentFolderName);
    return ResponseEntity.ok(AppResponse.success(response));
  }

  @PatchMapping("/{id}")
  public ResponseEntity<AppResponse<FolderResponse>> update(
    @PathVariable UUID id,
    @Valid @RequestBody UpdateFolderRequest request,
    @AuthenticationPrincipal CustomUserDetails me
  ) {
    FolderResponse response = folderService.update(id, request, me.getId());
    return ResponseEntity.ok(AppResponse.success(response));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<AppResponse<Void>> deleteHard(
    @PathVariable UUID id,
    @AuthenticationPrincipal CustomUserDetails me
  ) {
    folderService.deleteHard(id, me.getId());
    return ResponseEntity.ok(AppResponse.success(null));
  }

  @PostMapping("/bulk-delete")
  public ResponseEntity<AppResponse<Void>> bulkDelete(
    @Valid @RequestBody BulkDeleteRequest request,
    @AuthenticationPrincipal CustomUserDetails me
  ) {
    folderService.bulkDelete(request, me.getId());
    return ResponseEntity.ok(AppResponse.success(null));
  }

  @PostMapping("/{id}/copy")
  public ResponseEntity<AppResponse<FolderResponse>> copy(
    @PathVariable UUID id,
    @Valid @RequestBody CopyFolderRequest request,
    @AuthenticationPrincipal CustomUserDetails me
  ) {
    FolderResponse response = folderService.copy(id, request, me.getId());
    return ResponseEntity.ok(AppResponse.success(response));
  }

  @PostMapping("/bulk-move")
  public ResponseEntity<AppResponse<Void>> bulkMove(
    @Valid @RequestBody BulkMoveRequest request,
    @AuthenticationPrincipal CustomUserDetails me
  ) {
    folderService.bulkMove(request, me.getId());
    return ResponseEntity.ok(AppResponse.success(null));
  }
}
