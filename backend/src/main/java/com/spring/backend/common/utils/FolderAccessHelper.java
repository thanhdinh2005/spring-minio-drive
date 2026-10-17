package com.spring.backend.common.utils;

import java.util.UUID;

import com.spring.backend.entity.Folder;
import com.spring.backend.exception.AppException;
import com.spring.backend.exception.ErrorCode;

/**
 * Check quyền tạm thời: chỉ owner.
 *
 * TODO (Module 3): mở rộng thành quyền kế thừa — user có quyền VIEW/EDIT
 * nếu là owner HOẶC có Permission VIEW/EDIT trực tiếp trên folder HOẶC
 * kế thừa từ bất kỳ folder tổ tiên nào. Hàm requireEdit/requireView hiện
 * tại cố ý chỉ check owner để giữ đúng quyết định "chỉ check owner tạm".
 */
public final class FolderAccessHelper {

    private FolderAccessHelper() {
    }

    public static void requireOwner(Folder folder, UUID userId) {
        if (folder == null) {
            throw new AppException(ErrorCode.FOLDER_NOT_FOUND);
        }
        if (folder.getOwner() == null || !folder.getOwner().getId().equals(userId)) {
            throw new AppException(ErrorCode.FOLDER_ACCESS_DENIED);
        }
    }

    /** Quyền xem: tạm = owner (TODO Module 3: + VIEW/EDIT kế thừa). */
    public static void requireView(Folder folder, UUID userId) {
        requireOwner(folder, userId);
    }

    /** Quyền sửa: tạm = owner (TODO Module 3: + EDIT kế thừa). */
    public static void requireEdit(Folder folder, UUID userId) {
        requireOwner(folder, userId);
    }
}
