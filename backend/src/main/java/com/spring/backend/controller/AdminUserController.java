package com.spring.backend.controller;

import com.spring.backend.common.AppResponse;
import com.spring.backend.common.PageResponse;
import com.spring.backend.dto.admin.UpdateUserStatusRequest;
import com.spring.backend.dto.admin.UpdateUserStatusResponse;
import com.spring.backend.dto.auth.AdminUserResponse;
import com.spring.backend.service.UserService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

  private final UserService userService;

  @GetMapping
  public ResponseEntity<AppResponse<PageResponse<AdminUserResponse>>> getAllUsers(
    @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
    Pageable pageable
  ) {
    PageResponse<AdminUserResponse> response = userService.getAllUsers(pageable);
    return ResponseEntity.ok(AppResponse.success(response));
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasRole('ADMIN') or hasAuthority('ADMIN')")
  public ResponseEntity<AppResponse<UpdateUserStatusResponse>> updateUserStatus(
    @PathVariable UUID id,
    @Valid @RequestBody UpdateUserStatusRequest request
  ) {
    UpdateUserStatusResponse response = userService.updateUserStatus(id, request.getStatus());
    return ResponseEntity.ok(AppResponse.success(response));
  }
}
