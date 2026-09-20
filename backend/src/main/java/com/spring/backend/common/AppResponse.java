package com.spring.backend.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.spring.backend.exception.ErrorCode;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.time.Instant;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL) // ẩn field null khỏi JSON (vd data khi lỗi, code khi thành công)
public class AppResponse<T> {

  private final boolean success;
  private final int status;
  private final String code;
  private final String message;
  private final String traceId;
  private final T data;
  private final Instant timestamp;

  private AppResponse(boolean success, int status, String code, String message, String traceId, T data) {
    this.success = success;
    this.status = status;
    this.code = code;
    this.message = message;
    this.traceId = traceId;
    this.data = data;
    this.timestamp = Instant.now();
  }

  // ===== Success =====

  public static <T> AppResponse<T> success(T data) {
    return new AppResponse<>(true, 200, null, null, null, data);
  }

  public static <T> AppResponse<T> success(T data, String traceId) {
    return new AppResponse<>(true, 200, null, null, traceId, data);
  }

  // ===== Error =====

  public static <T> AppResponse<T> error(ErrorCode errorCode, String traceId) {
    return new AppResponse<>(false, errorCode.getHttpStatus().value(),
      errorCode.getCode(), errorCode.getMessage(), traceId, null);
  }

  // Dùng khi cần override message (vd validation detail thay vì message mặc định của ErrorCode)
  public static <T> AppResponse<T> error(ErrorCode errorCode, String customMessage, String traceId) {
    return new AppResponse<>(false, errorCode.getHttpStatus().value(),
      errorCode.getCode(), customMessage, traceId, null);
  }
}
