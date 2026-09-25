package com.spring.backend.service.impl;

import com.spring.backend.dto.auth.UserDto;
import com.spring.backend.entity.User;
import com.spring.backend.exception.AppException;
import com.spring.backend.exception.ErrorCode;
import com.spring.backend.repository.UserRepository;
import com.spring.backend.service.TestLongService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class TestLongServiceImpl implements TestLongService {

  private final UserRepository userRepository;

  @Override
  public UserDto getUserById(UUID id) {
    User user = userRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    return UserDto.builder()
      .id(user.getId())
      .displayName(user.getDisplayName())
      .email(user.getEmail())
      .build();
  }
}
