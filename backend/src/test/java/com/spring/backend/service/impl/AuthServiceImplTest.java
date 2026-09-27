package com.spring.backend.service.impl;

import com.spring.backend.dto.auth.LoginRequest;
import com.spring.backend.dto.auth.TokenResponse;
import com.spring.backend.entity.User;
import com.spring.backend.entity.enums.Plan;
import com.spring.backend.entity.enums.Role;
import com.spring.backend.entity.enums.Status;
import com.spring.backend.exception.AppException;
import com.spring.backend.exception.ErrorCode;
import com.spring.backend.repository.UserRepository;
import com.spring.backend.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class AuthServiceImplTest {

  @InjectMocks
  private AuthServiceImpl authService;

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private AuthenticationManager authenticationManager;
  @Mock private JwtService jwtService;

  private User mockUser;
  private LoginRequest loginRequest;

  @BeforeEach
  void setUp() {
    mockUser = User.builder()
      .id(UUID.randomUUID())
      .email("test@email.com")
      .passwordHash("test-password")
      .displayName("test display name")
      .status(Status.ACTIVE)
      .plan(Plan.FREE)
      .role(Role.USER)
      .build();

    loginRequest = LoginRequest.builder()
      .email("test@email.com")
      .password("test-password")
      .build();
  }

  @Nested
  @DisplayName("login")
  class LoginTests {
    @Test
    @DisplayName("should return tokens when credentials are valid")
    void shouldReturnTokens_whenCredentialsValid() {
      CustomUserDetails userDetails = new CustomUserDetails(mockUser, null);
      Authentication authentication = mock(Authentication.class);

      given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
        .willReturn(authentication);
      given(authentication.getPrincipal()).willReturn(userDetails);
      given(jwtService.generateAccessToken(mockUser)).willReturn("access-token-value");

      TokenResponse response = authService.login(loginRequest);

      assertThat(response.getAccessToken()).isEqualTo("access-token-value");
    }

    // TODO: Thêm 1 vài case quan trọng cho phần unit test
  }

  // TODO: Nested class chủ yếu hỗ trợ việc kiểm tra, 1 class tương đương 1 hàm và các case trong service
}
