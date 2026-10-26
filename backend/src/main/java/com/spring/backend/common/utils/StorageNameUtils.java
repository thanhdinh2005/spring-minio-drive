package com.spring.backend.common.utils;

import java.util.List;
import java.util.regex.Pattern;

public class StorageNameUtils {
    private static final Pattern INVALID_CHARS = Pattern.compile("[<>:\"/\\\\|?*#%&+{}^~\\[\\]`$@=\\r\\n\\t]");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}]");

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
        return !INVALID_CHARS.matcher(trimmed).find() && !CONTROL_CHARS.matcher(trimmed).find();
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

    /**
     * Làm sạch tên file để an toàn khi lưu trữ trên storage.
     * Loại bỏ ký tự không hợp lệ, cắt ngắn nếu quá dài.
     */
    public static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return "unnamed";
        }

        String cleaned = fileName.trim();

        // Thay thế ký tự không hợp lệ bằng dấu gạch dưới
        cleaned = INVALID_CHARS.matcher(cleaned).replaceAll("_");
        cleaned = CONTROL_CHARS.matcher(cleaned).replaceAll("_");

        // Loại bỏ khoảng trắng thừa
        cleaned = cleaned.replaceAll("\\s+", " ");

        // Cắt ngắn nếu quá dài (giữ lại extension)
        if (cleaned.length() > 200) {
            int lastDot = cleaned.lastIndexOf('.');
            if (lastDot > 0 && lastDot < cleaned.length() - 1) {
                String namePart = cleaned.substring(0, lastDot);
                String extPart = cleaned.substring(lastDot);
                if (namePart.length() > 200 - extPart.length()) {
                    namePart = namePart.substring(0, 200 - extPart.length());
                }
                cleaned = namePart + extPart;
            } else {
                cleaned = cleaned.substring(0, 200);
            }
        }

        // Đảm bảo không rỗng sau khi làm sạch
        if (cleaned.isEmpty() || cleaned.equals(".") || cleaned.equals("..")) {
            return "unnamed";
        }

        return cleaned;
    }
}
