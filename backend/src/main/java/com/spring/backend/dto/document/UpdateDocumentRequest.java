package com.spring.backend.dto.document;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDocumentRequest {

  @Size(max = 255, message = "Document name must not exceed 255 characters")
  private String name;

  private UUID folderId;
}