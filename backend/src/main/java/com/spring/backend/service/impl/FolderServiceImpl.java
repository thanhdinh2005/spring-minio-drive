package com.spring.backend.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.spring.backend.common.utils.FolderAccessHelper;
import com.spring.backend.common.utils.StorageNameUtils;
import com.spring.backend.dto.document.DocumentDto;
import com.spring.backend.dto.folder.FolderContentsDto;
import com.spring.backend.dto.folder.FolderCreateDto;
import com.spring.backend.dto.folder.FolderDto;
import com.spring.backend.dto.folder.FolderUpdateDto;
import com.spring.backend.entity.Folder;
import com.spring.backend.entity.User;
import com.spring.backend.exception.AppException;
import com.spring.backend.exception.ErrorCode;
import com.spring.backend.mapper.DocumentMapper;
import com.spring.backend.mapper.FolderMapper;
import com.spring.backend.repository.DocumentRepository;
import com.spring.backend.repository.FolderRepository;
import com.spring.backend.repository.UserRepository;
import com.spring.backend.service.FolderService;
import com.spring.backend.service.StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class FolderServiceImpl implements FolderService {

    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final FolderMapper folderMapper;
    private final DocumentMapper documentMapper;
    private final StorageService storageService;

    // ===== 2.1 POST /api/folders =====
    @Override
    @Transactional
    public FolderDto create(FolderCreateDto dto, UUID ownerId) {
        if (dto.getName() == null || !StorageNameUtils.isValidStorageName(dto.getName())) {
            throw new AppException(ErrorCode.INVALID_FOLDER_NAME);
        }

        Folder parentFolder = null;
        if (dto.getParentFolderId() != null) {
            parentFolder = folderRepository.findById(dto.getParentFolderId())
                    .orElseThrow(() -> new AppException(ErrorCode.FOLDER_NOT_FOUND));
            // parent không thuộc quyền user (owner hoặc EDIT kế thừa — tạm chỉ owner)
            FolderAccessHelper.requireEdit(parentFolder, ownerId);
        }

        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Giữ hành vi cũ: tự sinh tên duy nhất trong cùng parent (abc -> abc (1))
        List<Folder> siblings = (dto.getParentFolderId() == null)
                ? folderRepository.findByOwnerIdAndParentFolderIsNull(ownerId)
                : folderRepository.findByOwnerIdAndParentFolderId(ownerId, dto.getParentFolderId());
        List<String> siblingNames = siblings.stream().map(Folder::getName).collect(Collectors.toList());
        String newName = StorageNameUtils.generateUniqueName(siblingNames, dto.getName());

        Folder saved = folderRepository.save(Folder.builder()
                .name(newName)
                .owner(owner)
                .parentFolder(parentFolder)
                .build());
        return folderMapper.toDto(saved);
    }

    // ===== 2.2 GET /api/folders/{id}/contents =====
    @Override
    @Transactional(readOnly = true)
    public FolderContentsDto getContents(UUID folderId, UUID userId) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new AppException(ErrorCode.FOLDER_NOT_FOUND));
        FolderAccessHelper.requireView(folder, userId);

        List<FolderDto> subFolders = folderRepository.findByParentFolderId(folderId)
                .stream().map(folderMapper::toDto).toList();
        List<DocumentDto> documents = documentRepository.findByFolderId(folderId)
                .stream().map(documentMapper::toDto).toList();

        return FolderContentsDto.builder()
                .folder(folderMapper.toDto(folder))
                .subFolders(subFolders)
                .documents(documents)
                .build();
    }

    // ===== 2.6 PATCH /api/folders/{id} =====
    @Override
    @Transactional
    public FolderDto patch(UUID folderId, FolderUpdateDto dto, UUID userId) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new AppException(ErrorCode.FOLDER_NOT_FOUND));
        // Cần EDIT trên vị trí cũ
        FolderAccessHelper.requireEdit(folder, userId);

        if (dto.getName() != null) {
            if (!StorageNameUtils.isValidStorageName(dto.getName())) {
                throw new AppException(ErrorCode.INVALID_FOLDER_NAME);
            }
            folder.setName(dto.getName().trim());
        }

        boolean wantMoveToRoot = Boolean.TRUE.equals(dto.getMoveToRoot());
        if (wantMoveToRoot) {
            folder.setParentFolder(null);
        } else if (dto.getParentFolderId() != null) {
            if (dto.getParentFolderId().equals(folderId)) {
                throw new AppException(ErrorCode.VALIDATION_ERROR);
            }
            Folder dest = folderRepository.findById(dto.getParentFolderId())
                    .orElseThrow(() -> new AppException(ErrorCode.FOLDER_NOT_FOUND));
            // Cần EDIT trên vị trí đích
            FolderAccessHelper.requireEdit(dest, userId);
            // Chống cycle: không di chuyển vào chính con cháu của mình
            if (isDescendant(dest, folderId)) {
                throw new AppException(ErrorCode.VALIDATION_ERROR);
            }
            folder.setParentFolder(dest);
        }

        return folderMapper.toDto(folderRepository.save(folder));
    }

    // ===== 2.7 DELETE /api/folders/{id} — xóa cứng cascade =====
    // After-commit: trong transaction chỉ thu gom objectKey + xóa row DB + trừ quota.
    // Object trên RustFS chỉ bị xóa SAU KHI DB commit thành công, nên rollback DB
    // không bao giờ để lại DB trỏ tới object đã mất. Chế độ lỗi còn lại là
    // "orphan object" (DB sạch nhưng object sót trên RustFS) — an toàn hơn mất dữ liệu,
    // dọn bằng job đối soát (TODO).
    @Override
    @Transactional
    public void deleteHard(UUID folderId, UUID userId) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new AppException(ErrorCode.FOLDER_NOT_FOUND));
        // Ràng buộc: CHỈ owner mới được xóa, EDIT được share không cho phép xóa
        FolderAccessHelper.requireOwner(folder, userId);
        List<String> objectKeys = new ArrayList<>();
        deleteFolderRecursive(folder, objectKeys);
        deleteStorageAfterCommit(objectKeys);
    }

    private void deleteFolderRecursive(Folder folder, List<String> objectKeys) {
        // 1. Thu gom objectKey + xóa row document + trừ quota owner của document
        for (var doc : documentRepository.findByFolderId(folder.getId())) {
            if (doc.getObjectKey() != null) {
                objectKeys.add(doc.getObjectKey());
            }
            User docOwner = doc.getOwner();
            if (docOwner != null && doc.getSize() != null) {
                long used = docOwner.getStorageUsed() == null ? 0L : docOwner.getStorageUsed();
                docOwner.setStorageUsed(Math.max(0L, used - doc.getSize()));
                userRepository.save(docOwner);
            }
            documentRepository.delete(doc);
        }
        // 2. Đệ quy subFolders
        for (Folder child : folderRepository.findByParentFolderId(folder.getId())) {
            deleteFolderRecursive(child, objectKeys);
        }
        // 3. Xóa chính folder
        folderRepository.delete(folder);
    }

    /**
     * Xóa object RustFS sau khi transaction DB commit. Lỗi storage ở phase này
     * KHÔNG ném tiếp (DB đã commit, ném cũng vô ích) mà chỉ log để job đối soát
     * dọn orphan object sau. DeleteObject của S3 idempotent nên retry an toàn.
     */
    private void deleteStorageAfterCommit(List<String> objectKeys) {
        if (objectKeys.isEmpty()) {
            return;
        }
        List<String> keys = List.copyOf(objectKeys);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteStorageBestEffort(keys);
                }
            });
        } else {
            // Fallback: ngoài transaction (vd unit test Mockito không có tx) → xóa ngay
            deleteStorageBestEffort(keys);
        }
    }

    private void deleteStorageBestEffort(List<String> objectKeys) {
        for (String key : objectKeys) {
            try {
                storageService.delete(key);
            } catch (Exception e) {
                log.warn("After-commit storage delete failed, objectKey={} left as orphan: {}",
                        key, e.toString());
            }
        }
    }

    /** dest có phải là con/cháu của ancestorId không (đi ngược lên cha). */
    private boolean isDescendant(Folder dest, UUID ancestorId) {
        Folder cur = dest;
        while (cur != null) {
            if (cur.getId().equals(ancestorId)) {
                return true;
            }
            // parent LAZY — vẫn truy cập được trong @Transactional
            cur = cur.getParentFolder();
            if (cur != null) {
                // đảm bảo entity managed để lazy-load cha tiếp theo
                cur = folderRepository.findById(cur.getId()).orElse(null);
            }
        }
        return false;
    }
}
