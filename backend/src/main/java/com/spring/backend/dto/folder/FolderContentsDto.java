package com.spring.backend.dto.folder;

import java.util.List;

import com.spring.backend.dto.document.DocumentDto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FolderContentsDto {
    private FolderDto folder;
    private List<FolderDto> subFolders;
    private List<DocumentDto> documents;
}
