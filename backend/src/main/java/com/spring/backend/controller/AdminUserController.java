package com.spring.backend.controller;

import com.spring.backend.common.AppResponse;
import com.spring.backend.common.PageResponse;
import com.spring.backend.dto.auth.AdminUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import com.spring.backend.service.UserService;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


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

}
