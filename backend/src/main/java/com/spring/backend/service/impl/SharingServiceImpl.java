
package com.spring.backend.service.impl;
import com.spring.backend.dto.sharing.SharingDetailsResponse;
import com.spring.backend.entity.Document;
import com.spring.backend.repository.DocumentRepository;
import com.spring.backend.dto.sharing.DirectPermissionResponse;
import com.spring.backend.dto.sharing.SharePermissionRequest;
import com.spring.backend.dto.sharing.SharePermissionResponse;
import com.spring.backend.entity.Folder;
import com.spring.backend.entity.Permission;
import com.spring.backend.entity.User;
import com.spring.backend.entity.enums.PermissionType;
import com.spring.backend.exception.AppException;
import com.spring.backend.exception.ErrorCode;
import com.spring.backend.repository.FolderRepository;
import com.spring.backend.repository.PermissionRepository;
import com.spring.backend.repository.UserRepository;
import com.spring.backend.service.SharingService;
import com.spring.backend.dto.sharing.UpdatePermissionRequest;
import com.spring.backend.dto.sharing.PublicLinkResponse;
import com.spring.backend.dto.folder.DocumentSummaryResponse;
import com.spring.backend.dto.folder.FolderContentsResponse;
import com.spring.backend.dto.folder.FolderSummaryResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.security.SecureRandom;
import java.util.Base64;
@Service
@RequiredArgsConstructor
public class SharingServiceImpl implements SharingService {

    private final DocumentRepository documentRepository;
    private final SharingPermissionResolver sharingPermissionResolver;
    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private static final SecureRandom TOKEN__RANDOM = new SecureRandom();

    @Value("${app.public-folder-base-url:http://localhost:8081/api/public/folders}")
    private String publicFolderBaseUrl;
    /*
     * CHỨC NĂNG 1: CẤP QUYỀN CHIA SẺ
     *
     * API:
     * POST /api/folders/{id}/permissions
     *
     * Điều kiện:
     * - Folder phải tồn tại.
     * - Người gọi phải là owner.
     * - Không được chia sẻ cho chính owner.
     * - Recipient phải tồn tại.
     * - Permission chỉ được VIEW hoặc EDIT.
     * - Không tạo trùng quyền trên cùng folder-user.
     *
     * HTTP 201 được thiết lập ở Controller.
     */
    @Override
    @Transactional
    public SharePermissionResponse grantPermission(
            UUID folderId,
            SharePermissionRequest request,
            UUID actorId) {

        // Bước 1: Kiểm tra folder và quyền sở hữu.
        Folder folder = requireOwnedFolder(folderId, actorId);

        // Bước 2: Kiểm tra người nhận.
        if (request == null ||
                request.getSharedWithUserId() == null) {

            throw new AppException(
                ErrorCode.INVALID_SHARE_TARGET
            );
        }

        UUID recipientId = request.getSharedWithUserId();

        if (folder.getOwner().getId().equals(recipientId)) {

            throw new AppException(
                ErrorCode.INVALID_SHARE_TARGET
            );
        }

        // Bước 3: Kiểm tra permission.
        PermissionType type = request.getPermissionType();

        if (type != PermissionType.VIEW &&
                type != PermissionType.EDIT) {

            throw new AppException(
                ErrorCode.INVALID_SHARE_PERMISSION
            );
        }

        // Bước 4: Người nhận phải tồn tại.
        User recipient = userRepository.findById(recipientId)
            .orElseThrow(() ->
                new AppException(ErrorCode.USER_NOT_FOUND)
            );

        // Bước 5: Không tạo quyền trùng.
        boolean alreadyShared = permissionRepository
            .findByFolder_IdAndSharedWithUser_Id(
                folderId,
                recipientId
            )
            .isPresent();

        if (alreadyShared) {

            throw new AppException(
                ErrorCode.SHARE_PERMISSION_CONFLICT
            );
        }

        // Bước 6: Tạo bản ghi quyền.
        Permission permission = Permission.builder()
            .folder(folder)
            .sharedWithUser(recipient)
            .grantedBy(folder.getOwner())
            .permissionType(type)
            .build();

        // Bước 7: Lưu quyền xuống database.
        Permission saved;

        try {
            saved = permissionRepository.saveAndFlush(
                permission
            );
        } catch (DataIntegrityViolationException ex) {

            // Bảo vệ trước hai request đồng thời tạo
            // cùng một cặp folder-user.
            // Chỉ chuyển lỗi thành 409 nếu đúng
            // unique constraint của migration V7.
            if (isFolderUserUniqueViolation(ex)) {

                throw new AppException(
                    ErrorCode.SHARE_PERMISSION_CONFLICT
                );
            }

            throw ex;
        }

        // Bước 8: Trả response theo API Specs.
        return toResponse(saved);
    }

    /*
     * CHỨC NĂNG 2: XEM QUYỀN TRỰC TIẾP
     *
     * API:
     * GET /api/folders/{id}/permissions
     *
     * Chỉ owner được xem.
     * Không lấy quyền kế thừa từ folder cha.
     * Không lấy public-link permission.
     */
    @Override
    @Transactional(readOnly = true)
    public List<DirectPermissionResponse> getDirectShares(
            UUID folderId,
            UUID actorId
    ) {
        requireOwnedFolder(folderId, actorId);

        return permissionRepository
                .findByFolder_IdAndSharedWithUserIsNotNullOrderByCreatedAtDesc(
                        folderId
                )
                .stream()
                .map(this::toDirectPermissionResponse)
                .toList();
    }
    @Override
    @Transactional
    public SharePermissionResponse updatePermission(
            UUID permissionId,
            UpdatePermissionRequest request,
            UUID actorId
    ) {
        Permission permission = permissionRepository
                .findById(permissionId)
                .orElseThrow(
                        () -> new AppException(
                                ErrorCode.PERMISSION_NOT_FOUND
                        )
                );

        // Chỉ owner trực tiếp của folder được quản lý chia sẻ.
        requireOwnedFolder(
                permission.getFolder().getId(),
                actorId
        );

        // Endpoint này chỉ cập nhật quyền chia sẻ cho user.
        if (permission.getSharedWithUser() == null
                || permission.getPublicToken() != null) {
            throw new AppException(ErrorCode.INVALID_SHARE_TARGET);
        }

        if (request == null) {
            throw new AppException(ErrorCode.INVALID_SHARE_PERMISSION);
        }

        PermissionType type = request.getPermissionType();

        if (type != PermissionType.VIEW
                && type != PermissionType.EDIT) {
            throw new AppException(ErrorCode.INVALID_SHARE_PERMISSION);
        }

        permission.setPermissionType(type);

        Permission saved =
                permissionRepository.saveAndFlush(permission);

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public SharingDetailsResponse getFolderSharingDetails(
        UUID folderId,
        UUID actorId
    ){
        Folder folder = requireOwnedFolder(folderId, actorId);

        return SharingDetailsResponse.builder()
                .resourceId(folder.getId())
                .resourceName(folder.getName())
                .ownerId(folder.getOwner().getId())
                .users(sharingPermissionResolver.resolveForFolder(folder))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public SharingDetailsResponse getDocumentSharingDetails(
            UUID documentId,
            UUID actorId
    ) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(
                        () -> new AppException(
                            ErrorCode.DOCUMENT_NOT_FOUND
                        )
                );
        if(actorId == null
            || !document.getOwner().getId().equals(actorId)){
                throw new AppException(ErrorCode.DOCUMENT_ACCESS_DENIED);
        }

        return SharingDetailsResponse.builder()
                .resourceId(document.getId())
                .resourceName(document.getName())
                .ownerId(document.getOwner().getId())
                .users(
                    sharingPermissionResolver.resolveForDocument(document)
                )
                .build();

    }

    @Override 
    @Transactional 
    public PublicLinkResponse createOrGetPublicLink(
            UUID folderId,
            UUID actorId
    ){
        Folder folder = folderRepository.findByIdForUpdate(folderId)
                .orElseThrow(
                        () -> new AppException(ErrorCode.FOLDER_NOT_FOUND)
                );

        if(actorId == null
            || !folder.getOwner().getId().equals(actorId)){
                throw new AppException(
                    ErrorCode.CANNOT_SHARE_WITHOUT_OWNERSHIP
                );
        }
        Permission permission = permissionRepository
                .findByFolder_IdAndSharedWithUserIsNullAndPublicTokenIsNotNull(
                    folderId
                )
                .orElseGet(() -> {
                Permission publicLink = Permission.builder()
                        .folder(folder)
                        .sharedWithUser(null)
                        .permissionType(PermissionType.VIEW)
                        .publicToken(generatePublicToken())
                        .grantedBy(folder.getOwner())
                        .build();

                return permissionRepository.saveAndFlush(publicLink);
            });
            
        return toPublicLinkResponse(permission);

    }

    @Override
    @Transactional(readOnly = true)
    public FolderContentsResponse getPublicFolderContents(String token) {
        if (token == null || token.isBlank()) {
            throw new AppException(ErrorCode.PUBLIC_LINK_NOT_FOUND);
        }

        Permission permission = permissionRepository
                .findByPublicTokenAndSharedWithUserIsNullAndPermissionType(
                        token,
                        PermissionType.VIEW
                )
                .orElseThrow(
                        () -> new AppException(
                                ErrorCode.PUBLIC_LINK_NOT_FOUND
                        )
                );

        Folder folder = permission.getFolder();
        UUID ownerId = folder.getOwner().getId();

        long matchingFolders = folderRepository
                .countByOwner_IdAndName(ownerId, folder.getName());

        if (matchingFolders != 1) {
            throw new AppException(ErrorCode.PUBLIC_LINK_NOT_FOUND);
        }

        List<FolderSummaryResponse> subFolders = folderRepository
                .findByOwner_IdAndParentFolder(
                        ownerId,
                        folder.getName()
                )
                .stream()
                .map(this::toPublicFolderSummary)
                .toList();

        List<DocumentSummaryResponse> documents = documentRepository
                .findByFolder_Id(folder.getId())
                .stream()
                .map(this::toPublicDocumentSummary)
                .toList();

        return FolderContentsResponse.builder()
                .folder(
                        FolderContentsResponse.FolderInfo.builder()
                                .id(folder.getId())
                                .name(folder.getName())
                                .build()
                )
                .subFolders(subFolders)
                .documents(documents)
                .build();
    }

    @Override
    @Transactional
    public void revokePublicLink(
            UUID folderId,
            UUID actorId
    ) {
        Folder folder = folderRepository.findByIdForUpdate(folderId)
                .orElseThrow(
                        () -> new AppException(ErrorCode.FOLDER_NOT_FOUND)
                );

        if (actorId == null
                || !folder.getOwner().getId().equals(actorId)) {
            throw new AppException(
                    ErrorCode.CANNOT_SHARE_WITHOUT_OWNERSHIP
            );
        }

        Permission publicLink = permissionRepository
                .findByFolder_IdAndSharedWithUserIsNullAndPublicTokenIsNotNull(
                        folderId
                )
                .orElseThrow(
                        () -> new AppException(
                                ErrorCode.PUBLIC_LINK_NOT_FOUND
                        )
                );

        permissionRepository.delete(publicLink);
    }

    private FolderSummaryResponse toPublicFolderSummary(Folder folder) {
        boolean hasChildren = folderRepository
                .existsByOwner_IdAndParentFolder(
                        folder.getOwner().getId(),
                        folder.getName()
                );

        return FolderSummaryResponse.builder()
                .id(folder.getId())
                .name(folder.getName())
                .hasChildren(hasChildren)
                .build();
    }

    private DocumentSummaryResponse toPublicDocumentSummary(
            Document document
    ) {
        return DocumentSummaryResponse.builder()
                .id(document.getId())
                .name(document.getName())
                .size(document.getSize())
                .mimeType(document.getMimeType())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }



    private String generatePublicToken(){
        byte[] bytes = new byte[32];
        TOKEN__RANDOM.nextBytes(bytes);
        
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }


    private PublicLinkResponse toPublicLinkResponse(
        Permission permission
    ){
        String token = permission.getPublicToken();

        String baseUrl = publicFolderBaseUrl.replaceAll("/+$", "");
    
        return PublicLinkResponse.builder() 
                .publicToken(token)
                .publicUrl(baseUrl + "/" + token)
                .build();
    }

    /*
     * HÀM DÙNG CHUNG: KIỂM TRA OWNER
     *
     * Không kiểm tra isDeleted vì dự án
     * hiện sử dụng hard delete.
     */
    private Folder requireOwnedFolder(
            UUID folderId,
            UUID actorId) {

        Folder folder = folderRepository.findById(folderId)
            .orElseThrow(() ->
                new AppException(ErrorCode.FOLDER_NOT_FOUND)
            );

        if (actorId == null ||
                !folder.getOwner().getId().equals(actorId)) {

            throw new AppException(
                ErrorCode.CANNOT_SHARE_WITHOUT_OWNERSHIP
            );
        }

        return folder;
    }

    /*
     * HÀM DÙNG CHUNG: CHUYỂN ENTITY THÀNH DTO
     */
    private SharePermissionResponse toResponse(
            Permission permission) {

        return SharePermissionResponse.builder()
            .id(permission.getId())
            .folderId(permission.getFolder().getId())
            .sharedWithUserId(
                permission.getSharedWithUser().getId()
            )
            .permissionType(
                permission.getPermissionType()
            )
            .build();
    }

    /*
     * Kiểm tra xem lỗi database có liên quan
     * đến UNIQUE constraint V7 hay không.
     */
    private boolean isFolderUserUniqueViolation(
            Throwable exception) {

        Throwable cause = exception;

        while (cause != null) {

            if (cause instanceof
                    org.hibernate.exception.ConstraintViolationException
                    constraintException) {

                if ("uq_permissions_folder_user".equalsIgnoreCase(
                        constraintException.getConstraintName())) {

                    return true;
                }
            }

            cause = cause.getCause();
        }

        return false;
    }
    private DirectPermissionResponse toDirectPermissionResponse(
            Permission permission
    ) {
        User recipient = permission.getSharedWithUser();

        DirectPermissionResponse.SharedWithUserResponse sharedWithUser =
                DirectPermissionResponse.SharedWithUserResponse.builder()
                        .id(recipient.getId())
                        .email(recipient.getEmail())
                        .build();

        return DirectPermissionResponse.builder()
                .id(permission.getId())
                .sharedWithUser(sharedWithUser)
                .permissionType(permission.getPermissionType())
                .build();
    }


}
