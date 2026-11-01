package com.spring.backend.service;

import software.amazon.awssdk.services.s3.model.CompletedPart;

import java.util.List;
import java.util.Map;

/**
 * Truy cập object storage (MinIO/S3) ở mức thấp.
 * Hỗ trợ presigned URL cho GET, PUT, POST và Multipart Upload.
 */
public interface StorageService {

  /**
   * Tạo presigned URL (GET) cho một object, có thời hạn.
   */
  String presignGet(String objectKey, long expiresSeconds, String contentDisposition);

  /**
   * Tạo presigned URL (PUT) để upload trực tiếp một object (single-part).
   */
  String presignPut(String objectKey, long expiresSeconds, String contentType);

  /**
   * Tạo presigned POST policy để upload qua form (browser).
   * Trả về Map gồm "url" và các form field cần gửi kèm file.
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

  /**
   * Kiểm tra object có tồn tại.
   */
  boolean objectExists(String objectKey);

  /**
   * Xoá một object. Không lỗi nếu object không tồn tại.
   */
  void deleteObject(String objectKey);

  /**
   * Copy object phía server. Đích phải khác nguồn.
   */
  void copyObject(String sourceKey, String destinationKey);

  /**
   * Liệt kê toàn bộ objectKey dưới một prefix.
   */
  List<String> listObjects(String prefix);
}
