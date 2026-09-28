package com.spring.backend.controller;

import com.spring.backend.common.AppResponse;
import com.spring.backend.dto.auth.LoginRequest;
import com.spring.backend.dto.auth.TokenResponse;
import com.spring.backend.dto.auth.UserDto;
import com.spring.backend.service.AuthService;
import com.spring.backend.service.TestLongService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
  private final AuthService authService;
  private final TestLongService testLongService;

  @GetMapping("/{userId}")
  public ResponseEntity<AppResponse<UserDto>> getUserId(
    @PathVariable UUID userId
  ) {
    return ResponseEntity.ok(AppResponse.success(testLongService.getUserById(userId)));
  }

  @PostMapping("/login")
  public ResponseEntity<AppResponse<TokenResponse>> login(
    @Valid @RequestBody LoginRequest request
    ) {
    return ResponseEntity.ok(AppResponse.success(authService.login(request)));
  }
}
