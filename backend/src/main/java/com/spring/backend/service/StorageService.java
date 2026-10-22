package com.spring.backend.service;

import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;

import java.util.List;
import java.util.Map;

/**
 * Truy cập object storage (MinIO/S3) ở mức thấp.
 * Hỗ trợ presigned URL cho GET, PUT và Multipart Upload.
 */
public interface StorageService {

  /**
   * Tạo presigned URL (GET) cho một object, có thời hạn.
   */
  String presignGet(String objectKey, long expiresSeconds, String contentDisposition);

  /**
   * Tạo presigned URL (PUT) để upload trực tiếp một object (single-part).
   * Dùng cho file nhỏ ≤ multipartThreshold (mặc định 5MB).
   */
  String presignPut(String objectKey, long expiresSeconds, String contentType);

  /**
   * Tạo presigned POST policy để upload qua form (browser).
   * Trả về Map chứa: url, fields (policy, signature, key, ...).
   */
  Map<String, String> presignPost(String objectKey, long expiresSeconds, long maxSize, String contentType);

  /**
   * Khởi tạo Multipart Upload cho file lớn.
   */
  String initiateMultipartUpload(String objectKey, String contentType);

  /**
   * Tạo presigned URL để upload một part trong multipart upload.
   */
  String presignUploadPart(String uploadId, String objectKey, int partNumber, long expiresSeconds);

  /**
   * Hoàn tất Multipart Upload.
   */
  void completeMultipartUpload(String uploadId, String objectKey, List<CompletedPart> parts);

  /**
   * Hủy Multipart Upload (dọn dẹp parts đã upload).
   */
  void abortMultipartUpload(String uploadId, String objectKey);

  /**
   * Ngưỡng kích thước để quyết định dùng multipart upload (mặc định 5MB).
   */
  long getMultipartThreshold();
}
