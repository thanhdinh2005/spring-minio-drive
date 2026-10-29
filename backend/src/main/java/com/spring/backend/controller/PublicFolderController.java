package com.spring.backend.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.spring.backend.common.AppResponse;
import com.spring.backend.dto.folder.FolderContentsResponse;
import com.spring.backend.service.SharingService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/public/folders")
public class PublicFolderController {

    private final SharingService sharingService;

    @GetMapping("/{token}")
    public ResponseEntity<AppResponse<FolderContentsResponse>> getContents(
            @PathVariable("token") String token
    ) {
        FolderContentsResponse result =
                sharingService.getPublicFolderContents(token);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AppResponse.success(result));
    }
}