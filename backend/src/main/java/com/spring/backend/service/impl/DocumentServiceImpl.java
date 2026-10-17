package com.spring.backend.service.impl;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.spring.backend.common.utils.FolderAccessHelper;
import com.spring.backend.common.utils.StorageNameUtils;
import com.spring.backend.dto.document.DocumentDto;
import com.spring.backend.dto.document.DocumentUpdateDto;
import com.spring.backend.dto.document.PresignedUrlDto;
import com.spring.backend.entity.Document;
import com.spring.backend.entity.Folder;
import com.spring.backend.entity.User;
import com.spring.backend.exception.AppException;
import com.spring.backend.exception.ErrorCode;
import com.spring.backend.mapper.DocumentMapper;
import com.spring.backend.repository.DocumentRepository;
import com.spring.backend.repository.FolderRepository;
import com.spring.backend.repository.UserRepository;
import com.spring.backend.service.DocumentService;
import com.spring.backend.service.StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private static final int PRESIGNED_TTL_SECONDS = 300;

    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final DocumentMapper documentMapper;
    private final StorageService storageService;

    // ===== 2.3 POST /api/folders/{id}/documents =====
    // Thứ tự bắt buộc: check EDIT → check quota (storage_used) → upload MinIO → lưu metadata
    @Override
    @Transactional
    public DocumentDto upload(UUID folderId, MultipartFile file, UUID userId) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new AppException(ErrorCode.FOLDER_NOT_FOUND));
        FolderAccessHelper.requireEdit(folder, userId);

        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        long used = owner.getStorageUsed() == null ? 0L : owner.getStorageUsed();
        long limit = owner.getStorageLimit() == null ? 0L : owner.getStorageLimit();
        if (used + file.getSize() > limit) {
            throw new AppException(ErrorCode.DOCUMENT_TOO_LARGE);
        }

        String objectKey;
        try {
            objectKey = storageService.upload(
                    file.getInputStream(), file.getSize(), file.getContentType(),
                    userId, file.getOriginalFilename());
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        Document saved = documentRepository.save(Document.builder()
                .name(file.getOriginalFilename())
                .folder(folder)
                .owner(owner)
                .objectKey(objectKey)
                .size(file.getSize())
                .mimeType(file.getContentType())
                .build());

        owner.setStorageUsed(used + file.getSize());
        userRepository.save(owner);

        return documentMapper.toDto(saved);
    }

    // ===== 2.4 GET /api/documents/{id}/download-url =====
    @Override
    @Transactional(readOnly = true)
    public PresignedUrlDto downloadUrl(UUID documentId, UUID userId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));
        FolderAccessHelper.requireView(doc.getFolder(), userId);

        return PresignedUrlDto.builder()
                .url(storageService.presignGet(doc.getObjectKey(), PRESIGNED_TTL_SECONDS))
                .expiresIn(PRESIGNED_TTL_SECONDS)
                .build();
    }

    // ===== 2.5 GET /api/documents/{id}/preview-url =====
    @Override
    @Transactional(readOnly = true)
    public PresignedUrlDto previewUrl(UUID documentId, UUID userId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));
        FolderAccessHelper.requireView(doc.getFolder(), userId);

        if (!isPreviewable(doc.getMimeType())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        return PresignedUrlDto.builder()
                .url(storageService.presignGet(doc.getObjectKey(), PRESIGNED_TTL_SECONDS))
                .expiresIn(PRESIGNED_TTL_SECONDS)
                .build();
    }

    // ===== 2.6 PATCH /api/documents/{id} =====
    @Override
    @Transactional
    public DocumentDto patch(UUID documentId, DocumentUpdateDto dto, UUID userId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));
        // Cần EDIT trên vị trí cũ
        FolderAccessHelper.requireEdit(doc.getFolder(), userId);

        if (dto.getName() != null) {
            if (!StorageNameUtils.isValidStorageName(dto.getName())) {
                throw new AppException(ErrorCode.VALIDATION_ERROR);
            }
            doc.setName(dto.getName().trim());
        }

        if (dto.getFolderId() != null && !dto.getFolderId().equals(doc.getFolder().getId())) {
            Folder dest = folderRepository.findById(dto.getFolderId())
                    .orElseThrow(() -> new AppException(ErrorCode.FOLDER_NOT_FOUND));
            // Cần EDIT trên vị trí đích khi di chuyển
            FolderAccessHelper.requireEdit(dest, userId);
            doc.setFolder(dest);
        }

        return documentMapper.toDto(documentRepository.save(doc));
    }

    // ===== 2.7 DELETE /api/documents/{id} — xóa cứng, chỉ owner =====
    // After-commit (giống FolderService.deleteHard): trong transaction chỉ xóa row DB
    // + trừ quota; object RustFS xóa SAU KHI commit để rollback DB không để lại link gãy.
    @Override
    @Transactional
    public void delete(UUID documentId, UUID userId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));
        // Ràng buộc: CHỈ owner mới được xóa, EDIT được share không cho phép xóa
        if (doc.getOwner() == null || !doc.getOwner().getId().equals(userId)) {
            throw new AppException(ErrorCode.FOLDER_ACCESS_DENIED);
        }

        String objectKey = doc.getObjectKey();

        User owner = doc.getOwner();
        if (owner != null && doc.getSize() != null) {
            long used = owner.getStorageUsed() == null ? 0L : owner.getStorageUsed();
            // re-load để chắc chắn entity managed khi trừ quota
            User managed = userRepository.findById(owner.getId()).orElse(owner);
            long cur = managed.getStorageUsed() == null ? used : managed.getStorageUsed();
            managed.setStorageUsed(Math.max(0L, cur - doc.getSize()));
            userRepository.save(managed);
        }
        documentRepository.delete(doc);

        deleteStorageAfterCommit(objectKey);
    }

    private void deleteStorageAfterCommit(String objectKey) {
        if (objectKey == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        storageService.delete(objectKey);
                    } catch (Exception e) {
                        log.warn("After-commit storage delete failed, objectKey={} left as orphan: {}",
                                objectKey, e.toString());
                    }
                }
            });
        } else {
            // Fallback ngoài transaction (vd unit test) → xóa ngay best-effort
            try {
                storageService.delete(objectKey);
            } catch (Exception e) {
                log.warn("Storage delete failed, objectKey={}: {}", objectKey, e.toString());
            }
        }
    }

    private boolean isPreviewable(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        return mimeType.equals("application/pdf") || mimeType.startsWith("image/");
    }
}
