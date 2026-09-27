package com.spring.backend.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.spring.backend.common.AppResponse;
import com.spring.backend.dto.folder.FolderCreateDto;
import com.spring.backend.dto.folder.FolderUpdateDto;
import com.spring.backend.security.CustomUserDetails;
import com.spring.backend.service.FolderService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/folder")
@RequiredArgsConstructor
public class FolderController {
    private final FolderService service;

    @GetMapping()
    public ResponseEntity<?> findAllByOwnerIdAndParentFolderId(
            @RequestParam(value = "pageNo", defaultValue = "0") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "sortBy", defaultValue = "updatedAt") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir,
            @RequestParam(value = "parentFolderId", required = true) UUID parentFolderId,
            @AuthenticationPrincipal CustomUserDetails me
    ) {
        return ResponseEntity.ok(AppResponse.success(service.findAllByOwnerIdAndParentFolderId(pageNo, pageSize, sortBy, sortDir, me.getId(), parentFolderId)));
    }

    @GetMapping()
    public ResponseEntity<?> findAllByOwnerIdInRoot(
            @RequestParam(value = "pageNo", defaultValue = "0") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "sortBy", defaultValue = "updatedAt") String sortBy,
            @RequestParam(value = "sortDỉ", defaultValue = "desc") String sortDir,
            @AuthenticationPrincipal CustomUserDetails me
    ) {
        return ResponseEntity.ok(AppResponse.success(service.findAllByOwnerIdInRoot(pageNo, pageSize, sortBy, sortDir, me.getId())));
    }

    @GetMapping("/{folderId}")
    public ResponseEntity<?> findById(
            @PathVariable UUID folderId) {
        return ResponseEntity.ok(AppResponse.success(service.findById(folderId)));
    }

    @PostMapping
    public ResponseEntity<?> create(
        @RequestBody FolderCreateDto dto,
        @AuthenticationPrincipal CustomUserDetails me
    ){
        return ResponseEntity.ok(AppResponse.success(service.create(dto, me.getId())));
    }

    @PutMapping 
    public ResponseEntity<?> update(
        @RequestBody FolderUpdateDto dto,
        @AuthenticationPrincipal CustomUserDetails me
    ){
        return ResponseEntity.ok(AppResponse.success(service.update(dto, me.getId())));
    }

    @DeleteMapping 
    public ResponseEntity<?> deleteSoft(
        @RequestBody FolderUpdateDto dto,
        @AuthenticationPrincipal CustomUserDetails me
    ){
        return ResponseEntity.ok(AppResponse.success(service.update(dto, me.getId())));
    }
}
