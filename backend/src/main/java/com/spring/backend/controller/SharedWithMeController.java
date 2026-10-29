package com.spring.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.spring.backend.common.AppResponse;
import com.spring.backend.dto.sharing.SharedWithMeResponse;
import com.spring.backend.security.CustomUserDetails;
import com.spring.backend.service.SharingService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/shared-with-me")
public class SharedWithMeController {

    private final SharingService sharingService;

    @GetMapping
    public ResponseEntity<AppResponse<List<SharedWithMeResponse>>> getSharedWithMe(
            @AuthenticationPrincipal CustomUserDetails me
    ) {
        List<SharedWithMeResponse> result =
                sharingService.getSharedWithMe(me.getId());

        return ResponseEntity.ok(
                AppResponse.success(result)
        );
    }
}