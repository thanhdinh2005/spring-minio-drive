package com.spring.backend.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

import com.spring.backend.common.AppResponse;
import com.spring.backend.dto.folder.FolderCreateDto;
import com.spring.backend.dto.folder.FolderUpdateDto;
import com.spring.backend.security.CustomUserDetails;
import com.spring.backend.service.DocumentService;
import com.spring.backend.service.FolderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;
    private final DocumentService documentService;

    /** 2.1 POST /api/folders */
    @PostMapping
    public ResponseEntity<?> create(
            @Valid @RequestBody FolderCreateDto dto,
            @AuthenticationPrincipal CustomUserDetails me) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AppResponse.success(folderService.create(dto, me.getId())));
    }

    /** 2.2 GET /api/folders/{id}/contents */
    @GetMapping("/{id}/contents")
    public ResponseEntity<?> getContents(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails me) {
        return ResponseEntity.ok(AppResponse.success(folderService.getContents(id, me.getId())));
    }

    /** 2.3 POST /api/folders/{id}/documents */
    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails me) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AppResponse.success(documentService.upload(id, file, me.getId())));
    }

    /** 2.6 PATCH /api/folders/{id} */
    @PatchMapping("/{id}")
    public ResponseEntity<?> patch(
            @PathVariable UUID id,
            @RequestBody FolderUpdateDto dto,
            @AuthenticationPrincipal CustomUserDetails me) {
        return ResponseEntity.ok(AppResponse.success(folderService.patch(id, dto, me.getId())));
    }

    /** 2.7 DELETE /api/folders/{id} — xóa cứng */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteHard(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails me) {
        folderService.deleteHard(id, me.getId());
        return ResponseEntity.ok(AppResponse.success(null));
    }
}
