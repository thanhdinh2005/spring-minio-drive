package com.spring.backend.controller;

import com.spring.backend.common.AppResponse;
import com.spring.backend.dto.auth.UserDto;
import com.spring.backend.service.TestLongService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/public/test")
@RequiredArgsConstructor
public class AuthController {

  private final TestLongService testLongService;

  @GetMapping("/{userId}")
  public ResponseEntity<AppResponse<UserDto>> getUserId(
    @PathVariable UUID userId
  ) {
    return ResponseEntity.ok(AppResponse.success(testLongService.getUserById(userId)));
  }
}
