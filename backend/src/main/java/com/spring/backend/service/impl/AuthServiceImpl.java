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
import com.spring.backend.service.impl.FirebaseTokenVerifier.FirebaseUserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
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
  private final FirebaseTokenVerifier firebaseTokenVerifier;
  private final EmailService emailService;

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final long VERIFY_TOKEN_TTL_HOURS = 24;

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

      if (!Boolean.TRUE.equals(user.getEmailVerified())) {
        throw new AppException(ErrorCode.EMAIL_NOT_VERIFIED);
      }

      String token = jwtService.generateAccessToken(user);
      Long expiredIn = jwtService.getExpirationSeconds();

      return TokenResponse.builder()
          .accessToken(token)
          .expiredIn(expiredIn)
          .build();

    } catch (BadCredentialsException e) {
      // Handle Wrong password or email
      throw new AppException(ErrorCode.UNAUTHORIZED);
    } catch (DisabledException | LockedException e) {
      // Account bị khóa (status != ACTIVE) -> USER_LOCKED
      throw new AppException(ErrorCode.USER_LOCKED);
    }
  }

  @Transactional
  @Override
  public TokenResponse loginWithFirebase(String idToken) {
    FirebaseUserInfo firebaseUser = firebaseTokenVerifier.verify(idToken);

    String email = firebaseUser.email().toLowerCase();
    User existing = userRepository.findByEmail(email).orElse(null);
    User user;

    if (existing == null) {
      // User mới từ Google: email đã được Google verify sẵn
      user = userRepository.save(User.builder()
          .email(email)
          .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
          .displayName(firebaseUser.name() != null ? firebaseUser.name() : firebaseUser.email())
          .role(Role.USER)
          .status(Status.ACTIVE)
          .plan(Plan.FREE)
          .storageUsed(0L)
          .storageLimit(104857600L)
          .emailVerified(true)
          .build());
    } else {
      user = existing;
      if (user.getStatus() != Status.ACTIVE) {
        throw new AppException(ErrorCode.USER_LOCKED);
      }
      if (!Boolean.TRUE.equals(user.getEmailVerified())) {
        // Google chứng minh sở hữu email -> chủ email thật chiếm lại account:
        // verify luôn và reset password để đá kẻ đăng ký trước ra ngoài
        user.setEmailVerified(true);
        user.setEmailVerifyToken(null);
        user.setEmailVerifyTokenExpiry(null);
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user = userRepository.save(user);
      }
    }

    String token = jwtService.generateAccessToken(user);
    Long expiredIn = jwtService.getExpirationSeconds();

    return TokenResponse.builder()
        .accessToken(token)
        .expiredIn(expiredIn)
        .build();
  }

  @Transactional
  @Override
  public RegisterResponse register(RegisterRequest request) {
      String email = request.getEmail().trim().toLowerCase();

      if (userRepository.existsByEmail(email)) {
        throw new AppException(ErrorCode.USER_ALREADY_EXISTS);
      }

      String rawToken = generateVerifyToken();

      User user = User.builder()
        .email(email)
        .passwordHash(passwordEncoder.encode(request.getPassword()))
        .displayName(request.getDisplayName())
        .role(Role.USER)
        .status(Status.ACTIVE)
        .plan(Plan.FREE)
        .storageUsed(0L)
        .storageLimit(104857600L)
        .emailVerified(false)
        .emailVerifyToken(hashToken(rawToken))
        .emailVerifyTokenExpiry(Instant.now().plus(VERIFY_TOKEN_TTL_HOURS, ChronoUnit.HOURS))
        .build();

      User savedUser;
      try {
        savedUser = userRepository.save(user);
      } catch (DataIntegrityViolationException e) {
        // Race condition: 2 request đăng ký cùng email đồng thời
        throw new AppException(ErrorCode.USER_ALREADY_EXISTS);
      }

      emailService.sendVerificationEmail(savedUser.getEmail(), rawToken);

      return RegisterResponse.builder()
        .id(savedUser.getId())
        .email(savedUser.getEmail())
        .displayName(savedUser.getDisplayName())
        .plan(savedUser.getPlan())
        .build();
  }

  @Transactional
  @Override
  public void verifyEmail(String token) {
    User user = userRepository.findByEmailVerifyToken(hashToken(token))
        .orElseThrow(() -> new AppException(ErrorCode.INVALID_VERIFY_TOKEN));

    Instant expiry = user.getEmailVerifyTokenExpiry();
    if (expiry == null || expiry.isBefore(Instant.now())) {
      throw new AppException(ErrorCode.INVALID_VERIFY_TOKEN);
    }

    user.setEmailVerified(true);
    user.setEmailVerifyToken(null);
    user.setEmailVerifyTokenExpiry(null);
    userRepository.save(user);
  }

  @Transactional
  @Override
  public void resendVerification(String email) {
    // Luôn trả success kể cả email không tồn tại — tránh lộ email đã đăng ký
    userRepository.findByEmail(email.trim().toLowerCase()).ifPresent(user -> {
      if (Boolean.TRUE.equals(user.getEmailVerified())) {
        return;
      }

      String rawToken = generateVerifyToken();
      user.setEmailVerifyToken(hashToken(rawToken));
      user.setEmailVerifyTokenExpiry(Instant.now().plus(VERIFY_TOKEN_TTL_HOURS, ChronoUnit.HOURS));
      userRepository.save(user);

      emailService.sendVerificationEmail(user.getEmail(), rawToken);
    });
  }

  private String generateVerifyToken() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  private String hashToken(String token) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
