package com.spring.backend.service.impl;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.spring.backend.service.StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

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
    PutObjectRequest putObject = PutObjectRequest.builder()
      .bucket(bucket)
      .key(objectKey)
      .contentType(contentType)
      .build();

    PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
      .signatureDuration(Duration.ofSeconds(expiresSeconds))
      .putObjectRequest(putObject)
      .build();

    PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
    String url = presigned.url().toString();
    int queryStart = url.indexOf('?');
    String endpoint = queryStart < 0 ? url : url.substring(0, queryStart);
    String query = queryStart < 0 ? "" : url.substring(queryStart + 1);

    // Presigned PUT không giới hạn kích thước; maxSize phải được validate phía service.
    Map<String, String> formFields = new LinkedHashMap<>();
    formFields.put("url", endpoint);
    formFields.put("method", "PUT");
    formFields.put("maxObjectSize", String.valueOf(maxSize));
    formFields.put("Content-Type", contentType == null ? "" : contentType);
    formFields.put("x-amz-signature", queryParameter(query, "X-Amz-Signature"));
    formFields.put("x-amz-credential", queryParameter(query, "X-Amz-Credential"));
    formFields.put("x-amz-algorithm", queryParameter(query, "X-Amz-Algorithm"));
    formFields.put("x-amz-date", queryParameter(query, "X-Amz-Date"));
    formFields.put("x-amz-signedheaders", queryParameter(query, "X-Amz-SignedHeaders"));
    formFields.put("x-amz-expires", queryParameter(query, "X-Amz-Expires"));
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
    UploadPartRequest uploadPart = UploadPartRequest.builder()
      .bucket(bucket)
      .key(objectKey)
      .uploadId(uploadId)
      .partNumber(partNumber)
      .build();

    UploadPartPresignRequest presignRequest = UploadPartPresignRequest.builder()
      .signatureDuration(Duration.ofSeconds(expiresSeconds))
      .uploadPartRequest(uploadPart)
      .build();

    PresignedUploadPartRequest presigned = s3Presigner.presignUploadPart(presignRequest);
    return presigned.url().toString();
  }

  @Override
  public void completeMultipartUpload(String uploadId, String objectKey, List<CompletedPart> parts) {
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

  @Override
  public boolean objectExists(String objectKey) {
    try {
      s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(objectKey).build());
      return true;
    } catch (S3Exception e) {
      if (e.statusCode() == 404) {
        return false;
      }
      throw e;
    }
  }

  @Override
  public void deleteObject(String objectKey) {
    s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
  }

  @Override
  public void copyObject(String sourceKey, String destinationKey) {
    s3Client.copyObject(CopyObjectRequest.builder()
      .sourceBucket(bucket)
      .sourceKey(sourceKey)
      .destinationBucket(bucket)
      .destinationKey(destinationKey)
      .build());
  }

  @Override
  public List<String> listObjects(String prefix) {
    List<String> keys = new ArrayList<>();
    String continuationToken = null;
    do {
      ListObjectsV2Response page = s3Client.listObjectsV2(ListObjectsV2Request.builder()
        .bucket(bucket)
        .prefix(prefix)
        .continuationToken(continuationToken)
        .build());
      page.contents().forEach(object -> keys.add(object.key()));
      continuationToken = Boolean.TRUE.equals(page.isTruncated()) ? page.nextContinuationToken() : null;
    } while (continuationToken != null);
    return keys;
  }

  private String queryParameter(String query, String name) {
    String prefix = name + "=";
    for (String pair : query.split("&")) {
      if (pair.startsWith(prefix)) {
        return URI.create("http://x/?" + pair).getQuery().substring(prefix.length());
      }
    }
    return "";
  }
}
