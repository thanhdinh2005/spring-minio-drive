package com.spring.backend.dto.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TokenResponse {
  private String accessToken;
  private Long expiredIn;
}
