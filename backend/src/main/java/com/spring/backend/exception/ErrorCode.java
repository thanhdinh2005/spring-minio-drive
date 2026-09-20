package com.spring.backend.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

  // ===== User =====
  USER_NOT_FOUND(HttpStatus.NOT_FOUND, "ERR_USER_001", "User not found"),
  USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "ERR_USER_002", "Email already exists"),
  USER_LOCKED(HttpStatus.FORBIDDEN, "ERR_USER_003", "Account is locked"),
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "ERR_USER_004", "Invalid email or password"),

  // ===== Folder =====
  FOLDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ERR_FOLDER_001", "Folder not found"),
  FOLDER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "ERR_FOLDER_002", "You don't have access to this folder"),

  // ===== Document =====
  DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "ERR_DOC_001", "Document not found"),
  DOCUMENT_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "ERR_DOC_002", "File exceeds storage limit"),

  // ===== Permission =====
  PERMISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "ERR_PERM_001", "Permission not found"),
  INVALID_SHARE_TARGET(HttpStatus.BAD_REQUEST, "ERR_PERM_002",
    "Must specify either a user or generate a public link, not both"),
  CANNOT_SHARE_WITHOUT_OWNERSHIP(HttpStatus.FORBIDDEN, "ERR_PERM_003", "Only the owner can share this folder"),

  // ===== Request / fallback =====
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "ERR_REQ_001", "Invalid request"),
  MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "ERR_REQ_002", "Malformed request body"),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "ERR_REQ_003", "HTTP method not supported"),
  NOT_FOUND(HttpStatus.NOT_FOUND, "ERR_REQ_004", "Resource not found"),

  // ===== System =====
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "ERR_SYS_001", "An unexpected error occurred. Please try again later.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;

  ErrorCode(HttpStatus httpStatus, String code, String message) {
    this.httpStatus = httpStatus;
    this.code = code;
    this.message = message;
  }
}
