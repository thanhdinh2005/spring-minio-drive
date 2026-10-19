package com.spring.backend.dto.auth;

import com.spring.backend.entity.enums.Plan;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpgradeResponse {
  private Plan plan;
  private Long storageLimit;
}
