package com.spring.backend.controller;

import com.spring.backend.common.AppResponse;
import com.spring.backend.dto.auth.*;
import com.spring.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
  private final AuthService authService;

  @PostMapping("/login")
  public ResponseEntity<AppResponse<TokenResponse>> login(
    @Valid @RequestBody LoginRequest request
    ) {
    return ResponseEntity.ok(AppResponse.success(authService.login(request)));
  }

  @PostMapping("/google")
  public ResponseEntity<AppResponse<TokenResponse>> loginWithGoogle(
    @Valid @RequestBody FirebaseLoginRequest request
    ) {
    return ResponseEntity.ok(AppResponse.success(authService.loginWithFirebase(request.getIdToken())));
  }

  @GetMapping("/verify-email")
  public ResponseEntity<AppResponse<Void>> verifyEmail(@RequestParam String token) {
    authService.verifyEmail(token);
    return ResponseEntity.ok(AppResponse.<Void>success(null));
  }

  @PostMapping("/resend-verification")
  public ResponseEntity<AppResponse<Void>> resendVerification(
    @Valid @RequestBody ResendVerificationRequest request
    ) {
    authService.resendVerification(request.getEmail());
    return ResponseEntity.ok(AppResponse.<Void>success(null));
  }

  @PostMapping("/register")
  public ResponseEntity<AppResponse<RegisterResponse>> register(
    @Valid @RequestBody RegisterRequest request
    ) {
    return ResponseEntity.ok(AppResponse.success(authService.register(request)));
  }
}
