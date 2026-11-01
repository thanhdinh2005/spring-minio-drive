package com.spring.backend.repository;

import com.spring.backend.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

  @Query("SELECT COALESCE(SUM(d.size), 0L) FROM Document d WHERE d.owner.id = :ownerId")
  Long sumSizeByOwnerId(@Param("ownerId") UUID ownerId);

  // Lấy các document trực tiếp bên trong một folder, có phân trang
  Page<Document> findByFolder_Id(UUID folderId, Pageable pageable);

  // Đếm số document trực tiếp bên trong một folder
  long countByFolder_Id(UUID folderId);

  // Lấy documents trong các folder IDs (dùng khi xoá cascade), kèm owner + folder
  @EntityGraph(attributePaths = {"owner", "folder"})
  List<Document> findByFolder_IdIn(Collection<UUID> folderIds);

  // Lấy toàn bộ documents trực tiếp bên trong một folder, kèm owner + folder
  @EntityGraph(attributePaths = {"owner", "folder"})
  List<Document> findByFolder_Id(UUID folderId);

  // Dọn document của folder bị xoá cứng, tránh FK documents.folder_id chặn DELETE folder
  void deleteByFolder_IdIn(Collection<UUID> folderIds);

  // Kiểm tra trùng tên trong cùng folder
  boolean existsByNameAndFolder_IdAndIdNot(String name, UUID folderId, UUID excludeId);

  // Lọc document theo điều kiện; sort do Pageable quyết định (server-side)
  // JPQL (không phải native) để Spring dịch tên property (vd. updatedAt) đúng sang cột (updated_at)
  // CAST(:name AS string): PostgreSQL suy tham số null untyped thành bytea -> "varchar ~~ bytea" khi :name null
  @Query(value = "SELECT d FROM Document d WHERE d.owner.id = :ownerId " +
    "AND (:folderId IS NULL OR d.folder.id = :folderId) " +
    "AND (:name IS NULL OR d.name LIKE CONCAT('%', CAST(:name AS string), '%')) " +
    "AND (:mimeType IS NULL OR d.mimeType = :mimeType)",
    countQuery = "SELECT COUNT(d) FROM Document d WHERE d.owner.id = :ownerId " +
    "AND (:folderId IS NULL OR d.folder.id = :folderId) " +
    "AND (:name IS NULL OR d.name LIKE CONCAT('%', CAST(:name AS string), '%')) " +
    "AND (:mimeType IS NULL OR d.mimeType = :mimeType)")
  Page<Document> filterDocuments(@Param("ownerId") UUID ownerId,
    @Param("folderId") UUID folderId,
    @Param("name") String name,
    @Param("mimeType") String mimeType,
    Pageable pageable);
}
