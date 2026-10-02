package com.spring.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.backend.dto.auth.RegisterRequest;
import com.spring.backend.dto.auth.RegisterResponse;
import com.spring.backend.entity.enums.Plan;
import com.spring.backend.exception.AppException;
import com.spring.backend.exception.ErrorCode;
import com.spring.backend.exception.GlobalExceptionHandler;
import com.spring.backend.service.AuthService;
import com.spring.backend.service.TestLongService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController Unit Test")
public class AuthControllerTest {

    @InjectMocks
    private AuthController authController;

    @Mock private AuthService authService;
    @Mock private TestLongService testLongService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private RegisterRequest validRegisterRequest;
    private RegisterResponse mockRegisterResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        validRegisterRequest = RegisterRequest.builder()
                .email("test@email.com")
                .password("password123")
                .displayName("Test User")
                .build();

        mockRegisterResponse = RegisterResponse.builder()
                .id(UUID.randomUUID())
                .email("test@email.com")
                .displayName("Test User")
                .plan(Plan.FREE)
                .build();
    }

    @Nested
    @DisplayName("register")
    class RegisterTests {

        @Test
        @DisplayName("Đăng ký thành công → 200 OK")
        void shouldReturn200AndRegisterResponse_whenRequestIsValid() throws Exception {
            // Given
            given(authService.register(any(RegisterRequest.class))).willReturn(mockRegisterResponse);

            // When & Then
            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRegisterRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.id").value(mockRegisterResponse.getId().toString()))
                    .andExpect(jsonPath("$.data.email").value("test@email.com"))
                    .andExpect(jsonPath("$.data.displayName").value("Test User"))
                    .andExpect(jsonPath("$.data.plan").value(Plan.FREE.name()));

            verify(authService).register(any(RegisterRequest.class));
        }

        @Test
        @DisplayName("Đăng ký thất bại do email đã tồn tại → 409 Conflict")
        void shouldReturn409Conflict_whenEmailAlreadyExists() throws Exception {
            // Given
            given(authService.register(any(RegisterRequest.class)))
                    .willThrow(new AppException(ErrorCode.USER_ALREADY_EXISTS));

            // When & Then
            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRegisterRequest)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.code").value("ERR_USER_002"))
                    .andExpect(jsonPath("$.message").value("Email already exists"));

            verify(authService).register(any(RegisterRequest.class));
        }

        @Test
        @DisplayName("Đăng ký thất bại do email không hợp lệ → 400 Bad Request")
        void shouldReturn400BadRequest_whenEmailIsInvalid() throws Exception {
            // Given
            RegisterRequest invalidEmailRequest = RegisterRequest.builder()
                    .email("invalid-email")
                    .password("password123")
                    .displayName("Test User")
                    .build();

            // When & Then
            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidEmailRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("ERR_REQ_001"))
                    .andExpect(jsonPath("$.message").value(containsString("email")));

            // Service không được gọi vì validation fail tại controller
            verify(authService, never()).register(any());
        }

        @Test
        @DisplayName("Đăng ký thất bại do password không hợp lệ (quá ngắn < 5 ký tự) → 400 Bad Request")
        void shouldReturn400BadRequest_whenPasswordIsInvalid() throws Exception {
            // Given
            RegisterRequest invalidPasswordRequest = RegisterRequest.builder()
                    .email("test@email.com")
                    .password("123") // Dưới 5 ký tự
                    .displayName("Test User")
                    .build();

            // When & Then
            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidPasswordRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("ERR_REQ_001"))
                    .andExpect(jsonPath("$.message").value(containsString("password")));

            verify(authService, never()).register(any());
        }

        @Test
        @DisplayName("Đăng ký thất bại do displayName rỗng → 400 Bad Request")
        void shouldReturn400BadRequest_whenDisplayNameIsBlank() throws Exception {
            // Given
            RegisterRequest blankDisplayNameRequest = RegisterRequest.builder()
                    .email("test@email.com")
                    .password("password123")
                    .displayName("") // Rỗng
                    .build();

            // When & Then
            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(blankDisplayNameRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("ERR_REQ_001"))
                    .andExpect(jsonPath("$.message").value(containsString("displayName")));

            verify(authService, never()).register(any());
        }
    }
}
