package com.spring.backend.dto.test;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TestRequest {
  @Email(message = "Email must be valid")
  @Size(min = 5, max = 20, message = "Email cannot exceed 20 characters and at least 5 characters")
  private String email;
}
