package com.spring.backend.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response cho GET /documents/{id}/download-url và /preview-url.
 * - url: presigned URL có thời hạn tới object trên MinIO/S3.
 * - expiresIn: thời hạn của URL tính bằng giây.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUrlResponse {

  private String url;

  private Long expiresIn;
}
