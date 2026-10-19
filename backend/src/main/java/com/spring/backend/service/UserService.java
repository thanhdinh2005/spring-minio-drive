package com.spring.backend.service;

import com.spring.backend.dto.auth.UserResponse;
import com.spring.backend.dto.auth.UpgradeResponse;
import com.spring.backend.dto.user.StorageUsageResponse;

import java.util.UUID;

public interface UserService {
  UserResponse getProfile(UUID id);
  StorageUsageResponse getStorageUsage(UUID id);
  UpgradeResponse upgradeToPremium(UUID id);
}
