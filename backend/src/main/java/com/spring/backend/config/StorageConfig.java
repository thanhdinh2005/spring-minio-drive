package com.spring.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class StorageConfig {

  @Value("${storage.endpoint}")
  private String endpoint;

  @Value("${storage.bucket}")
  private String bucket;

  @Value("${storage.region}")
  private String region;

  @Value("${storage.access-key}")
  private String accessKey;

  @Value("${storage.secret-key}")
  private String secretKey;

  @Value("${storage.path-style-access:true}")
  private boolean pathStyleAccess;

  @Bean
  public S3Client s3Client() {
    return S3Client.builder()
      .endpointOverride(URI.create(endpoint))
      .region(Region.of(region))
      .credentialsProvider(
        StaticCredentialsProvider.create(
          AwsBasicCredentials.create(
            accessKey,
            secretKey
          )
        )
      )
      .forcePathStyle(pathStyleAccess)
      .build();
  }

  @Bean
  public S3Presigner s3Presigner() {
    return software.amazon.awssdk.services.s3.presigner.S3Presigner.builder()
      .endpointOverride(URI.create(endpoint))
      .region(Region.of(region))
      .credentialsProvider(
        StaticCredentialsProvider.create(
          AwsBasicCredentials.create(
            accessKey,
            secretKey
          )
        )
      )
      // BẮT BUỘC: đồng bộ path-style với S3Client. Không có dòng này, URL ký ra
      // dạng virtual-hosted (it-bucket.localhost:9000/...) nên browser/client
      // không resolve được khi dùng RustFS/MinIO local.
      .serviceConfiguration(S3Configuration.builder()
        .pathStyleAccessEnabled(pathStyleAccess)
        .build())
      .build();
  }
}
