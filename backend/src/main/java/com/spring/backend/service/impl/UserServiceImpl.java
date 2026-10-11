package com.spring.backend.service.impl;

import com.spring.backend.dto.auth.UserResponse;
import com.spring.backend.dto.user.StorageUsageResponse;
import com.spring.backend.entity.User;
import com.spring.backend.exception.AppException;
import com.spring.backend.exception.ErrorCode;
import com.spring.backend.repository.DocumentRepository;
import com.spring.backend.repository.UserRepository;
import com.spring.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {
  private final UserRepository userRepository;
  private final DocumentRepository documentRepository;

  @Override
  public UserResponse getProfile(UUID id) {
    User user = userRepository.findById(id)
      .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

    return UserResponse.builder()
      .id(user.getId())
      .email(user.getEmail())
      .displayName(user.getDisplayName())
      .role(user.getRole())
      .plan(user.getPlan())
      .build();
  }

  @Override
  public StorageUsageResponse getStorageUsage(UUID id) {
    User user = userRepository.findById(id)
      .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

    Long storageUsed = documentRepository.sumSizeByOwnerId(id);

    return StorageUsageResponse.builder()
      .storageUsed(storageUsed != null ? storageUsed : 0L)
      .storageLimit(user.getStorageLimit())
      .plan(user.getPlan())
      .build();
  }
}
