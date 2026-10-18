package com.spring.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.spring.backend.entity.Folder;

@Repository
public interface FolderRepository extends JpaRepository<Folder, UUID> {

  // Lấy các folder con trực tiếp của một folder (không lọc theo owner vì folder có thể được share)
  List<Folder> findByParentFolder(String parentFolder);

  // Lấy các folder gốc (parent_folder = null) của một user
  List<Folder> findByOwner_IdAndParentFolderIsNull(UUID ownerId);
}
