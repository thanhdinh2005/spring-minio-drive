WITH
-- 1. SEED USERS (5 người dùng)
inserted_users AS (
    INSERT INTO users (
        id, email, password_hash, display_name, role, status, plan, storage_used, storage_limit, created_at, updated_at
    ) VALUES
    (gen_random_uuid(), 'nguyenvana@gmail.com', '$2a$10$DScNUF40WpE58tICci93nuhQkvJEg9v02KnfJi8fnReLZgL1W9OR2', 'Nguyễn Văn A', 'ADMIN', 'ACTIVE', 'PREMIUM', 419430400, 10737418240, now() - INTERVAL '30 days', now()),
    (gen_random_uuid(), 'tranthib@gmail.com',  '$2a$10$DScNUF40WpE58tICci93nuhQkvJEg9v02KnfJi8fnReLZgL1W9OR2', 'Trần Thị B',  'USER',  'ACTIVE', 'FREE',  52428800,  104857600,   now() - INTERVAL '25 days', now()),
    (gen_random_uuid(), 'levanc@gmail.com',     '$2a$10$DScNUF40WpE58tICci93nuhQkvJEg9v02KnfJi8fnReLZgL1W9OR2', 'Lê Văn C',    'USER',  'ACTIVE', 'PREMIUM', 209715200, 10737418240,  now() - INTERVAL '20 days', now()),
    (gen_random_uuid(), 'phamthid@gmail.com',  '$2a$10$DScNUF40WpE58tICci93nuhQkvJEg9v02KnfJi8fnReLZgL1W9OR2', 'Phạm Thị D',  'USER',  'ACTIVE','FREE', 0,         104857600,   now() - INTERVAL '10 days', now()),
    (gen_random_uuid(), 'hoangvane@gmail.com',  '$2a$10$DScNUF40WpE58tICci93nuhQkvJEg9v02KnfJi8fnReLZgL1W9OR2', 'Hoàng Văn E',  'USER',  'ACTIVE', 'FREE',  10485760,  104857600,   now() - INTERVAL '5 days',  now())
    RETURNING id, email
),

-- Select các ID cụ thể của User để dùng gán thư mục
u_a AS (SELECT id FROM inserted_users WHERE email = 'nguyenvana@gmail.com'),
u_b AS (SELECT id FROM inserted_users WHERE email = 'tranthib@gmail.com'),
u_c AS (SELECT id FROM inserted_users WHERE email = 'levanc@gmail.com'),

-- 2. SEED ROOT FOLDERS (Thư mục gốc)
inserted_root_folders AS (
    INSERT INTO folders (id, name, owner_id, parent_folder_id, created_at, updated_at) VALUES
    (gen_random_uuid(), 'Tài liệu Công ty',   (SELECT id FROM u_a), NULL, now() - INTERVAL '28 days', now()),
    (gen_random_uuid(), 'Dự án Marketing Q4', (SELECT id FROM u_b), NULL, now() - INTERVAL '24 days', now()),
    (gen_random_uuid(), 'Thiết kế & Layout',  (SELECT id FROM u_c), NULL, now() - INTERVAL '19 days', now())
    RETURNING id, name, owner_id
),

-- Select các ID của Root Folder
f_company AS (SELECT id FROM inserted_root_folders WHERE name = 'Tài liệu Công ty'),
f_marketing AS (SELECT id FROM inserted_root_folders WHERE name = 'Dự án Marketing Q4'),

-- 3. SEED SUB FOLDERS (Thư mục con - có parent_folder_id)
inserted_sub_folders AS (
    INSERT INTO folders (id, name, owner_id, parent_folder_id, created_at, updated_at) VALUES
    (gen_random_uuid(), 'Hợp đồng & Pháp lý', (SELECT id FROM u_a), (SELECT id FROM f_company),   now() - INTERVAL '27 days', now()),
    (gen_random_uuid(), 'Báo cáo Nhân sự',    (SELECT id FROM u_a), (SELECT id FROM f_company),   now() - INTERVAL '26 days', now()),
    (gen_random_uuid(), 'Hình ảnh Banner',    (SELECT id FROM u_b), (SELECT id FROM f_marketing), now() - INTERVAL '22 days', now())
    RETURNING id, name, owner_id
),

-- gom tất cả folder IDs lại để dùng cho bảng documents
all_folders AS (
    SELECT id, name, owner_id FROM inserted_root_folders
    UNION ALL
    SELECT id, name, owner_id FROM inserted_sub_folders
),
f_contract AS (SELECT id, owner_id FROM all_folders WHERE name = 'Hợp đồng & Pháp lý'),
f_hr AS (SELECT id, owner_id FROM all_folders WHERE name = 'Báo cáo Nhân sự'),
f_banner AS (SELECT id, owner_id FROM all_folders WHERE name = 'Hình ảnh Banner'),
f_design AS (SELECT id, owner_id FROM all_folders WHERE name = 'Thiết kế & Layout')

-- 4. SEED DOCUMENTS (Tài liệu lưu trong các thư mục tương ứng)
INSERT INTO documents (id, name, folder_id, owner_id, object_key, size, mime_type, created_at, updated_at) VALUES
-- Documents trong "Hợp đồng & Pháp lý" (Owner: Nguyễn Văn A)
(gen_random_uuid(), 'HDLD_2026_NguyenVanA.pdf', (SELECT id FROM f_contract), (SELECT owner_id FROM f_contract), 'docs/contracts/hdld_a.pdf', 2458112, 'application/pdf', now() - INTERVAL '20 days', now()),
(gen_random_uuid(), 'Dieu_khoan_su_dung_v2.docx', (SELECT id FROM f_contract), (SELECT owner_id FROM f_contract), 'docs/contracts/terms_v2.docx', 1048576, 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', now() - INTERVAL '18 days', now()),

-- Documents trong "Báo cáo Nhân sự" (Owner: Nguyễn Văn A)
(gen_random_uuid(), 'Bao_cao_nhan_su_T8.xlsx', (SELECT id FROM f_hr), (SELECT owner_id FROM f_hr), 'docs/hr/hr_report_08.xlsx', 3145728, 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', now() - INTERVAL '15 days', now()),
(gen_random_uuid(), 'Danh_sach_nhan_vien.csv', (SELECT id FROM f_hr), (SELECT owner_id FROM f_hr), 'docs/hr/employees.csv', 512000, 'text/csv', now() - INTERVAL '12 days', now()),

-- Documents trong "Hình ảnh Banner" (Owner: Trần Thị B)
(gen_random_uuid(), 'Banner_Khuyen_Mai_Q4.png', (SELECT id FROM f_banner), (SELECT owner_id FROM f_banner), 'media/banners/promo_q4.png', 5242880, 'image/png', now() - INTERVAL '10 days', now()),
(gen_random_uuid(), 'Key_Visual_Campaign.psd', (SELECT id FROM f_banner), (SELECT owner_id FROM f_banner), 'media/banners/kv_camp.psd', 47185920, 'application/x-photoshop', now() - INTERVAL '8 days', now()),

-- Documents trong "Thiết kế & Layout" (Owner: Lê Văn C)
(gen_random_uuid(), 'App_UI_Mobile_v3.fig', (SELECT id FROM f_design), (SELECT owner_id FROM f_design), 'designs/app_v3.fig', 15728640, 'application/octet-stream', now() - INTERVAL '5 days', now()),
(gen_random_uuid(), 'Guidelines_Branding.pdf', (SELECT id FROM f_design), (SELECT owner_id FROM f_design), 'designs/guidelines.pdf', 8388608, 'application/pdf', now() - INTERVAL '2 days', now());
