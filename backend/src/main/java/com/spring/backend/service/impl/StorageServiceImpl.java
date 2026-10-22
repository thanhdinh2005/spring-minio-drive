package com.spring.backend.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.spring.backend.service.StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageServiceImpl implements StorageService {

  private final S3Presigner s3Presigner;
  private final S3Client s3Client;

  @Value("${storage.bucket}")
  private String bucket;

  @Value("${storage.multipart-threshold:5242880}") // 5MB default
  private long multipartThreshold;

  @Value("${storage.access-key}")
  private String accessKey;

  @Value("${storage.region}")
  private String region;

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

  @Override
  public String presignPut(String objectKey, long expiresSeconds, String contentType) {
    PutObjectRequest.Builder putObject = PutObjectRequest.builder()
      .bucket(bucket)
      .key(objectKey);

    if (contentType != null) {
      putObject.contentType(contentType);
    }

    PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
      .signatureDuration(Duration.ofSeconds(expiresSeconds))
      .putObjectRequest(putObject.build())
      .build();

    PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
    return presigned.url().toString();
  }

  @Override
  public Map<String, String> presignPost(String objectKey, long expiresSeconds, long maxSize, String contentType) {
    long expirationTime = Instant.now().plusSeconds(expiresSeconds).getEpochSecond();

    String policy = buildPostPolicy(objectKey, maxSize, contentType, expirationTime);
    String encodedPolicy = java.util.Base64.getEncoder().encodeToString(policy.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    String signature = calculateSignature(encodedPolicy);

    Map<String, String> formFields = new LinkedHashMap<>();
    formFields.put("key", objectKey);
    formFields.put("policy", encodedPolicy);
    formFields.put("x-amz-signature", signature);
    formFields.put("x-amz-credential", buildCredential());
    formFields.put("x-amz-algorithm", "AWS4-HMAC-SHA256");
    formFields.put("x-amz-date", Instant.now().toString().replaceAll("[-:]", "").split("\\.")[0] + "Z");

    if (contentType != null) {
      formFields.put("Content-Type", contentType);
    }

    String endpoint = s3Client.serviceClientConfiguration().endpointOverride()
      .map(uri -> uri.toString().replaceAll("/$", ""))
      .orElse("http://localhost:9000");
    formFields.put("url", endpoint + "/" + bucket);

    return formFields;
  }

  @Override
  public String initiateMultipartUpload(String objectKey, String contentType) {
    CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder()
      .bucket(bucket)
      .key(objectKey)
      .contentType(contentType)
      .build();

    CreateMultipartUploadResponse response = s3Client.createMultipartUpload(request);
    return response.uploadId();
  }

  @Override
  public String presignUploadPart(String uploadId, String objectKey, int partNumber, long expiresSeconds) {
    // For multipart upload parts, we need to include uploadId and partNumber in the request
    // The presigner doesn't directly support this, so we create a PutObjectRequest with the required params
    PutObjectRequest putObject = PutObjectRequest.builder()
      .bucket(bucket)
      .key(objectKey)
      .build();

    // Note: In AWS SDK v2, presigning multipart parts requires manual signing or using the high-level API
    // For now, we'll use the standard presignPut and add uploadId/partNumber as query parameters manually
    // This is a simplified implementation
    PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
      .signatureDuration(Duration.ofSeconds(expiresSeconds))
      .putObjectRequest(putObject)
      .build();

    PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
    String url = presigned.url().toString();
    
    // Append uploadId and partNumber as query parameters
    return url + (url.contains("?") ? "&" : "?") + "uploadId=" + uploadId + "&partNumber=" + partNumber;
  }

  @Override
  public void completeMultipartUpload(String uploadId, String objectKey, java.util.List<CompletedPart> parts) {
    CompletedMultipartUpload completedUpload = CompletedMultipartUpload.builder()
      .parts(parts)
      .build();

    CompleteMultipartUploadRequest request = CompleteMultipartUploadRequest.builder()
      .bucket(bucket)
      .key(objectKey)
      .uploadId(uploadId)
      .multipartUpload(completedUpload)
      .build();

    s3Client.completeMultipartUpload(request);
  }

  @Override
  public void abortMultipartUpload(String uploadId, String objectKey) {
    AbortMultipartUploadRequest request = AbortMultipartUploadRequest.builder()
      .bucket(bucket)
      .key(objectKey)
      .uploadId(uploadId)
      .build();

    s3Client.abortMultipartUpload(request);
  }

  @Override
  public long getMultipartThreshold() {
    return multipartThreshold;
  }

  private String buildPostPolicy(String objectKey, long maxSize, String contentType, long expirationTime) {
    StringBuilder policy = new StringBuilder();
    policy.append("{");
    policy.append("\"expiration\": \"").append(Instant.ofEpochSecond(expirationTime).toString()).append("\",");
    policy.append("\"conditions\": [");
    policy.append("{\"bucket\": \"").append(bucket).append("\"},");
    policy.append("{\"key\": \"").append(objectKey).append("\"},");
    if (contentType != null) {
      policy.append("{\"Content-Type\": \"").append(contentType).append("\"},");
    }
    policy.append("{\"x-amz-algorithm\": \"AWS4-HMAC-SHA256\"},");
    policy.append("{\"x-amz-credential\": \"").append(buildCredential()).append("\"},");
    policy.append("[\"content-length-range\", 0, ").append(maxSize).append("]");
    policy.append("]");
    policy.append("}");
    return policy.toString();
  }

  private String buildCredential() {
    String date = Instant.now().toString().split("T")[0].replace("-", "");
    return accessKey + "/" + date + "/" + region + "/s3/aws4_request";
  }

  private String calculateSignature(String encodedPolicy) {
    // Simplified: In production, use AWS Signature V4 signing
    // For MinIO with presigned POST, this would need proper V4 signing
    return "placeholder-signature";
  }
}