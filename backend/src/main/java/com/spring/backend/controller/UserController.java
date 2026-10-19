package com.spring.backend.controller;

import com.spring.backend.common.AppResponse;
import com.spring.backend.dto.auth.UpgradeResponse;
import com.spring.backend.dto.auth.UserResponse;
import com.spring.backend.dto.user.StorageUsageResponse;
import com.spring.backend.security.CustomUserDetails;
import com.spring.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @GetMapping("/me")
  public ResponseEntity<AppResponse<UserResponse>> getMyProfile(
    @AuthenticationPrincipal CustomUserDetails me
  ) {
    UserResponse response = userService.getProfile(me.getId());
    return ResponseEntity.ok(AppResponse.success(response));
  }

  @GetMapping("/me/storage")
  public ResponseEntity<AppResponse<StorageUsageResponse>> getMyStorage(
    @AuthenticationPrincipal CustomUserDetails me
  ) {
    StorageUsageResponse response = userService.getStorageUsage(me.getId());
    return ResponseEntity.ok(AppResponse.success(response));
  }

  @GetMapping("me/upgrade")
  public ResponseEntity<AppResponse<UpgradeResponse>> getMyPlan(
    @AuthenticationPrincipal CustomUserDetails me
  ) {
    UpgradeResponse response = userService.upgradeToPremium(me.getId());
    return ResponseEntity.ok(AppResponse.success(response));
  }
}
