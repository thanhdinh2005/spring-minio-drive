package com.spring.backend.entity;

import com.spring.backend.entity.enums.PermissionType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
  name = "permissions",
  uniqueConstraints = @UniqueConstraint(columnNames = "public_token")
)
@Getter @Setter
@NoArgsConstructor
@SuperBuilder
public class Permission extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "folder_id", nullable = false)
  private Folder folder;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "shared_with_user_id")
  private User sharedWithUser;

  @Enumerated(EnumType.STRING)
  @Column(name = "permission_type", nullable = false, length = 20)
  private PermissionType permissionType;

  @Column(name = "public_token", length = 64, unique = true)
  private String publicToken;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "granted_by_id", nullable = false)
  private User grantedBy;
}
