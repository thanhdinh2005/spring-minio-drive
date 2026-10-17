package com.spring.backend.service.impl;

import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.spring.backend.service.StorageService;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
@RequiredArgsConstructor
public class StorageServiceImpl implements StorageService {

  private final S3Client s3Client;
  private final S3Presigner s3Presigner;

  @Value("${storage.bucket}")
  private String bucket;

  @Override
  public String upload(InputStream input, long size, String contentType, UUID ownerId, String originalFilename) {
    String safeName = originalFilename == null ? "file"
        : originalFilename.replaceAll("[\\\\/\\s]+", "_");
    String objectKey = "users/" + ownerId + "/" + UUID.randomUUID() + "-" + safeName;

    PutObjectRequest put = PutObjectRequest.builder()
        .bucket(bucket)
        .key(objectKey)
        .contentType(contentType)
        .contentLength(size)
        .build();
    s3Client.putObject(put, RequestBody.fromInputStream(input, size));
    return objectKey;
  }

  @Override
  public String presignGet(String objectKey, int expiresInSeconds) {
    GetObjectRequest get = GetObjectRequest.builder()
        .bucket(bucket)
        .key(objectKey)
        .build();
    GetObjectPresignRequest presign = GetObjectPresignRequest.builder()
        .signatureDuration(Duration.ofSeconds(expiresInSeconds))
        .getObjectRequest(get)
        .build();
    return s3Presigner.presignGetObject(presign).url().toString();
  }

  @Override
  public void delete(String objectKey) {
    s3Client.deleteObject(DeleteObjectRequest.builder()
        .bucket(bucket)
        .key(objectKey)
        .build());
  }
}
