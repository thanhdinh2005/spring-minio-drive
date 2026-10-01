package com.spring.backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.spring.backend.entity.Folder;

@Repository
public interface FolderRepository extends JpaRepository<Folder, UUID>{
    public List<Folder> findByOwnerIdAndParentFolderId(UUID userId, UUID parentFolderId);  
    public List<Folder> findByOwnerIdAndParentFolderIsNull(UUID ownerId);

    @Modifying(clearAutomatically = true)
    @Query(
        "UPDATE Folder f " +
        "SET f.isDeleted = 1, f.deletedAt = :deletedAt " +
        "WHERE f.id IN :ids AND f.owner.id = :ownerId "
    )
    public void softDeleteMultipleFolders(
        @Param(value = "ids") List<UUID> ids,
        @Param(value = "deletedAt") Instant deletedAt,
        @Param(value = "ownerId") UUID ownerId);

    // lấy các folder ở root
    public Page<Folder> findByOwnerIdAndParentFolderIsNull(Pageable pageable, UUID ownerId);

    public Page<Folder> findByOwnerIdAndParentFolderId(Pageable pageable, UUID ownerId, UUID parentFolderId);
}
