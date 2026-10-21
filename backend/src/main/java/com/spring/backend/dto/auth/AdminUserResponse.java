package com.spring.backend.dto.auth;

import com.spring.backend.entity.enums.Plan;
import com.spring.backend.entity.enums.Status;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserResponse {
  private UUID id;
  private String email;
  private String displayName;
  private Status status;
  private Plan plan;
  private Instant createdAt;
}
