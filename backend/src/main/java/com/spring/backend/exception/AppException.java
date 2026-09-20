package com.spring.backend.exception;

import lombok.Getter;

import java.util.Map;

@Getter
public class AppException extends RuntimeException {

  private final ErrorCode errorCode;
  private final Map<String, Object> context;

  public AppException(ErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
    this.context = Map.of();
  }

  // Use when logging and do not show for client
  public AppException(ErrorCode errorCode, Map<String, Object> context) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
    this.context = context;
  }
}
