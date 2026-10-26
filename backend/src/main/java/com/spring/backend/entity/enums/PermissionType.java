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

  private int strength(){
    return switch(this){
      case NONE -> 0;
      case VIEW -> 1;
      case EDIT -> 2;
      case OWNER ->3;
    };
  }
  public static PermissionType max(PermissionType a, PermissionType b) {
    return a.strength() >= b.strength() ? a : b;
  }
}
