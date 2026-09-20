package com.spring.backend.entity.enums;

public enum PermissionType {
  VIEW, EDIT, NONE, OWNER;

  public boolean canView() {
    return this == VIEW || this == EDIT || this == OWNER;
  }

  public boolean canEdit() {
    return this == EDIT || this == OWNER;
  }

  public boolean canShare() {
    return this == OWNER;
  }

  public static PermissionType max(PermissionType a, PermissionType b) {
    return a.ordinal() >= b.ordinal() ? a : b;
  }
}
