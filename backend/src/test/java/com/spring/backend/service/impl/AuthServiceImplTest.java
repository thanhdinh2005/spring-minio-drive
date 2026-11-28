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
import com.spring.backend.service.impl.FirebaseTokenVerifier.FirebaseUserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
  @Mock private FirebaseTokenVerifier firebaseTokenVerifier;
  @Mock private EmailService emailService;

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
      .emailVerified(true)
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

    @Test
    @DisplayName("should throw EMAIL_NOT_VERIFIED when email is not verified")
    void shouldThrowEmailNotVerified_whenEmailUnverified() {
      mockUser.setEmailVerified(false);
      CustomUserDetails userDetails = new CustomUserDetails(mockUser, null);
      Authentication authentication = mock(Authentication.class);

      given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
        .willReturn(authentication);
      given(authentication.getPrincipal()).willReturn(userDetails);

      assertThatThrownBy(() -> authService.login(loginRequest))
        .isInstanceOf(AppException.class)
        .extracting(e -> ((AppException) e).getErrorCode())
        .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);
    }

    @Test
    @DisplayName("should throw USER_LOCKED when account is disabled")
    void shouldThrowUserLocked_whenAccountDisabled() {
      given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
        .willThrow(new DisabledException("disabled"));

      assertThatThrownBy(() -> authService.login(loginRequest))
        .isInstanceOf(AppException.class)
        .extracting(e -> ((AppException) e).getErrorCode())
        .isEqualTo(ErrorCode.USER_LOCKED);
    }
  }

  @Nested
  @DisplayName("register")
  class RegisterTests {

    private RegisterRequest registerRequest() {
      return RegisterRequest.builder()
        .email("new@email.com")
        .password("password123")
        .displayName("New User")
        .build();
    }

    @Test
    @DisplayName("should create unverified user and send verification email")
    void shouldCreateUnverifiedUser_whenRegister() {
      given(userRepository.existsByEmail("new@email.com")).willReturn(false);
      given(passwordEncoder.encode("password123")).willReturn("hashed");
      given(userRepository.save(any(User.class)))
        .willAnswer(inv -> inv.getArgument(0));

      RegisterResponse response = authService.register(registerRequest());

      assertThat(response.getEmail()).isEqualTo("new@email.com");

      org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
      verify(userRepository).save(captor.capture());
      User saved = captor.getValue();
      assertThat(saved.getEmailVerified()).isFalse();
      assertThat(saved.getEmailVerifyToken()).isNotBlank();
      assertThat(saved.getEmailVerifyTokenExpiry()).isAfter(Instant.now());

      verify(emailService).sendVerificationEmail(eq("new@email.com"), anyString());
    }

    @Test
    @DisplayName("should throw USER_ALREADY_EXISTS when email exists")
    void shouldThrowAlreadyExists_whenEmailTaken() {
      given(userRepository.existsByEmail("new@email.com")).willReturn(true);

      assertThatThrownBy(() -> authService.register(registerRequest()))
        .isInstanceOf(AppException.class)
        .extracting(e -> ((AppException) e).getErrorCode())
        .isEqualTo(ErrorCode.USER_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("should normalize email to lowercase")
    void shouldNormalizeEmail_whenRegister() {
      RegisterRequest req = RegisterRequest.builder()
        .email("  New@Email.COM ")
        .password("password123")
        .displayName("New User")
        .build();

      given(userRepository.existsByEmail("new@email.com")).willReturn(false);
      given(passwordEncoder.encode("password123")).willReturn("hashed");
      given(userRepository.save(any(User.class)))
        .willAnswer(inv -> inv.getArgument(0));

      RegisterResponse response = authService.register(req);

      assertThat(response.getEmail()).isEqualTo("new@email.com");
    }
  }

  @Nested
  @DisplayName("verifyEmail")
  class VerifyEmailTests {

    @Test
    @DisplayName("should mark user verified and clear token")
    void shouldVerifyUser_whenTokenValid() {
      User user = User.builder()
        .email("test@email.com")
        .emailVerified(false)
        .emailVerifyToken("sometokenhash")
        .emailVerifyTokenExpiry(Instant.now().plus(1, ChronoUnit.HOURS))
        .build();
      given(userRepository.findByEmailVerifyToken(anyString()))
        .willReturn(Optional.of(user));

      authService.verifyEmail("raw-token");

      assertThat(user.getEmailVerified()).isTrue();
      assertThat(user.getEmailVerifyToken()).isNull();
      assertThat(user.getEmailVerifyTokenExpiry()).isNull();
      verify(userRepository).save(user);
    }

    @Test
    @DisplayName("should throw INVALID_VERIFY_TOKEN when token not found")
    void shouldThrowInvalidToken_whenNotFound() {
      given(userRepository.findByEmailVerifyToken(anyString()))
        .willReturn(Optional.empty());

      assertThatThrownBy(() -> authService.verifyEmail("bad"))
        .isInstanceOf(AppException.class)
        .extracting(e -> ((AppException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_VERIFY_TOKEN);
    }

    @Test
    @DisplayName("should throw INVALID_VERIFY_TOKEN when token expired")
    void shouldThrowInvalidToken_whenExpired() {
      User user = User.builder()
        .email("test@email.com")
        .emailVerified(false)
        .emailVerifyToken("sometokenhash")
        .emailVerifyTokenExpiry(Instant.now().minus(1, ChronoUnit.HOURS))
        .build();
      given(userRepository.findByEmailVerifyToken(anyString()))
        .willReturn(Optional.of(user));

      assertThatThrownBy(() -> authService.verifyEmail("raw-token"))
        .isInstanceOf(AppException.class)
        .extracting(e -> ((AppException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_VERIFY_TOKEN);
    }
  }

  @Nested
  @DisplayName("resendVerification")
  class ResendVerificationTests {

    @Test
    @DisplayName("should do nothing when email does not exist")
    void shouldDoNothing_whenEmailNotFound() {
      given(userRepository.findByEmail("ghost@email.com"))
        .willReturn(Optional.empty());

      authService.resendVerification("ghost@email.com");

      verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("should do nothing when user already verified")
    void shouldDoNothing_whenAlreadyVerified() {
      User user = User.builder().email("test@email.com").emailVerified(true).build();
      given(userRepository.findByEmail("test@email.com"))
        .willReturn(Optional.of(user));

      authService.resendVerification("test@email.com");

      verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("should regenerate token and send email when unverified")
    void shouldResend_whenUnverified() {
      User user = User.builder()
        .email("test@email.com")
        .emailVerified(false)
        .build();
      given(userRepository.findByEmail("test@email.com"))
        .willReturn(Optional.of(user));
      given(userRepository.save(any(User.class)))
        .willAnswer(inv -> inv.getArgument(0));

      authService.resendVerification("test@email.com");

      assertThat(user.getEmailVerifyToken()).isNotBlank();
      assertThat(user.getEmailVerifyTokenExpiry()).isAfter(Instant.now());
      verify(emailService).sendVerificationEmail(eq("test@email.com"), anyString());
    }
  }

  @Nested
  @DisplayName("loginWithFirebase")
  class LoginWithFirebaseTests {

    private void stubTokenResponse() {
      given(jwtService.generateAccessToken(any(User.class))).willReturn("access-token");
      given(jwtService.getExpirationSeconds()).willReturn(3600L);
    }

    @Test
    @DisplayName("should create verified user on first Google login")
    void shouldCreateVerifiedUser_whenNew() {
      given(firebaseTokenVerifier.verify("id-token"))
        .willReturn(new FirebaseUserInfo("google@email.com", "Google User"));
      given(userRepository.findByEmail("google@email.com"))
        .willReturn(Optional.empty());
      given(passwordEncoder.encode(anyString())).willReturn("randomhash");
      given(userRepository.save(any(User.class)))
        .willAnswer(inv -> inv.getArgument(0));
      stubTokenResponse();

      TokenResponse response = authService.loginWithFirebase("id-token");

      assertThat(response.getAccessToken()).isEqualTo("access-token");

      org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
      verify(userRepository).save(captor.capture());
      assertThat(captor.getValue().getEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("should reclaim unverified account: verify + reset password")
    void shouldReclaimUnverifiedAccount_whenGoogleLogin() {
      User existing = User.builder()
        .email("victim@email.com")
        .passwordHash("attacker-hash")
        .emailVerified(false)
        .status(Status.ACTIVE)
        .build();
      given(firebaseTokenVerifier.verify("id-token"))
        .willReturn(new FirebaseUserInfo("victim@email.com", "Victim"));
      given(userRepository.findByEmail("victim@email.com"))
        .willReturn(Optional.of(existing));
      given(passwordEncoder.encode(anyString())).willReturn("new-random-hash");
      given(userRepository.save(any(User.class)))
        .willAnswer(inv -> inv.getArgument(0));
      stubTokenResponse();

      authService.loginWithFirebase("id-token");

      assertThat(existing.getEmailVerified()).isTrue();
      assertThat(existing.getPasswordHash()).isEqualTo("new-random-hash");
      assertThat(existing.getEmailVerifyToken()).isNull();
    }

    @Test
    @DisplayName("should throw USER_LOCKED when account is not active")
    void shouldThrowUserLocked_whenNotActive() {
      User existing = User.builder()
        .email("locked@email.com")
        .status(Status.LOCKED)
        .emailVerified(true)
        .build();
      given(firebaseTokenVerifier.verify("id-token"))
        .willReturn(new FirebaseUserInfo("locked@email.com", "Locked"));
      given(userRepository.findByEmail("locked@email.com"))
        .willReturn(Optional.of(existing));

      assertThatThrownBy(() -> authService.loginWithFirebase("id-token"))
        .isInstanceOf(AppException.class)
        .extracting(e -> ((AppException) e).getErrorCode())
        .isEqualTo(ErrorCode.USER_LOCKED);
    }
  }

  // TODO: Nested class chủ yếu hỗ trợ việc kiểm tra, 1 class tương đương 1 hàm và các case trong service
}
