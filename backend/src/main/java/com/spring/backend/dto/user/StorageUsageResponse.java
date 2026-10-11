package com.spring.backend.dto.user;

import com.spring.backend.entity.enums.Plan;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageUsageResponse {
  private Long storageUsed;
  private Long storageLimit;
  private Plan plan;
}
