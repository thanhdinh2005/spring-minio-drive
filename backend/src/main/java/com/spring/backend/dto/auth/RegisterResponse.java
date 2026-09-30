package com.spring.backend.dto.auth;

import com.spring.backend.entity.enums.Plan;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class RegisterResponse {
  private UUID id;
  private String email;
  private String displayName;
  private Plan plan;
}
