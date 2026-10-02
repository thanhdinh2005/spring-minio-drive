package com.spring.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RegisterRequest {
  @Email(message = "Email must be valid")
  @Size(min = 5, max = 20, message = "Email cannot exceed 20 characters and at least 5 characters")
  private String email;

  @NotBlank(message = "Password is required")
  @Size(min = 5, max = 20, message = "Password cannot exceed 20 characters and at least 5 characters")
  private String password;

  @NotBlank(message = "DisplayName is required")
  private String displayName;
}
