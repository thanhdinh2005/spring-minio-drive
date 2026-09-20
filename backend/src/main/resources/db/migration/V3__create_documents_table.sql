CREATE TABLE documents (
    id          UUID PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    folder_id   UUID         NOT NULL REFERENCES folders(id),
    owner_id    UUID         NOT NULL REFERENCES users(id),
    object_key  VARCHAR(512) NOT NULL,
    size        BIGINT       NOT NULL,
    mime_type   VARCHAR(100),
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_documents_folder_id ON documents(folder_id);
CREATE INDEX idx_documents_owner_id ON documents(owner_id);
