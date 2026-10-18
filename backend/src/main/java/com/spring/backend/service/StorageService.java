package com.spring.backend.service;

/**
 * Truy cập object storage (MinIO/S3) ở mức thấp.
 * Hiện chỉ phục vụ tạo presigned URL để tải/xem trước tài liệu.
 */
public interface StorageService {

  /**
   * Tạo presigned URL (GET) cho một object, có thời hạn.
   *
   * @param objectKey          key của object trong bucket
   * @param expiresSeconds     thời hạn URL tính bằng giây
   * @param contentDisposition giá trị header Content-Disposition khi tải
   *                           (null = để client tự quyết định)
   * @return URL đã ký
   */
  String presignGet(String objectKey, long expiresSeconds, String contentDisposition);
}
