package com.spring.backend.service;

import com.spring.backend.dto.auth.UserDto;

import java.util.UUID;

public interface TestLongService {
  UserDto getUserById(UUID id);
}
