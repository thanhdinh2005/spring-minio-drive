package com.spring.backend.dto.auth;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserDto {
  private UUID id;
  private String email;
  private String displayName;

}
