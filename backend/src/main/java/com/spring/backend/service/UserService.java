package com.spring.backend.service;

import com.spring.backend.common.PageResponse;
import com.spring.backend.dto.auth.AdminUserResponse;
import org.springframework.data.domain.Pageable;
import com.spring.backend.dto.auth.UserResponse;
import com.spring.backend.dto.auth.UpgradeResponse;
import com.spring.backend.dto.user.StorageUsageResponse;
import com.spring.backend.dto.admin.UpdateUserStatusResponse;
import com.spring.backend.entity.enums.Status;

import java.util.UUID;

public interface UserService {
  UserResponse getProfile(UUID id);
  StorageUsageResponse getStorageUsage(UUID id);
  UpgradeResponse upgradeToPremium(UUID id);
  PageResponse<AdminUserResponse> getAllUsers(Pageable pageable);
  UpdateUserStatusResponse updateUserStatus(UUID id, Status status);
}
