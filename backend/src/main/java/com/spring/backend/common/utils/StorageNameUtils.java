package com.spring.backend.common.utils;

import java.util.List;

public class StorageNameUtils {
    public static boolean isValidStorageName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        String trimmed = name.trim();

        // 1. Giới hạn độ dài tối đa 255 ký tự
        if (trimmed.length() > 255) {
            return false;
        }

        // 2. Không cho phép kết thúc bằng dấu chấm (Windows không hỗ trợ)
        if (trimmed.endsWith(".")) {
            return false;
        }

        // 3. Kiểm tra ký tự cấm hệ thống (< > : " / \ | ? *)
        //    và ký tự nguy hiểm URL (# % & + { } ^ ~ [ ] ` $ @ =)
        //    Lưu ý: không dùng \0 trong regex (Java báo Illegal octal escape sequence).
        return !trimmed.matches(".*[<>:\"/\\\\|?*#%&+{}^~\\[\\]`$@=\\r\\n\\t].*");
    }

    public static String generateUniqueName(List<String> existingNames, String newName) {
        if (existingNames == null || newName == null) {
            return newName;
        }

        String candidateName = newName.trim();
        int count = 1;

        // Vòng lặp kiểm tra: Hễ tên vẫn còn nằm trong danh sách thì tự cộng số đếm (1), (2),...
        while (existingNames.contains(candidateName)) {
            candidateName = newName.trim() + " (" + count + ")";
            count++;
        }

        return candidateName;
    }
}
