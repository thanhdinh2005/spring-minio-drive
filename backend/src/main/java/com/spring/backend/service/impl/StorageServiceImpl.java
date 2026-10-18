package com.spring.backend.service.impl;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.spring.backend.service.StorageService;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@Service
@RequiredArgsConstructor
public class StorageServiceImpl implements StorageService {

  private final S3Presigner s3Presigner;

  @Value("${storage.bucket}")
  private String bucket;

  @Override
  public String presignGet(String objectKey, long expiresSeconds, String contentDisposition) {
    GetObjectRequest.Builder getObject = GetObjectRequest.builder()
      .bucket(bucket)
      .key(objectKey);

    if (contentDisposition != null) {
      getObject.responseContentDisposition(contentDisposition);
    }

    GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
      .signatureDuration(Duration.ofSeconds(expiresSeconds))
      .getObjectRequest(getObject.build())
      .build();

    PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
    return presigned.url().toString();
  }
}
