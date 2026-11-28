package com.spring.backend.entity;

import com.spring.backend.entity.enums.Plan;
import com.spring.backend.entity.enums.Role;
import com.spring.backend.entity.enums.Status;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor
@SuperBuilder
public class User extends BaseEntity {

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Role role;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Status status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Plan plan;

  @Builder.Default
  @Column(name = "storage_used", nullable = false)
  private Long storageUsed = 0L;

  @Builder.Default
  @Column(name = "storage_limit", nullable = false)
  private Long storageLimit = 104857600L;

  @Builder.Default
  @Column(name = "email_verified", nullable = false)
  private Boolean emailVerified = false;

  // Lưu SHA-256 hash của token, không lưu raw token
  @Column(name = "email_verify_token", length = 128)
  private String emailVerifyToken;

  @Column(name = "email_verify_token_expiry")
  private java.time.Instant emailVerifyTokenExpiry;
}
