package com.spring.backend.controller;

import com.spring.backend.common.AppResponse;
import com.spring.backend.dto.folder.FolderDto;
import com.spring.backend.service.FolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/public/folders")
@RequiredArgsConstructor
public class FolderController {
    private final FolderService folderService;

    @GetMapping("/{id}")
    public ResponseEntity<AppResponse<FolderDto>> getFolderById(
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(AppResponse.success(folderService.getFolderById(id)));
    }
}
