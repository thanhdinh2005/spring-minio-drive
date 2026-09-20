package com.spring.backend.exception;

import com.spring.backend.common.AppResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  // ===== 1. Lỗi nghiệp vụ tự throw =====
  @ExceptionHandler(AppException.class)
  public ResponseEntity<AppResponse<Void>> handleAppException(AppException ex) {
    ErrorCode code = ex.getErrorCode();
    String traceId = generateTraceId();

    if (code.getHttpStatus().is5xxServerError()) {
      log.error("[{}] {} | code={} | context={}",
        traceId, code.getMessage(), code.getCode(), ex.getContext(), ex);
    } else {
      log.warn("[{}] {} | code={} | context={}",
        traceId, code.getMessage(), code.getCode(), ex.getContext());
    }

    return ResponseEntity.status(code.getHttpStatus())
      .body(AppResponse.error(code, traceId));
  }

  // ===== 2. Validate @RequestBody (@Valid) =====
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<AppResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
    String traceId = generateTraceId();

    String detail = ex.getBindingResult().getFieldErrors().stream()
      .map(err -> err.getField() + ": " + err.getDefaultMessage())
      .collect(Collectors.joining("; "));

    log.warn("[{}] Validation failed: {}", traceId, detail);

    return ResponseEntity.badRequest()
      .body(AppResponse.error(ErrorCode.VALIDATION_ERROR, detail, traceId));
  }

  // ===== 3. Validate @RequestParam / @PathVariable (@Validated ở class) =====
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<AppResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
    String traceId = generateTraceId();

    String detail = ex.getConstraintViolations().stream()
      .map(v -> v.getPropertyPath() + ": " + v.getMessage())
      .collect(Collectors.joining("; "));

    log.warn("[{}] Constraint violation: {}", traceId, detail);

    return ResponseEntity.badRequest()
      .body(AppResponse.error(ErrorCode.VALIDATION_ERROR, detail, traceId));
  }

  // ===== 4. Body gửi lên sai JSON / thiếu field bắt buộc kiểu primitive =====
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<AppResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
    String traceId = generateTraceId();
    log.warn("[{}] Malformed request body: {}", traceId, ex.getMessage());

    return ResponseEntity.badRequest()
      .body(AppResponse.error(ErrorCode.MALFORMED_REQUEST, traceId));
  }

  // ===== 5. Gọi sai HTTP method (vd PUT vào endpoint chỉ có GET) =====
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<AppResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
    String traceId = generateTraceId();
    log.warn("[{}] Method not supported: {}", traceId, ex.getMessage());

    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
      .body(AppResponse.error(ErrorCode.METHOD_NOT_ALLOWED, traceId));
  }

  // ===== 6. Gọi endpoint không tồn tại =====
  @ExceptionHandler(NoHandlerFoundException.class)
  public ResponseEntity<AppResponse<Void>> handleNotFound(NoHandlerFoundException ex) {
    String traceId = generateTraceId();
    log.warn("[{}] No handler found: {} {}", traceId, ex.getHttpMethod(), ex.getRequestURL());

    return ResponseEntity.status(HttpStatus.NOT_FOUND)
      .body(AppResponse.error(ErrorCode.NOT_FOUND, traceId));
  }

  // ===== 7. Không có quyền (Spring Security) =====
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<AppResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
    String traceId = generateTraceId();
    log.warn("[{}] Access denied: {}", traceId, ex.getMessage());

    return ResponseEntity.status(HttpStatus.FORBIDDEN)
      .body(AppResponse.error(ErrorCode.FOLDER_ACCESS_DENIED, traceId));
  }

  // ===== 8. Fallback — mọi lỗi không lường trước =====
  @ExceptionHandler(Exception.class)
  public ResponseEntity<AppResponse<Void>> handleUnexpected(Exception ex) {
    String traceId = generateTraceId();
    log.error("[{}] Unexpected error: {}", traceId, ex.getMessage(), ex);

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
      .body(AppResponse.error(ErrorCode.INTERNAL_SERVER_ERROR, traceId));
  }

  private String generateTraceId() {
    return UUID.randomUUID().toString().substring(0, 8);
  }
}
