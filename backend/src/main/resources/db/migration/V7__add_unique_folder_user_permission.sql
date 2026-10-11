ALTER TABLE permissions
ADD CONSTRAINT uq_permissions_folder_user
UNIQUE (folder_id, shared_with_user_id);