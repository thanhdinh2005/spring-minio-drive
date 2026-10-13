
-- ========================================================
-- 1. USERS
-- =========================================================

INSERT INTO users (
    id, email, password_hash, display_name,
    role, status, plan, storage_used, storage_limit,
    created_at, updated_at
)
VALUES
(
    'a1000000-0000-4000-8000-000000000001',
    'nguyenvana@gmail.com',
    '$2a$10$DScNUF40WpE58tICci93nuhQkvJEg9v02KnfJi8fnReLZgL1W9OR2',
    'Nguyễn Văn A', 'ADMIN', 'ACTIVE', 'PREMIUM',
    419430400, 10737418240,
    now() - INTERVAL '30 days', now()
),
(
    'a1000000-0000-4000-8000-000000000002',
    'tranthib@gmail.com',
    '$2a$10$DScNUF40WpE58tICci93nuhQkvJEg9v02KnfJi8fnReLZgL1W9OR2',
    'Trần Thị B', 'USER', 'ACTIVE', 'FREE',
    52428800, 104857600,
    now() - INTERVAL '25 days', now()
),
(
    'a1000000-0000-4000-8000-000000000003',
    'levanc@gmail.com',
    '$2a$10$DScNUF40WpE58tICci93nuhQkvJEg9v02KnfJi8fnReLZgL1W9OR2',
    'Lê Văn C', 'USER', 'ACTIVE', 'PREMIUM',
    209715200, 10737418240,
    now() - INTERVAL '20 days', now()
),
(
    'a1000000-0000-4000-8000-000000000004',
    'phamthid@gmail.com',
    '$2a$10$DScNUF40WpE58tICci93nuhQkvJEg9v02KnfJi8fnReLZgL1W9OR2',
    'Phạm Thị D', 'USER', 'ACTIVE', 'FREE',
    0, 104857600,
    now() - INTERVAL '10 days', now()
),
(
    'a1000000-0000-4000-8000-000000000005',
    'hoangvane@gmail.com',
    '$2a$10$DScNUF40WpE58tICci93nuhQkvJEg9v02KnfJi8fnReLZgL1W9OR2',
    'Hoàng Văn E', 'USER', 'ACTIVE', 'FREE',
    10485760, 104857600,
    now() - INTERVAL '5 days', now()
)
ON CONFLICT (id) DO NOTHING;


-- =========================================================
-- 2. FOLDERS
-- parent_folder stores the parent folder UUID as VARCHAR.
-- NULL means the folder is at the root.
-- =========================================================

INSERT INTO folders (
    id, name, owner_id, parent_folder, created_at, updated_at
)
VALUES
-- Nguyễn Văn A
(
    'b2000000-0000-4000-8000-000000000001',
    'Work',
    'a1000000-0000-4000-8000-000000000001',
    NULL, now() - INTERVAL '29 days', now()
),
(
    'b2000000-0000-4000-8000-000000000002',
    'Reports',
    'a1000000-0000-4000-8000-000000000001',
    'b2000000-0000-4000-8000-000000000001',
    now() - INTERVAL '28 days', now()
),
(
    'b2000000-0000-4000-8000-000000000003',
    'Personal',
    'a1000000-0000-4000-8000-000000000001',
    NULL, now() - INTERVAL '20 days', now()
),

-- Trần Thị B
(
    'b2000000-0000-4000-8000-000000000004',
    'University',
    'a1000000-0000-4000-8000-000000000002',
    NULL, now() - INTERVAL '24 days', now()
),
(
    'b2000000-0000-4000-8000-000000000005',
    'Assignments',
    'a1000000-0000-4000-8000-000000000002',
    'b2000000-0000-4000-8000-000000000004',
    now() - INTERVAL '23 days', now()
),

-- Lê Văn C
(
    'b2000000-0000-4000-8000-000000000006',
    'Projects',
    'a1000000-0000-4000-8000-000000000003',
    NULL, now() - INTERVAL '18 days', now()
),
(
    'b2000000-0000-4000-8000-000000000007',
    'Source Code',
    'a1000000-0000-4000-8000-000000000003',
    'b2000000-0000-4000-8000-000000000006',
    now() - INTERVAL '15 days', now()
),

-- Phạm Thị D
(
    'b2000000-0000-4000-8000-000000000008',
    'Photos',
    'a1000000-0000-4000-8000-000000000004',
    NULL, now() - INTERVAL '8 days', now()
),

-- Hoàng Văn E
(
    'b2000000-0000-4000-8000-000000000009',
    'Documents',
    'a1000000-0000-4000-8000-000000000005',
    NULL, now() - INTERVAL '4 days', now()
)
ON CONFLICT (id) DO NOTHING;


-- =========================================================
-- 3. DOCUMENTS
-- object_key represents the object storage key.
-- These rows do not upload actual files to object storage.
-- =========================================================

INSERT INTO documents (
    id, name, folder_id, owner_id, object_key,
    size, mime_type, created_at, updated_at
)
VALUES
-- Nguyễn Văn A / Work
(
    'c3000000-0000-4000-8000-000000000001',
    'project-plan.pdf',
    'b2000000-0000-4000-8000-000000000001',
    'a1000000-0000-4000-8000-000000000001',
    'users/a1000000-0000-4000-8000-000000000001/project-plan.pdf',
    2097152, 'application/pdf',
    now() - INTERVAL '27 days', now()
),
(
    'c3000000-0000-4000-8000-000000000002',
    'annual-report.pdf',
    'b2000000-0000-4000-8000-000000000002',
    'a1000000-0000-4000-8000-000000000001',
    'users/a1000000-0000-4000-8000-000000000001/annual-report.pdf',
    5242880, 'application/pdf',
    now() - INTERVAL '25 days', now()
),
(
    'c3000000-0000-4000-8000-000000000003',
    'meeting-notes.docx',
    'b2000000-0000-4000-8000-000000000002',
    'a1000000-0000-4000-8000-000000000001',
    'users/a1000000-0000-4000-8000-000000000001/meeting-notes.docx',
    1048576,
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    now() - INTERVAL '20 days', now()
),

-- Trần Thị B / University
(
    'c3000000-0000-4000-8000-000000000004',
    'lecture-notes.pdf',
    'b2000000-0000-4000-8000-000000000004',
    'a1000000-0000-4000-8000-000000000002',
    'users/a1000000-0000-4000-8000-000000000002/lecture-notes.pdf',
    3145728, 'application/pdf',
    now() - INTERVAL '22 days', now()
),
(
    'c3000000-0000-4000-8000-000000000005',
    'assignment-01.docx',
    'b2000000-0000-4000-8000-000000000005',
    'a1000000-0000-4000-8000-000000000002',
    'users/a1000000-0000-4000-8000-000000000002/assignment-01.docx',
    524288,
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    now() - INTERVAL '15 days', now()
),

-- Lê Văn C / Projects
(
    'c3000000-0000-4000-8000-000000000006',
    'architecture.png',
    'b2000000-0000-4000-8000-000000000006',
    'a1000000-0000-4000-8000-000000000003',
    'users/a1000000-0000-4000-8000-000000000003/architecture.png',
    2097152, 'image/png',
    now() - INTERVAL '16 days', now()
),
(
    'c3000000-0000-4000-8000-000000000007',
    'README.md',
    'b2000000-0000-4000-8000-000000000007',
    'a1000000-0000-4000-8000-000000000003',
    'users/a1000000-0000-4000-8000-000000000003/README.md',
    16384, 'text/markdown',
    now() - INTERVAL '12 days', now()
),

-- Hoàng Văn E / Documents
(
    'c3000000-0000-4000-8000-000000000008',
    'resume.pdf',
    'b2000000-0000-4000-8000-000000000009',
    'a1000000-0000-4000-8000-000000000005',
    'users/a1000000-0000-4000-8000-000000000005/resume.pdf',
    1048576, 'application/pdf',
    now() - INTERVAL '3 days', now()
)
ON CONFLICT (id) DO NOTHING;


-- =========================================================
-- 4. PERMISSIONS
-- Assumes permission_type supports READ and WRITE.
-- Each permission targets either a user or a public token.
-- =========================================================

INSERT INTO permissions (
    id, folder_id, shared_with_user_id,
    permission_type, public_token, granted_by_id,
    created_at, updated_at
)
VALUES
-- Nguyễn Văn A shares Work with Trần Thị B (read-only)
(
    'd4000000-0000-4000-8000-000000000001',
    'b2000000-0000-4000-8000-000000000001',
    'a1000000-0000-4000-8000-000000000002',
    'READ', NULL,
    'a1000000-0000-4000-8000-000000000001',
    now() - INTERVAL '10 days', now()
),

-- Nguyễn Văn A shares Reports with Lê Văn C (write access)
(
    'd4000000-0000-4000-8000-000000000002',
    'b2000000-0000-4000-8000-000000000002',
    'a1000000-0000-4000-8000-000000000003',
    'WRITE', NULL,
    'a1000000-0000-4000-8000-000000000001',
    now() - INTERVAL '8 days', now()
),

-- Trần Thị B shares Assignments publicly
(
    'd4000000-0000-4000-8000-000000000003',
    'b2000000-0000-4000-8000-000000000005',
    NULL,
    'READ',
    'demo-public-assignment-001',
    'a1000000-0000-4000-8000-000000000002',
    now() - INTERVAL '5 days', now()
),

-- Lê Văn C shares Projects with Hoàng Văn E
(
    'd4000000-0000-4000-8000-000000000004',
    'b2000000-0000-4000-8000-000000000006',
    'a1000000-0000-4000-8000-000000000005',
    'READ', NULL,
    'a1000000-0000-4000-8000-000000000003',
    now() - INTERVAL '2 days', now()
)
ON CONFLICT (id) DO NOTHING;
