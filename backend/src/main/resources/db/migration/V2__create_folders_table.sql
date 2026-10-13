CREATE TABLE folders (
    id                UUID PRIMARY KEY,
    name              VARCHAR(255) NOT NULL,
    owner_id          UUID         NOT NULL REFERENCES users(id),
    parent_folder     VARCHAR(255),
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_folders_owner_id ON folders(owner_id);
