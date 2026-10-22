package com.spring.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.spring.backend.entity.Folder;

@Repository
public interface FolderRepository extends JpaRepository<Folder, UUID> {

  // Lấy folder theo owner và parentFolder name
  Optional<Folder> findByOwner_IdAndNameAndParentFolder(UUID ownerId, String name, String parentFolder);

  // Lấy các folder con trực tiếp của một folder (theo parentFolder name)
  List<Folder> findByOwner_IdAndParentFolder(UUID ownerId, String parentFolder);

  // Lấy các folder con trực tiếp của một folder, có phân trang
  Page<Folder> findByOwner_IdAndParentFolder(UUID ownerId, String parentFolder, Pageable pageable);

  // Kiểm tra sự tồn tại folder con
  boolean existsByOwner_IdAndParentFolder(UUID ownerId, String parentFolder);

  // Đếm số folder con trực tiếp của một folder
  long countByOwner_IdAndParentFolder(UUID ownerId, String parentFolder);

  // Lấy các folder gốc (parent_folder IS NULL) của một user
  List<Folder> findByOwner_IdAndParentFolderIsNull(UUID ownerId);

  // Lấy folder theo owner và ID (để check ownership)
  Optional<Folder> findByIdAndOwner_Id(UUID id, UUID ownerId);

  // Tìm kiếm folder theo tên (LIKE)
  @Query("SELECT f FROM Folder f WHERE f.owner.id = :ownerId AND LOWER(f.name) LIKE LOWER(CONCAT('%', :keyword, '%'))")
  List<Folder> searchByOwnerIdAndKeyword(@Param("ownerId") UUID ownerId, @Param("keyword") String keyword);

  // Lấy tất cả folder của user (dùng cho tree view)
  List<Folder> findByOwner_Id(UUID ownerId);

  // Tìm folder descendants by parent folder names (để xóa đệ quy)
  @Query("SELECT f FROM Folder f WHERE f.owner.id = :ownerId AND f.parentFolder IN :parentNames")
  List<Folder> findByOwnerIdAndParentFolderIn(@Param("ownerId") UUID ownerId, @Param("parentNames") List<String> parentNames);
}
