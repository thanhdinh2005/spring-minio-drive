package com.spring.backend.service.impl;

import com.spring.backend.dto.auth.LoginRequest;
import com.spring.backend.dto.auth.RegisterRequest;
import com.spring.backend.dto.auth.RegisterResponse;
import com.spring.backend.dto.auth.TokenResponse;
import com.spring.backend.entity.User;
import com.spring.backend.entity.enums.Plan;
import com.spring.backend.entity.enums.Role;
import com.spring.backend.entity.enums.Status;
import com.spring.backend.exception.AppException;
import com.spring.backend.exception.ErrorCode;
import com.spring.backend.repository.UserRepository;
import com.spring.backend.security.CustomUserDetails;
import com.spring.backend.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {
  private final AuthenticationManager authenticationManager;
  private final JwtService jwtService;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Override
  public TokenResponse login(LoginRequest request) {
    try {
      // Use authentication manager to authenticate
      Authentication authentication = authenticationManager.authenticate(
          new UsernamePasswordAuthenticationToken(
              request.getEmail(),
              request.getPassword()
          )
      );

      CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

      User user = userDetails.getUser();

      String token = jwtService.generateAccessToken(user);
      Long expiredIn = jwtService.getExpirationSeconds();

      return TokenResponse.builder()
          .accessToken(token)
          .expiredIn(expiredIn)
          .build();

    } catch (BadCredentialsException e) {
      // Handle Wrong password or email
      throw new AppException(ErrorCode.UNAUTHORIZED);
    }
  }

  @Transactional
  @Override
  public RegisterResponse register(RegisterRequest request) {
      if (userRepository.existsByEmail(request.getEmail())) {
        throw new AppException(ErrorCode.USER_ALREADY_EXISTS);
      }

      User user = User.builder()
        .email(request.getEmail())
        .passwordHash(passwordEncoder.encode(request.getPassword()))
        .displayName(request.getDisplayName())
        .role(Role.USER)
        .status(Status.ACTIVE)
        .plan(Plan.FREE)
        .storageUsed(0L)
        .storageLimit(104857600L)
        .build();

      User savedUser = userRepository.save(user);

      return RegisterResponse.builder()
        .id(savedUser.getId())
        .email(savedUser.getEmail())
        .displayName(savedUser.getDisplayName())
        .plan(savedUser.getPlan())
        .build();
  }
}
