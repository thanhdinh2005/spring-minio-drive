package com.spring.backend.service;

import com.spring.backend.dto.auth.UserResponse;

import java.util.UUID;

public interface UserService {
  UserResponse getProfile(UUID id);
}
