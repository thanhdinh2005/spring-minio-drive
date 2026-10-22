package com.spring.backend.dto.admin;

import com.spring.backend.entity.enums.Status;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserStatusResponse {

  private UUID id;
  private Status status;
}
