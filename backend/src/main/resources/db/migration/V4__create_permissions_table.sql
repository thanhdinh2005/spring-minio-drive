CREATE TABLE permissions (
    id                     UUID PRIMARY KEY,
    folder_id              UUID         NOT NULL REFERENCES folders(id),
    shared_with_user_id    UUID         REFERENCES users(id),
    permission_type        VARCHAR(20)  NOT NULL,
    public_token           VARCHAR(64)  UNIQUE,
    granted_by_id          UUID         NOT NULL REFERENCES users(id),
    created_at             TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT chk_permission_share_target CHECK (
        (shared_with_user_id IS NOT NULL AND public_token IS NULL)
        OR
        (shared_with_user_id IS NULL AND public_token IS NOT NULL)
    )
);

CREATE INDEX idx_permissions_folder_id ON permissions(folder_id);
CREATE INDEX idx_permissions_shared_with_user_id ON permissions(shared_with_user_id);
CREATE INDEX idx_permissions_public_token ON permissions(public_token);
