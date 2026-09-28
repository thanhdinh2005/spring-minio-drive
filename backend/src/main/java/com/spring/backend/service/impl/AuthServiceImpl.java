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
import com.spring.backend.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {
  private final AuthenticationManager authenticationManager;
  private final JwtService jwtService;


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

      CustomUserDetails userDetails =
        (CustomUserDetails) authentication.getPrincipal();

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
}
