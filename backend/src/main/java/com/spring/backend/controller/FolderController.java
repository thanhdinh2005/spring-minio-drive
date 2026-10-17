package com.spring.backend.controller;

import com.spring.backend.common.AppResponse;
import com.spring.backend.dto.folder.FolderCreateRequest;
import com.spring.backend.dto.folder.FolderDto;
import com.spring.backend.security.CustomUserDetails;
import com.spring.backend.service.FolderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    @PostMapping("")
    public ResponseEntity<AppResponse<FolderDto>> createFolder(
            @RequestBody @Valid FolderCreateRequest request
    ) {
        UUID userId = ((CustomUserDetails) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal()).getId();

        FolderDto dto = folderService.createFolder(request, userId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AppResponse.created(dto));
    }
}
