package com.spring.backend.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

//annotation
//Đánh dấu đây là một entity, Hibernate sẽ ánh xạ nó với bảng DB.
@Entity 
//Chỉ định tên bảng trong DB là folders.
@Table(name = "folders")
//Lombok sinh tự động getter/setter cho các field.
@Getter @Setter
//Sinh constructor rỗng.
@NoArgsConstructor
///Cho phép dùng builder pattern, kế thừa từ BaseEntity.
@SuperBuilder
//extends BaseEntity: Kế thừa các field chung (ví dụ: id, createdAt, updatedAt).
public class Folder extends BaseEntity {
  //Cột name trong DB, không được để trống
  @Column(nullable = false)
  private String name;

  //fetch = FetchType.LAZY: Chỉ load owner khi cần, tránh query thừa
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_id", nullable = false)
  private User owner;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_folder_id", nullable = true)
  private Folder parentFolder;

  @Column(name = "is_deleted")
  private int isDeleted;

  @Column(name= "deleted_at")
  private Instant deletedAt;
}
