package com.spring.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.spring.backend.entity.enums.PermissionType;
import com.spring.backend.entity.Permission;
import com.spring.backend.entity.enums.PermissionType;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

        Optional<Permission> findByFolder_IdAndSharedWithUser_Id(
        UUID folderId,
        UUID userId
);

    @EntityGraph(attributePaths = {"sharedWithUser", "grantedBy"})
    List<Permission> findByFolder_IdAndSharedWithUserIsNotNullOrderByCreatedAtDesc(UUID folderId);

    @EntityGraph(attributePaths = {
            "folder",
            "sharedWithUser",
            "grantedBy"
    })
    List<Permission>
            findByFolder_IdInAndSharedWithUserIsNotNullOrderByCreatedAtDesc(
                    List<UUID> folderIds
            );
        
        @EntityGraph(attributePaths = {"folder"})
        Optional<Permission>
        findByFolder_IdAndSharedWithUserIsNullAndPublicTokenIsNotNull(
                UUID folderId
        );

        @EntityGraph(attributePaths = {"folder"})
        Optional<Permission>
        findByPublicTokenAndSharedWithUserIsNullAndPermissionType(
                String publicToken,
                PermissionType permissionType
        );


        

        @EntityGraph(attributePaths = {"folder", "grantedBy"})
        List<Permission>
                findBySharedWithUser_IdAndPublicTokenIsNullAndPermissionTypeInOrderByCreatedAtDesc(
                        UUID userId,
                        List<PermissionType> permissionTypes
                );
}
