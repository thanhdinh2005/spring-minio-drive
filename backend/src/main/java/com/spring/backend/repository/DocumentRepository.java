package com.spring.backend.repository;

import com.spring.backend.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

  @Query("SELECT COALESCE(SUM(d.size), 0L) FROM Document d WHERE d.owner.id = :ownerId")
  Long sumSizeByOwnerId(@Param("ownerId") UUID ownerId);

  // Lấy các document trực tiếp bên trong một folder, có phân trang
  Page<Document> findByFolder_Id(UUID folderId, Pageable pageable);

  // Lấy toàn bộ document trực tiếp bên trong một folder (dùng khi xóa cascade)
  List<Document> findByFolder_Id(UUID folderId);

  // Đếm số document trực tiếp bên trong một folder
  long countByFolder_Id(UUID folderId);

  // Tìm document theo owner và ID (để check ownership)
  Optional<Document> findByIdAndOwner_Id(UUID id, UUID ownerId);

  // Tìm document trong folder theo owner (để check permission)
  Optional<Document> findByIdAndFolder_IdAndOwner_Id(UUID id, UUID folderId, UUID ownerId);

  // Lấy documents trong các folder IDs (dùng cho xóa cascade)
  List<Document> findByFolder_IdIn(List<UUID> folderIds);

  // Kiểm tra trùng tên trong cùng folder
  boolean existsByNameAndFolder_IdAndIdNot(String name, UUID folderId, UUID excludeId);
}
