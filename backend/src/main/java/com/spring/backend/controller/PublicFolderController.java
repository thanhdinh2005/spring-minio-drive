package com.spring.backend.controller;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.spring.backend.common.AppResponse;
import com.spring.backend.dto.document.PresignedUrlResponse;
import com.spring.backend.dto.folder.FolderContentsResponse;
import com.spring.backend.service.DocumentService;
import com.spring.backend.service.SharingService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/public/folders")
public class PublicFolderController {

    private final SharingService sharingService;
    private final DocumentService documentService;

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

    // Duyệt subfolder trong cây của public link bằng chính token gốc
    @GetMapping("/{token}/folders/{folderId}")
    public ResponseEntity<AppResponse<FolderContentsResponse>> getSubFolderContents(
            @PathVariable("token") String token,
            @PathVariable("folderId") UUID folderId
    ) {
        FolderContentsResponse result =
                sharingService.getPublicSubFolderContents(token, folderId);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AppResponse.success(result));
    }

    // Cấp presigned URL mới khi URL nhúng trong contents đã hết hạn
    @GetMapping("/{token}/documents/{documentId}/download-url")
    public ResponseEntity<AppResponse<PresignedUrlResponse>> getDocumentDownloadUrl(
            @PathVariable("token") String token,
            @PathVariable("documentId") UUID documentId
    ) {
        UUID publicRootId = sharingService.getPublicFolderContents(token).getFolder().getId();
        PresignedUrlResponse result = documentService.getPublicDownloadUrl(documentId, publicRootId);


        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AppResponse.success(result));
    }
}
