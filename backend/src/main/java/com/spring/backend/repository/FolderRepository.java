package com.spring.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;

import com.spring.backend.entity.Folder;

@Repository
public interface FolderRepository extends JpaRepository<Folder, UUID> {

  // Lấy các folder con trực tiếp của một folder
  List<Folder> findByParent_Id(UUID parentId);

  // Lấy các folder con trực tiếp của một folder, có phân trang
  Page<Folder> findByParent_Id(UUID parentId, Pageable pageable);

  // Kiểm tra sự tồn tại folder con
  boolean existsByParent_Id(UUID parentId);

  // Lấy các folder gốc (parent_id IS NULL) của một user
  List<Folder> findByOwner_IdAndParentIsNull(UUID ownerId);

  // Lấy tất cả folder của user (dùng cho tree view)
  List<Folder> findByOwner_Id(UUID ownerId);

  // Lấy toàn bộ folder gốc bất kể owner, để sinh tên duy nhất trong cấp đích
  List<Folder> findByParentIsNull();

  // Lọc folder theo điều kiện; sort do Pageable quyết định (server-side)
  // CAST(:name AS string): PostgreSQL suy tham số null untyped thành bytea -> "varchar ~~ bytea" khi :name null
  @Query("SELECT f FROM Folder f WHERE f.owner.id = :ownerId " +
    "AND (:name IS NULL OR f.name LIKE CONCAT('%', CAST(:name AS string), '%')) " +
    "AND (:parentId IS NULL OR f.parent.id = :parentId)")
  Page<Folder> filterFolders(@Param("ownerId") UUID ownerId,
    @Param("name") String name,
    @Param("parentId") UUID parentId,
    Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select f from Folder f where f.id = :folderId")
  Optional<Folder> findByIdForUpdate(
          @Param("folderId") UUID folderId
  );
}
