package com.spring.backend.service;

import java.io.InputStream;
import java.util.UUID;

/**
 * Abstraction MinIO/S3 dùng chung cho DocumentService.
 * ObjectKey quy ước: users/{ownerId}/{uuid}-{filename đã sanitize}.
 */
public interface StorageService {

  String upload(InputStream input, long size, String contentType, UUID ownerId, String originalFilename);

  /** Presigned GET URL, hết hạn sau {@code expiresInSeconds} giây. */
  String presignGet(String objectKey, int expiresInSeconds);

  void delete(String objectKey);
}
