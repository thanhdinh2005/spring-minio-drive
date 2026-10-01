-- Bổ sung soft-delete cho folders, đồng bộ với Folder entity (isDeleted, deletedAt).
-- Không sửa V1-V5 vì Flyway đã áp dụng chúng (sửa sẽ lệch checksum).
ALTER TABLE folders ADD COLUMN IF NOT EXISTS is_deleted INTEGER NOT NULL DEFAULT 0;
ALTER TABLE folders ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
