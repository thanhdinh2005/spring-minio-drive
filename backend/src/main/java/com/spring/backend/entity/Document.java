package com.spring.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "documents")
@Getter @Setter
@NoArgsConstructor
@SuperBuilder
public class Document extends BaseEntity {

  @Column(nullable = false)
  private String name;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "folder_id", nullable = false)
  private Folder folder;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_id", nullable = false)
  private User owner;

  @Column(name = "object_key", nullable = false, length = 512)
  private String objectKey;

  @Column(nullable = false)
  private Long size;

  @Column(name = "mime_type", length = 100)
  private String mimeType;
}
