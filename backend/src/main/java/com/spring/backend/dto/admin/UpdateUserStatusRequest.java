package com.spring.backend.dto.admin;

import com.spring.backend.entity.enums.Status;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserStatusRequest {
  @NotNull(message = "Status must not be null")
  private Status status;
}
