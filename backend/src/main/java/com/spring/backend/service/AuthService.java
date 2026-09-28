package com.spring.backend.service;

import com.spring.backend.dto.auth.LoginRequest;
import com.spring.backend.dto.auth.TokenResponse;

public interface AuthService {
  TokenResponse login(LoginRequest request);
}
