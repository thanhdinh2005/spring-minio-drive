package com.spring.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.spring.backend.common.AppResponse;
import com.spring.backend.dto.folder.CreateFolderRequest;
import com.spring.backend.dto.folder.FolderResponse;
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
}
