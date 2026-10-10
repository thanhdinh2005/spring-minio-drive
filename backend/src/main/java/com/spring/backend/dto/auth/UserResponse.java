package com.spring.backend.dto.auth;

import com.spring.backend.entity.enums.Plan;
import com.spring.backend.entity.enums.Role;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class UserResponse {
  private UUID id;
  private String email;
  private String displayName;
  private Role role;
  private Plan plan;
}
