package com.spring.backend.service.impl;

import com.spring.backend.dto.auth.UpgradeResponse;
import com.spring.backend.dto.auth.UserResponse;
import com.spring.backend.dto.user.StorageUsageResponse;
import com.spring.backend.entity.User;
import com.spring.backend.entity.enums.Plan;
import com.spring.backend.entity.enums.Role;
import com.spring.backend.entity.enums.Status;
import com.spring.backend.exception.AppException;
import com.spring.backend.exception.ErrorCode;
import com.spring.backend.repository.DocumentRepository;
import com.spring.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

  @InjectMocks
  private UserServiceImpl userService;

  @Mock
  private UserRepository userRepository;

  @Mock
  private DocumentRepository documentRepository;

  private UUID userId;
  private User mockUser;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    mockUser = User.builder()
      .id(userId)
      .email("user@example.com")
      .displayName("User Name")
      .role(Role.USER)
      .status(Status.ACTIVE)
      .plan(Plan.FREE)
      .storageUsed(999999L) // intentionally different from documents sum to verify it's not read from user.storageUsed
      .storageLimit(104857600L)
      .build();
  }

  @Nested
  @DisplayName("getStorageUsage")
  class GetStorageUsageTests {

    @Test
    @DisplayName("Thành công - tính storage_used trực tiếp bằng SUM(documents.size), không đọc từ user.storageUsed")
    void getStorageUsage_Success() {
      // Given
      Long calculatedStorage = 52428800L;
      given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));
      given(documentRepository.sumSizeByOwnerId(userId)).willReturn(calculatedStorage);

      // When
      StorageUsageResponse response = userService.getStorageUsage(userId);

      // Then
      assertThat(response).isNotNull();
      assertThat(response.getStorageUsed()).isEqualTo(calculatedStorage);
      assertThat(response.getStorageUsed()).isNotEqualTo(mockUser.getStorageUsed());
      assertThat(response.getStorageLimit()).isEqualTo(104857600L);
      assertThat(response.getPlan()).isEqualTo(Plan.FREE);

      verify(userRepository).findById(userId);
      verify(documentRepository).sumSizeByOwnerId(userId);
    }

    @Test
    @DisplayName("Thành công - khi user chưa có file nào (SUM trả về 0 hoặc null) -> storageUsed = 0")
    void getStorageUsage_NoDocuments_ReturnsZero() {
      // Given
      given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));
      given(documentRepository.sumSizeByOwnerId(userId)).willReturn(0L);

      // When
      StorageUsageResponse response = userService.getStorageUsage(userId);

      // Then
      assertThat(response).isNotNull();
      assertThat(response.getStorageUsed()).isEqualTo(0L);
      assertThat(response.getStorageLimit()).isEqualTo(104857600L);
      assertThat(response.getPlan()).isEqualTo(Plan.FREE);
    }

    @Test
    @DisplayName("Thất bại - không tìm thấy user -> throw AppException USER_NOT_FOUND")
    void getStorageUsage_UserNotFound_ThrowsException() {
      // Given
      given(userRepository.findById(userId)).willReturn(Optional.empty());

      // When & Then
      assertThatThrownBy(() -> userService.getStorageUsage(userId))
        .isInstanceOf(AppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("getProfile")
  class GetProfileTests {

    @Test
    @DisplayName("Thành công trả về UserResponse")
    void getProfile_Success() {
      given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));

      UserResponse response = userService.getProfile(userId);

      assertThat(response).isNotNull();
      assertThat(response.getId()).isEqualTo(userId);
      assertThat(response.getEmail()).isEqualTo("user@example.com");
    }
  }

  @Nested
  @DisplayName("upgradeToPremium")
  class UpgradeToPremiumTests {

    @Test
    @DisplayName("Nâng cấp thành công lên PREMIUM với storageLimit 1073741824")
    void upgradeToPremium_Success() {
      given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));

      UpgradeResponse response = userService.upgradeToPremium(userId);

      assertThat(response).isNotNull();
      assertThat(response.getPlan()).isEqualTo(Plan.PREMIUM);
      assertThat(response.getStorageLimit()).isEqualTo(1073741824L);
      assertThat(mockUser.getPlan()).isEqualTo(Plan.PREMIUM);
      assertThat(mockUser.getStorageLimit()).isEqualTo(1073741824L);

      verify(userRepository).save(mockUser);
    }
  }

}
