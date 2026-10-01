package com.spring.backend.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.spring.backend.common.PageResponse;
import com.spring.backend.common.utils.StorageNameUtils;
import com.spring.backend.dto.folder.FolderCreateDto;
import com.spring.backend.dto.folder.FolderDto;
import com.spring.backend.dto.folder.FolderUpdateDto;
import com.spring.backend.entity.Folder;
import com.spring.backend.entity.User;
import com.spring.backend.exception.AppException;
import com.spring.backend.exception.ErrorCode;
import com.spring.backend.mapper.FolderMapper;
import com.spring.backend.repository.FolderRepository;
import com.spring.backend.repository.UserRepository;
import com.spring.backend.service.FolderService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FolderServiceImpl implements FolderService {
    private final FolderRepository repository;
    private final UserRepository userRepository;
    private final FolderMapper folderMapper;

    @Override
    public FolderDto create(FolderCreateDto dto, UUID ownerId) {
        if (!StorageNameUtils.isValidStorageName(dto.getName())) {
            throw new AppException(ErrorCode.INVALID_FOLDER_NAME);
        }

        // lấy danh sách folder thuộc userId hiện tại, chia làm 2 trường hợp
        // parent_folder là null (root) hoặc là thư mục con
        List<Folder> foldersByUser;
        if (dto.getParentId() == null) {
            foldersByUser = repository.findByOwnerIdAndParentFolderIsNull(ownerId);
        } else {
            foldersByUser = repository.findByOwnerIdAndParentFolderId(ownerId, dto.getParentId());
        }

        // hàm sinh tên mới nếu tên tồn tại (abc -> abc (1))
        List<String> folderNames = foldersByUser.stream().map(item -> item.getName()).collect(Collectors.toList());
        String newName = StorageNameUtils.generateUniqueName(folderNames, dto.getName());

        Folder parentFolder = null;
        if (dto.getParentId() != null) {
            parentFolder = repository.findById(dto.getParentId()).orElse(null);
            if (parentFolder == null) {
                throw new AppException(ErrorCode.FOLDER_NOT_FOUND);
            }
        }

        User owner = userRepository.findById(ownerId).orElse(null);
        if (owner == null) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }

        Folder newFolder = Folder.builder()
                .name(newName)
                .owner(owner)
                .parentFolder(parentFolder)
                .build();
        return folderMapper.toDto(repository.save(newFolder));
    }

    @Override
    public FolderDto update(FolderUpdateDto dto, UUID ownerId) {
        Folder currentFolder = repository.findById(dto.getId()).orElse(null);
        if (currentFolder == null) {
            throw new AppException(ErrorCode.FOLDER_NOT_FOUND);
        }
        if (currentFolder.getOwner() == null || !currentFolder.getOwner().getId().equals(ownerId)) {
            throw new AppException(ErrorCode.FOLDER_ACCESS_DENIED);
        }

        Folder parentFolder = null;
        if (dto.getParentId() != null) {
            parentFolder = repository.findById(dto.getParentId()).orElse(null);
            if (parentFolder == null) {
                throw new AppException(ErrorCode.FOLDER_NOT_FOUND);
            }
        }

        currentFolder.setParentFolder(parentFolder);
        currentFolder.setName(dto.getName());
        return folderMapper.toDto(repository.save(currentFolder));
    }

    @Override
    public void deleteSoft(List<UUID> ids, UUID ownerId) {
        repository.softDeleteMultipleFolders(ids, Instant.now(), ownerId);
    }

    @Override
    public FolderDto findById(UUID id) {
        return folderMapper.toDto(repository.findById(id).orElse(null));
    }

    
    @Override
    public PageResponse<FolderDto> findAllByOwnerIdInRoot(int pageNo, int pageSize, String sortBy, String sortDir, UUID ownerId) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.DESC.name())
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);
        Page<Folder> page = repository.findByOwnerIdAndParentFolderIsNull(pageable, ownerId);

        return PageResponse.from(page.map(folderMapper::toDto));
    }

    @Override
    public PageResponse<FolderDto> findAllByOwnerIdAndParentFolderId(int pageNo, int pageSize, String sortBy,
            String sortDir, UUID ownerId, UUID parentFolderId) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.DESC.name())
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);
        Page<Folder> page = repository.findByOwnerIdAndParentFolderId(pageable, ownerId, parentFolderId);
    
        return PageResponse.from(page.map(folderMapper::toDto));
    }

    

}
