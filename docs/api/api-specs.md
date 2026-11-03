# API Specification — spring-minio-drive

Tài liệu mô tả REST API của backend, tổng hợp trực tiếp từ các controller trong
`backend/src/main/java/com/spring/backend/controller/`.

- **Base URL:** `http://localhost:8081/api` — toàn bộ endpoint nằm dưới context path `/api`
- **Phiên bản:** `0.0.1-SNAPSHOT`
- **Định dạng:** `application/json` (riêng upload dùng `multipart/form-data`)
- **Swagger UI (profile `dev`):** `http://localhost:8081/api/swagger-ui.html`
- **Health check:** `http://localhost:8081/api/actuator/health`

## 1. Xác thực

API dùng JWT Bearer token, không dùng session/cookie.

```http
Authorization: Bearer <accessToken>
```

- Token lấy từ `POST /auth/login`, hết hạn theo `TOKEN_EXP` (mặc định 3 600 000 ms).
- Token chứa `sub` (email) và claim `userId`; ký HS256 bằng `SECRET_KEY`.
- Endpoint public (không cần token): `/auth/**`, `/public/**`, `/swagger-ui/**`, `/api-docs/**`, `/actuator/**`.
- Endpoint `/admin/**` yêu cầu role `ADMIN`; các endpoint còn lại yêu cầu đã đăng nhập.
- Tài khoản có `status = LOCKED` không được xác thực: request kèm token sẽ bị coi là chưa đăng nhập (401).
- Request thiếu/sai token trả **401**; không đủ quyền trả **403**.

## 2. Mô hình phân quyền folder

Folder là đơn vị phân quyền; document thừa hưởng quyền từ folder chứa nó.

| `PermissionType` | Ý nghĩa |
| :--- | :--- |
| `NONE` | Không có quyền |
| `VIEW` | Xem folder/nội dung, tải và preview document |
| `EDIT` | Toàn bộ quyền của `VIEW`, cộng thêm upload/sửa/xoá document, tạo folder con, di chuyển |
| `OWNER` | Toàn bộ quyền của `EDIT`, cộng thêm chia sẻ, tạo/thu hồi public link, xoá folder |

- Quyền hiệu lực được tính bằng cách duyệt folder hiện tại và toàn bộ folder tổ tiên;
  `EDIT` được ưu tiên hơn `VIEW`, owner của bất kỳ folder tổ tiên nào cũng có `OWNER`.
- Request body chỉ chấp nhận `VIEW` hoặc `EDIT`; `NONE`/`OWNER` bị từ chối.
- Quyền chia sẻ (`POST/GET /folders/{id}/permissions`, public link) chỉ dành cho **owner trực tiếp**
  của folder (`folder.owner`), không áp dụng cho owner của folder cha.

| Hành động | VIEW | EDIT | OWNER |
| :--- | :---: | :---: | :---: |
| Xem folder, nội dung, path | ✓ | ✓ | ✓ |
| Tải / preview document | ✓ | ✓ | ✓ |
| Upload, sửa, xoá, copy document | | ✓ | ✓ |
| Tạo folder con, sửa/di chuyển folder | | ✓ | ✓ |
| Xoá folder (hard delete cả cây) | | | ✓ |
| Chia sẻ, xem quyền, public link | | | ✓ |

> Danh sách folder (`/folders/root`, `/folders/filter`, `/folders/tree` khi không truyền
> `parentId`) và `/documents/filter` chỉ trả tài nguyên **do chính user sở hữu**. Folder
> được chia sẻ truy cập qua `GET /shared-with-me` rồi gọi trực tiếp theo `id`.

## 3. Quy ước response

### 3.1 Envelope

Mọi response (trừ lỗi phát sinh ở tầng Security filter) đều bọc trong `AppResponse`:

```json
{
  "success": true,
  "status": 200,
  "data": {},
  "timestamp": "2026-11-03T07:20:00.123Z"
}
```

| Field | Kiểu | Ghi chú |
| :--- | :--- | :--- |
| `success` | boolean | `true` thành công, `false` lỗi |
| `status` | int | Trùng HTTP status |
| `code` | string | Chỉ có khi lỗi (mã lỗi) |
| `message` | string | Chỉ có khi lỗi |
| `traceId` | string | Chỉ có khi lỗi (8 ký tự, dùng để tra log) |
| `data` | object / array / null | Chỉ có khi thành công |
| `timestamp` | string (ISO-8601) | Thời điểm response |

Các field `null` bị lược bỏ khỏi JSON (`@JsonInclude(NON_NULL)`); vì vậy response không có
dữ liệu (ví dụ xoá thành công) sẽ **không có** field `data`, và response thành công không có
`code`/`message`/`traceId`.

### 3.2 Lỗi

```json
{
  "success": false,
  "status": 404,
  "code": "ERR_FOLDER_001",
  "message": "Folder not found",
  "traceId": "3f9c2a1b",
  "timestamp": "2026-11-03T07:20:00.123Z"
}
```

- Lỗi validate `@Valid` trả `ERR_REQ_001` với `message` dạng `"name: Folder name is required"`.
- Lỗi nghiệp vụ dùng `VALIDATION_ERROR` trả message mặc định `"Invalid request"` (chi tiết chỉ ghi log).

### 3.3 Phân trang

Có 2 dạng phân trang trong hệ thống:

**Spring `Page`** — dùng cho `GET /folders/filter` và `GET /documents/filter`:

```json
{
  "content": [],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 10,
    "offset": 0,
    "paged": true,
    "unpaged": false,
    "sort": { "empty": false, "sorted": true, "unsorted": false }
  },
  "totalElements": 42,
  "totalPages": 5,
  "last": false,
  "size": 10,
  "number": 0,
  "sort": { "empty": false, "sorted": true, "unsorted": false },
  "first": true,
  "numberOfElements": 10,
  "empty": false
}
```

**`PageResponse`** — dùng cho `GET /admin/users`:

```json
{
  "content": [],
  "pageNo": 0,
  "pageSize": 10,
  "totalElements": 42,
  "totalPages": 5,
  "last": false
}
```

Quy ước chung: `pageNo`/`number` bắt đầu từ `0`; `pageSize` mặc định `10`;
`pageNo < 0` được đưa về `0`; `pageSize < 1` được đưa về `1`.

## 4. Mã lỗi

| HTTP | Code | Message | Dùng ở |
| :--- | :--- | :--- | :--- |
| 404 | `ERR_USER_001` | User not found | Chia sẻ, admin, user |
| 409 | `ERR_USER_002` | Email already exists | Đăng ký |
| 403 | `ERR_USER_003` | Account is locked | *(chưa dùng)* |
| 401 | `ERR_USER_004` | Invalid email or password | *(chưa dùng — login trả `ERR_REQ_005`)* |
| 404 | `ERR_FOLDER_001` | Folder not found | Folder, chia sẻ |
| 403 | `ERR_FOLDER_002` | You don't have access to this folder | Mọi kiểm tra quyền folder |
| 400 | `ERR_FOLDER_003` | Folder name contains invalid characters | Tạo/đổi tên folder, đổi tên document |
| 404 | `ERR_DOC_001` | Document not found | Document |
| 413 | `ERR_DOC_002` | File exceeds storage limit | Upload vượt 10 MB hoặc vượt quota |
| 403 | `ERR_DOC_003` | You don't have access to this document | Sharing details của document |
| 404 | `ERR_PERM_001` | Permission not found | Sửa/thu hồi quyền |
| 400 | `ERR_PERM_002` | A recipient is required and must not be the folder owner | Chia sẻ sai đối tượng |
| 403 | `ERR_PERM_003` | Only the owner can share this folder | Public link / chia sẻ |
| 400 | `ERR_PERM_004` | Share permission must be VIEW or EDIT | *(dự phòng — validate DTO chặn trước)* |
| 400 | `ERR_PERM_005` | Cannot share a folder with its owner | *(chưa dùng — dùng `ERR_PERM_002`)* |
| 400 | `ERR_REQ_001` | Invalid request | Validate `@Valid`, lỗi nghiệp vụ dạng 400 |
| 400 | `ERR_REQ_002` | Malformed request body | JSON sai/không đọc được |
| 405 | `ERR_REQ_003` | HTTP method not supported | Sai HTTP method |
| 404 | `ERR_REQ_004` | Resource not found | Endpoint không tồn tại |
| 401 | `ERR_REQ_005` | Incorrect email or password | Login sai |
| 409 | `ERR_PERM_006` | A permission already exists for this folder and user | Chia sẻ trùng |
| 404 | `ERR_PERM_007` | Public link not found or revoked | Public link sai/thu hồi |
| 500 | `ERR_SYS_001` | An unexpected error occurred. Please try again later. | Lỗi không lường trước |
| 500 | `ERR_SYS_002` | Storage operation failed | Lỗi S3/RustFS |

## 5. Enum

| Enum | Giá trị | Ghi chú |
| :--- | :--- | :--- |
| `Role` | `ADMIN`, `USER` | Quyền hệ thống |
| `Plan` | `FREE`, `PREMIUM` | Gói dung lượng (FREE 100 MB, PREMIUM 1 GB) |
| `Status` | `ACTIVE`, `LOCKED` | Trạng thái tài khoản |
| `PermissionType` | `NONE`, `VIEW`, `EDIT`, `OWNER` | Request chỉ nhận `VIEW`/`EDIT` |

## 6. Schema dùng chung

### 6.1 `TokenResponse`

| Field | Kiểu | Ghi chú |
| :--- | :--- | :--- |
| `accessToken` | string | JWT HS256 |
| `expiredIn` | number | Thời hạn token, **tính bằng giây** (tên field là `expiredIn`) |

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "expiredIn": 604800
}
```

### 6.2 `RegisterResponse`

| Field | Kiểu |
| :--- | :--- |
| `id` | UUID |
| `email` | string |
| `displayName` | string |
| `plan` | `Plan` |

### 6.3 `UserResponse`

| Field | Kiểu |
| :--- | :--- |
| `id` | UUID |
| `email` | string |
| `displayName` | string |
| `role` | `Role` |
| `plan` | `Plan` |

### 6.4 `StorageUsageResponse`

| Field | Kiểu | Ghi chú |
| :--- | :--- | :--- |
| `storageUsed` | number | Tổng `SUM(documents.size)` của owner |
| `storageLimit` | number | Byte |
| `plan` | `Plan` | |

### 6.5 `UpgradeResponse`

| Field | Kiểu |
| :--- | :--- |
| `plan` | `Plan` |
| `storageLimit` | number (byte) |

### 6.6 `AdminUserResponse`

| Field | Kiểu |
| :--- | :--- |
| `id` | UUID |
| `email` | string |
| `displayName` | string |
| `status` | `Status` |
| `plan` | `Plan` |
| `createdAt` | ISO-8601 |

### 6.7 `UpdateUserStatusResponse`

| Field | Kiểu |
| :--- | :--- |
| `id` | UUID |
| `status` | `Status` |

### 6.8 `FolderResponse`

| Field | Kiểu | Ghi chú |
| :--- | :--- | :--- |
| `id` | UUID | |
| `name` | string | Tên duy nhất trong cấp |
| `parentId` | UUID / null | `null` nếu là folder gốc |
| `parentName` | string / null | |
| `ownerId` | UUID | |
| `createdAt` | ISO-8601 | |
| `updatedAt` | ISO-8601 | |

```json
{
  "id": "8f14e45f-ea1c-4b7e-9d3a-2b5c6d7e8f90",
  "name": "Tài liệu",
  "parentId": null,
  "parentName": null,
  "ownerId": "0b6f1c2d-3e4a-4b5c-8d7e-9f0a1b2c3d4e",
  "createdAt": "2026-11-03T07:00:00Z",
  "updatedAt": "2026-11-03T07:00:00Z"
}
```

### 6.9 `FolderContentsResponse`

| Field | Kiểu | Ghi chú |
| :--- | :--- | :--- |
| `folder` | object | `{ id, name }` của folder đang xem |
| `items` | `ContentItem[]` | Folder và document trộn chung |
| `pageNo` | int | |
| `pageSize` | int | |
| `totalElements` | number | |
| `totalPages` | int | |
| `last` | boolean | |

`ContentItem`:

| Field | Kiểu | Ghi chú |
| :--- | :--- | :--- |
| `itemType` | string | `FOLDER` hoặc `DOCUMENT` |
| `id` | UUID | |
| `name` | string | |
| `hasChildren` | boolean | Folder: có folder con; Document: luôn `true` |
| `size` | number | Chỉ có giá trị với `DOCUMENT` (null với folder) |
| `mimeType` | string | Chỉ có giá trị với `DOCUMENT` (null với folder) |
| `itemUrl` | string | Chỉ có khi xem qua public link: `folders/{id}` hoặc `documents/{id}` (null với folder gốc) |
| `downloadUrl` | string | Chỉ có khi xem qua public link: presigned URL (300 s) |
| `previewUrl` | string / null | Chỉ có khi xem qua public link và file preview được |

### 6.10 `FolderTreeResponse`

| Field | Kiểu | Ghi chú |
| :--- | :--- | :--- |
| `folders` | `FolderTreeNode[]` | |

`FolderTreeNode`:

| Field | Kiểu | Ghi chú |
| :--- | :--- | :--- |
| `id` | **string** | Khác các schema khác: trả dạng chuỗi UUID |
| `name` | string | |
| `hasChildren` | boolean | |
| `children` | array | Hiện luôn là `[]`; client gọi lại `GET /folders/tree?parentId=...` để lấy cấp con |

### 6.11 `FolderPathResponse`

| Field | Kiểu | Ghi chú |
| :--- | :--- | :--- |
| `path` | `{ id, name }[]` | Từ folder gốc đến folder hiện tại (bao gồm chính nó) |

### 6.12 `DocumentResponse`

| Field | Kiểu | Ghi chú |
| :--- | :--- | :--- |
| `id` | UUID | |
| `name` | string | |
| `size` | number | Byte |
| `mimeType` | string | |
| `objectKey` | string | Key nội bộ trên storage |
| `folderId` | UUID | |
| `ownerId` | UUID | |
| `createdAt` | ISO-8601 | |
| `updatedAt` | ISO-8601 | |

```json
{
  "id": "5c2a1b3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d",
  "name": "bao-cao.pdf",
  "size": 204800,
  "mimeType": "application/pdf",
  "objectKey": "0b6f1c2d-.../8f14e45f-.../ab12cd34-bao-cao.pdf",
  "folderId": "8f14e45f-ea1c-4b7e-9d3a-2b5c6d7e8f90",
  "ownerId": "0b6f1c2d-3e4a-4b5c-8d7e-9f0a1b2c3d4e",
  "createdAt": "2026-11-03T07:10:00Z",
  "updatedAt": "2026-11-03T07:10:00Z"
}
```

### 6.13 `PresignedUrlResponse`

| Field | Kiểu | Ghi chú |
| :--- | :--- | :--- |
| `url` | string | Presigned URL tới S3/RustFS |
| `expiresIn` | number | Giây (hiện là `300`) |

### 6.14 `SharePermissionResponse`

| Field | Kiểu |
| :--- | :--- |
| `id` | UUID |
| `folderId` | UUID |
| `sharedWithUserId` | UUID |
| `permissionType` | `PermissionType` |

### 6.15 `DirectPermissionResponse`

| Field | Kiểu | Ghi chú |
| :--- | :--- | :--- |
| `id` | UUID | ID của permission |
| `sharedWithUser` | object | `{ id, email }` |
| `permissionType` | `PermissionType` | `VIEW`/`EDIT` |

### 6.16 `PublicLinkResponse`

| Field | Kiểu | Ghi chú |
| :--- | :--- | :--- |
| `publicToken` | string | Token URL-safe base64 (43 ký tự) |
| `publicUrl` | string | Ví dụ `http://localhost:8081/api/public/folders/{token}` |

### 6.17 `SharedWithMeResponse`

| Field | Kiểu | Ghi chú |
| :--- | :--- | :--- |
| `folderId` | UUID | |
| `name` | string | Tên folder được chia sẻ |
| `permissionType` | `PermissionType` | |
| `sharedBy` | string | Email người chia sẻ |

### 6.18 `SharingDetailsResponse`

| Field | Kiểu | Ghi chú |
| :--- | :--- | :--- |
| `resourceId` | UUID | Folder hoặc document |
| `resourceName` | string | |
| `ownerId` | UUID | |
| `users` | `EffectivePermissionResponse[]` | Danh sách người được chia sẻ |

### 6.19 `EffectivePermissionResponse`

| Field | Kiểu | Ghi chú |
| :--- | :--- | :--- |
| `userId` | UUID | |
| `userName` | string | `displayName` |
| `userEmail` | string | |
| `effectivePermission` | `PermissionType` | Quyền cao nhất sau khi hợp nhất |
| `sources` | `PermissionSourceResponse[]` | Các nguồn quyền |

### 6.20 `PermissionSourceResponse`

| Field | Kiểu | Ghi chú |
| :--- | :--- | :--- |
| `permissionId` | UUID | |
| `folderId` | UUID | Folder được cấp quyền |
| `folderName` | string | |
| `permission` | `PermissionType` | Quyền tại nguồn đó |
| `inherited` | boolean | `false`: cấp trực tiếp trên folder đang xem; `true`: kế thừa từ folder cha |
| `grantedById` | UUID | |

## 7. Tổng hợp endpoint

| # | Method | Path | Xác thực | Quyền | Mô tả |
| :---: | :--- | :--- | :--- | :--- | :--- |
| 1 | POST | `/auth/register` | Public | — | Đăng ký |
| 2 | POST | `/auth/login` | Public | — | Đăng nhập, lấy JWT |
| 3 | GET | `/users/me` | Bearer | User | Hồ sơ cá nhân |
| 4 | GET | `/users/me/storage` | Bearer | User | Dung lượng đã dùng |
| 5 | GET | `/users/me/upgrade` | Bearer | User | Nâng cấp PREMIUM *(có side effect)* |
| 6 | GET | `/admin/users` | Bearer | ADMIN | Danh sách user (phân trang) |
| 7 | PATCH | `/admin/users/{id}/status` | Bearer | ADMIN | Khoá/mở khoá user |
| 8 | POST | `/folders` | Bearer | User | Tạo folder |
| 9 | GET | `/folders/filter` | Bearer | User | Lọc folder của mình |
| 10 | GET | `/folders/root` | Bearer | User | Folder gốc của mình |
| 11 | GET | `/folders/tree` | Bearer | User | Cây folder theo cấp |
| 12 | GET | `/folders/{id}` | Bearer | VIEW | Chi tiết folder |
| 13 | GET | `/folders/{id}/contents` | Bearer | VIEW | Nội dung folder |
| 14 | GET | `/folders/{id}/path` | Bearer | VIEW | Đường dẫn breadcrumb |
| 15 | PATCH | `/folders/{id}` | Bearer | EDIT | Đổi tên / di chuyển folder |
| 16 | DELETE | `/folders/{id}` | Bearer | OWNER | Xoá cứng cả cây |
| 17 | POST | `/folders/bulk-delete` | Bearer | OWNER | Xoá nhiều folder |
| 18 | POST | `/folders/{id}/copy` | Bearer | VIEW + EDIT đích | Copy folder |
| 19 | POST | `/folders/bulk-move` | Bearer | EDIT | Di chuyển nhiều folder |
| 20 | POST | `/documents/{folderId}` | Bearer | EDIT | Upload file (multipart) |
| 21 | GET | `/documents/filter` | Bearer | User | Lọc document của mình |
| 22 | GET | `/documents/{id}` | Bearer | VIEW | Chi tiết document |
| 23 | GET | `/documents/{id}/download-url` | Bearer | VIEW | Presigned URL tải file |
| 24 | GET | `/documents/{id}/preview-url` | Bearer | VIEW | Presigned URL preview |
| 25 | PATCH | `/documents/{id}` | Bearer | EDIT | Đổi tên / di chuyển document |
| 26 | DELETE | `/documents/{id}` | Bearer | EDIT | Xoá document |
| 27 | POST | `/documents/bulk-delete` | Bearer | EDIT | Xoá nhiều document |
| 28 | POST | `/documents/{id}/copy` | Bearer | VIEW + EDIT đích | Copy document |
| 29 | POST | `/folders/{folderId}/permissions` | Bearer | OWNER | Chia sẻ folder |
| 30 | GET | `/folders/{folderId}/permissions` | Bearer | OWNER | Quyền trực tiếp |
| 31 | PATCH | `/permissions/{permissionId}` | Bearer | OWNER | Sửa quyền |
| 32 | DELETE | `/permissions/{permissionId}` | Bearer | OWNER | Thu hồi quyền |
| 33 | GET | `/folders/{folderId}/sharing-details` | Bearer | OWNER | Quyền hiệu lực của folder |
| 34 | GET | `/documents/{documentId}/sharing-details` | Bearer | OWNER | Quyền hiệu lực của document |
| 35 | POST | `/folders/{folderId}/public-link` | Bearer | OWNER | Tạo/lấy public link |
| 36 | DELETE | `/folders/{folderId}/public-link` | Bearer | OWNER | Thu hồi public link |
| 37 | GET | `/shared-with-me` | Bearer | User | Folder được chia sẻ cho mình |
| 38 | GET | `/public/folders/{token}` | Public | — | Nội dung folder qua public link |
| 39 | GET | `/public/folders/{token}/folders/{folderId}` | Public | — | Duyệt folder con qua public link |
| 40 | GET | `/public/folders/{token}/documents/{documentId}/download-url` | Public | — | Presigned URL qua public link |

## 8. Chi tiết endpoint

### 8.1 Auth

#### POST `/auth/register` — Đăng ký

- **Body**

| Field | Kiểu | Bắt buộc | Ràng buộc |
| :--- | :--- | :---: | :--- |
| `email` | string | ✓ | Định dạng email, độ dài 5–20 |
| `password` | string | ✓ | 5–20 ký tự |
| `displayName` | string | ✓ | Không rỗng |

```json
{
  "email": "user@example.com",
  "password": "secret123",
  "displayName": "Nguyễn Văn A"
}
```

- **200 OK** — `data`: `RegisterResponse`. User mới có `role = USER`, `status = ACTIVE`, `plan = FREE`, quota 100 MB.
- **Lỗi:** `400 ERR_REQ_001` (validate), `409 ERR_USER_002` (email tồn tại).

> Ràng buộc `email` tối đa 20 ký tự là giới hạn hiện tại của DTO (không phải chuẩn email).

#### POST `/auth/login` — Đăng nhập

- **Body**

| Field | Kiểu | Bắt buộc | Ràng buộc |
| :--- | :--- | :---: | :--- |
| `email` | string | ✓ | 5–20 ký tự |
| `password` | string | ✓ | 5–20 ký tự |

```json
{ "email": "user@example.com", "password": "secret123" }
```

- **200 OK** — `data`: `TokenResponse`
- **Lỗi:** `401 ERR_REQ_005` sai email/mật khẩu, `400 ERR_REQ_001` validate.

---

### 8.2 User

#### GET `/users/me`

- **200 OK** — `data`: `UserResponse`
- **Lỗi:** `401`, `404 ERR_USER_001`.

#### GET `/users/me/storage`

- **200 OK** — `data`: `StorageUsageResponse`
- Ghi chú: endpoint đồng bộ lại `users.storage_used` từ `SUM(documents.size)` (có ghi DB).

#### GET `/users/me/upgrade`

- **200 OK** — `data`: `UpgradeResponse`
- Chuyển user sang `PREMIUM` và nâng `storageLimit` lên 1 GB. Đây là endpoint demo, **dùng GET nhưng có side effect**; gọi lặp lại vẫn idempotent.

---

### 8.3 Admin

#### GET `/admin/users`

- **Quyền:** `ROLE_ADMIN`
- **Query**

| Param | Mặc định | Ghi chú |
| :--- | :--- | :--- |
| `page` | `0` | Spring Pageable |
| `size` | `10` | |
| `sort` | `createdAt,desc` | Ví dụ `sort=email,asc` |

- **200 OK** — `data`: `PageResponse<AdminUserResponse>`
- **Lỗi:** `401`, `403`.

#### PATCH `/admin/users/{id}/status`

- **Quyền:** `ROLE_ADMIN`
- **Body**

| Field | Kiểu | Bắt buộc |
| :--- | :--- | :---: |
| `status` | `ACTIVE` \| `LOCKED` | ✓ |

```json
{ "status": "LOCKED" }
```

- **200 OK** — `data`: `UpdateUserStatusResponse`
- **Lỗi:** `404 ERR_USER_001`, `400 ERR_REQ_001`/`ERR_REQ_002` (validate/enum).

---

### 8.4 Folder

#### POST `/folders` — Tạo folder

- **Body**

| Field | Kiểu | Bắt buộc | Ghi chú |
| :--- | :--- | :---: | :--- |
| `name` | string | ✓ | ≤ 255 ký tự, không chứa ký tự cấm của Windows/URL (xem `StorageNameUtils`) |
| `parentId` | UUID | | Bỏ trống = folder gốc |

```json
{ "name": "Tài liệu", "parentId": null }
```

- **201 Created** — `data`: `FolderResponse`, body `status = 201`.
- Hành vi: tên được tự động thêm hậu tố `(1)`, `(2)`… nếu trùng trong cùng cấp.
- **Lỗi:** `400 ERR_FOLDER_003` tên không hợp lệ, `400 ERR_REQ_001`, `404 ERR_FOLDER_001` (parent), `403 ERR_FOLDER_002` (không có EDIT trên parent).

#### GET `/folders/filter` — Lọc folder của mình

- **Query**

| Param | Mặc định | Ghi chú |
| :--- | :--- | :--- |
| `name` | | Tìm tương đối (`LIKE %name%`) |
| `parentId` | | Lọc folder con trực tiếp |
| `pageNo` | `0` | |
| `pageSize` | `10` | |
| `sortBy` | `updatedAt` | `id`, `name`, `createdAt`, `updatedAt` |
| `sortDir` | `desc` | `asc` hoặc `desc` |

- **200 OK** — `data`: Spring `Page<FolderResponse>` (chỉ folder do user sở hữu).
- `sortBy` ngoài danh sách → fallback `updatedAt`.

#### GET `/folders/root`

- **200 OK** — `data`: `FolderResponse[]` (folder gốc do user sở hữu).

#### GET `/folders/tree`

- **Query:** `parentId` (không bắt buộc). Bỏ trống → folder gốc của user; có giá trị → folder con trực tiếp.
- **200 OK** — `data`: `FolderTreeResponse`. `children` hiện luôn rỗng, dùng `hasChildren` để biết còn cấp con.

#### GET `/folders/{id}`

- **Quyền:** `VIEW`
- **200 OK** — `data`: `FolderResponse`
- **Lỗi:** `404 ERR_FOLDER_001`, `403 ERR_FOLDER_002`.

#### GET `/folders/{id}/contents` — Nội dung folder

- **Quyền:** `VIEW`
- **Query**

| Param | Mặc định | Ghi chú |
| :--- | :--- | :--- |
| `pageNo` | `0` | |
| `pageSize` | `10` | |
| `sortBy` | `updatedAt` | `id`, `name`, `createdAt`, `updatedAt` |
| `sortDir` | `desc` | |

- **200 OK** — `data`: `FolderContentsResponse`
- Hành vi: folder con luôn đứng trước document trong cùng một trang; sắp xếp theo `sortBy`
  áp dụng riêng cho từng nhóm; `hasChildren` của document luôn `true`.
- **Lỗi:** `404`, `403`.

#### GET `/folders/{id}/path`

- **Quyền:** `VIEW`
- **200 OK** — `data`: `FolderPathResponse` (root → folder hiện tại).

#### PATCH `/folders/{id}` — Đổi tên / di chuyển folder

- **Quyền:** `EDIT` trên folder hiện tại; nếu đổi `parentId` cần thêm `EDIT` trên folder đích.
- **Body**

| Field | Kiểu | Ghi chú |
| :--- | :--- | :--- |
| `name` | string | ≤ 255 ký tự |
| `parentId` | UUID | Di chuyển vào folder cha mới |
| `moveToRoot` | boolean | `true` để đưa về gốc |

```json
{ "name": "Tài liệu mới", "moveToRoot": true }
```

- Phải gửi ít nhất một trong `name` / `parentId` / `moveToRoot`; không gửi `parentId` cùng `moveToRoot = true`.
- Không cho di chuyển vào chính nó hoặc folder hậu duệ (400).
- **200 OK** — `data`: `FolderResponse`
- **Lỗi:** `400 ERR_REQ_001` (thiếu dữ liệu/cycle), `400 ERR_FOLDER_003` (tên), `403`, `404`.

#### DELETE `/folders/{id}` — Xoá folder

- **Quyền:** `OWNER` hiệu lực (owner folder hoặc tổ tiên).
- **200 OK** — `data`: `null`
- Hành vi: xoá cứng toàn bộ cây con (folder + document + permission) và xoá object S3 tương ứng sau khi commit.
- **Lỗi:** `403 ERR_FOLDER_002`, `404 ERR_FOLDER_001`.

#### POST `/folders/bulk-delete`

- **Body:** `{ "ids": ["uuid", "..."] }` — 1 đến 100 phần tử.
- Xoá tuần tự từng folder như `DELETE /folders/{id}`; một phần tử lỗi sẽ rollback toàn bộ.
- **200 OK** — `data`: `null`
- **Lỗi:** `400 ERR_REQ_001`, `403`, `404`.

#### POST `/folders/{id}/copy` — Copy folder

- **Body:** `{ "targetFolderId": "uuid" }` (bắt buộc).
- Điều kiện: `VIEW` trên folder nguồn, `EDIT` trên folder đích; kiểm tra quota của người copy.
- Copy đệ quy toàn bộ cây; bản sao thuộc sở hữu người copy, tên thêm hậu tố `_copy` + `(n)` nếu trùng.
- **200 OK** — `data`: `FolderResponse` (folder gốc của bản sao)
- **Lỗi:** `400 ERR_REQ_001`, `403`, `404`, `413 ERR_DOC_002` (vượt quota), `500 ERR_SYS_002` (storage).

#### POST `/folders/bulk-move`

- **Body**

| Field | Kiểu | Bắt buộc |
| :--- | :--- | :---: |
| `ids` | UUID[] | ✓ (1–100) |
| `targetFolderId` | UUID | ✓ |

- `EDIT` trên folder đích và từng folder được di chuyển; chống cycle; tự đổi tên nếu trùng.
- **200 OK** — `data`: `null`

---

### 8.5 Document

#### POST `/documents/{folderId}` — Upload file

- **Content-Type:** `multipart/form-data`
- **Quyền:** `EDIT` trên folder
- **Form part**

| Part | Kiểu | Bắt buộc | Ghi chú |
| :--- | :--- | :---: | :--- |
| `file` | binary | ✓ | Tối đa 10 MB (mặc định) |

```bash
curl -X POST "http://localhost:8081/api/documents/{folderId}" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@bao-cao.pdf;type=application/pdf"
```

- MIME hợp lệ: PDF, JPEG/PNG/GIF/WebP, Word, Excel, PowerPoint, ZIP, text/plain, CSV
  (`storage.allowed-mime-types`).
- Kiểm tra quota: `SUM(documents.size)` của người upload + file ≤ `storageLimit`.
- Object key dạng `{userId}/{folderId}/{uuid}_{tên đã sanitize}`.
- **201 Created** — `data`: `DocumentResponse`
- **Lỗi:** `400 ERR_REQ_001` MIME không hợp lệ, `403`, `404`, `413 ERR_DOC_002` (quá 10 MB hoặc vượt quota), `500 ERR_SYS_002`.

#### GET `/documents/filter` — Lọc document của mình

- **Query**

| Param | Mặc định | Ghi chú |
| :--- | :--- | :--- |
| `folderId` | | |
| `name` | | `LIKE %name%` |
| `mimeType` | | So khớp chính xác |
| `pageNo` | `0` | |
| `pageSize` | `10` | |
| `sortBy` | `updatedAt` | `id`, `name`, `size`, `mimeType`, `createdAt`, `updatedAt` |
| `sortDir` | `desc` | |

- **200 OK** — `data`: Spring `Page<DocumentResponse>` (chỉ document do user sở hữu).

#### GET `/documents/{id}`

- **Quyền:** `VIEW` trên folder chứa document.
- **200 OK** — `data`: `DocumentResponse`
- **Lỗi:** `404 ERR_DOC_001`, `403 ERR_FOLDER_002`.

#### GET `/documents/{id}/download-url`

- **200 OK** — `data`: `PresignedUrlResponse` (URL tải kèm `Content-Disposition: attachment`, hết hạn 300 s).
- **Lỗi:** `404`, `403`.

#### GET `/documents/{id}/preview-url`

- Chỉ hỗ trợ `application/pdf` và `image/*`; loại khác trả `400 ERR_REQ_001`.
- **200 OK** — `data`: `PresignedUrlResponse` (`Content-Disposition: inline`, 300 s).

#### PATCH `/documents/{id}` — Đổi tên / di chuyển

- **Quyền:** `EDIT` trên folder hiện tại; nếu đổi folder cần `EDIT` trên folder đích.
- **Body**

| Field | Kiểu | Ghi chú |
| :--- | :--- | :--- |
| `name` | string | ≤ 255 ký tự, không trùng tên trong folder đích |
| `folderId` | UUID | Folder đích mới |

- Phải gửi ít nhất một field; di chuyển vào folder hiện tại trả 400.
- **200 OK** — `data`: `DocumentResponse`
- **Lỗi:** `400 ERR_REQ_001`, `400 ERR_FOLDER_003`, `403`, `404`.

#### DELETE `/documents/{id}`

- **Quyền:** `EDIT` trên folder chứa document.
- **200 OK** — `data`: `null`
- Hành vi: xoá DB + xoá object S3 sau khi commit.

#### POST `/documents/bulk-delete`

- **Body:** `{ "ids": ["uuid", "..."] }` — 1 đến 100 phần tử.
- **200 OK** — `data`: `null`

#### POST `/documents/{id}/copy`

- **Body:** `{ "targetFolderId": "uuid" }` (không bắt buộc; mặc định copy tại chỗ).
- Điều kiện: `VIEW` trên document nguồn, `EDIT` trên folder đích; kiểm tra quota.
- Tên bản sao: `{tên}_copy` + `(n)` nếu trùng.
- **200 OK** — `data`: `DocumentResponse`
- **Lỗi:** `403`, `404 ERR_DOC_001`, `413 ERR_DOC_002` (vượt quota), `500 ERR_SYS_002`.

---

### 8.6 Chia sẻ & Public link

#### POST `/folders/{folderId}/permissions` — Chia sẻ folder

- **Quyền:** owner trực tiếp của folder.
- **Body**

| Field | Kiểu | Bắt buộc | Ghi chú |
| :--- | :--- | :---: | :--- |
| `sharedWithUserId` | UUID | ✓ (service kiểm tra) | Không được là owner |
| `permissionType` | `VIEW` \| `EDIT` | ✓ | |

```json
{ "sharedWithUserId": "0b6f1c2d-3e4a-4b5c-8d7e-9f0a1b2c3d4e", "permissionType": "VIEW" }
```

- **201 Created** — `data`: `SharePermissionResponse`
- **Lỗi:** `400 ERR_REQ_001` (thiếu `permissionType` hoặc gửi `NONE`/`OWNER`), `400 ERR_REQ_002` (enum không tồn tại), `400 ERR_PERM_002` (thiếu recipient hoặc tự chia sẻ cho owner), `403 ERR_PERM_003`, `404 ERR_FOLDER_001`/`ERR_USER_001`, `409 ERR_PERM_006`.

#### GET `/folders/{folderId}/permissions`

- **Quyền:** owner trực tiếp.
- **200 OK** — `data`: `DirectPermissionResponse[]` (chỉ quyền cấp trực tiếp trên folder này, mới nhất trước; không gồm quyền kế thừa/public link).
- **Lỗi:** `403 ERR_PERM_003`, `404 ERR_FOLDER_001`.

#### PATCH `/permissions/{permissionId}`

- **Quyền:** owner của folder chứa permission.
- **Body:** `{ "permissionType": "EDIT" }`
- Chỉ áp dụng cho permission của user; permission public link trả `400 ERR_PERM_002`.
- **200 OK** — `data`: `SharePermissionResponse`
- **Lỗi:** `404 ERR_PERM_001`, `400 ERR_REQ_001`/`ERR_REQ_002`, `403 ERR_PERM_003`.

#### DELETE `/permissions/{permissionId}`

- **Quyền:** owner của folder chứa permission.
- Chỉ thu hồi permission của user (không dùng cho public link).
- **200 OK** — `data`: `null`
- **Lỗi:** `404 ERR_PERM_001`, `400 ERR_PERM_002`, `403 ERR_PERM_003`.

#### GET `/folders/{folderId}/sharing-details`

- **Quyền:** owner trực tiếp.
- **200 OK** — `data`: `SharingDetailsResponse`; `sources[].inherited = false` cho quyền cấp trên chính folder, `true` cho quyền từ folder cha.
- **Lỗi:** `403 ERR_PERM_003`, `404 ERR_FOLDER_001`.

#### GET `/documents/{documentId}/sharing-details`

- **Quyền:** owner của document.
- **200 OK** — `data`: `SharingDetailsResponse` (quyền được tổng hợp từ folder chứa document và các folder cha; mọi source đều `inherited = true` do document không có quyền riêng).
- **Lỗi:** `404 ERR_DOC_001`, `403 ERR_DOC_003`.

#### POST `/folders/{folderId}/public-link`

- **Quyền:** owner trực tiếp.
- Tạo public link nếu chưa có; nếu đã có trả lại link cũ (idempotent). Token 43 ký tự URL-safe base64.
- **201 Created** — `data`: `PublicLinkResponse`
- **Lỗi:** `403 ERR_PERM_003`, `404 ERR_FOLDER_001`.

#### DELETE `/folders/{folderId}/public-link`

- **Quyền:** owner trực tiếp.
- **200 OK** — `data`: `null`
- **Lỗi:** `404 ERR_PERM_007` (chưa có/thu hồi rồi), `403 ERR_PERM_003`, `404 ERR_FOLDER_001`.

#### GET `/shared-with-me`

- **200 OK** — `data`: `SharedWithMeResponse[]`
- Chỉ trả quyền **trực tiếp** cấp cho user (không gồm folder con kế thừa, không gồm public link), mới nhất trước.

---

### 8.7 Public (không cần JWT)

Tất cả response public đều gắn header `Cache-Control: no-store`.

#### GET `/public/folders/{token}`

- **200 OK** — `data`: `FolderContentsResponse`
- Hành vi: trả **toàn bộ** folder con + document trong một trang (`pageNo = 0`, `pageSize = totalElements`, `last = true`); document kèm `downloadUrl`/`previewUrl` presigned 300 s, folder con kèm `itemUrl = folders/{id}`.
- **Lỗi:** `404 ERR_PERM_007` token sai/đã thu hồi.

#### GET `/public/folders/{token}/folders/{folderId}`

- Duyệt folder con trong cây của public link bằng chính token gốc.
- **200 OK** — `data`: `FolderContentsResponse`
- **Lỗi:** `404 ERR_PERM_007` (token sai hoặc `folderId` nằm ngoài cây).

#### GET `/public/folders/{token}/documents/{documentId}/download-url`

- **200 OK** — `data`: `PresignedUrlResponse` (300 s)
- **Lỗi:** `404 ERR_PERM_007` (token sai), `404 ERR_DOC_001` (document không thuộc cây của link).

---

## 9. Ví dụ luồng cơ bản

```bash
BASE=http://localhost:8081/api

# 1. Đăng ký
curl -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"secret123","displayName":"Nguyen Van A"}'

# 2. Đăng nhập, lấy token
TOKEN=$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"secret123"}' | jq -r '.data.accessToken')

# 3. Tạo folder gốc
curl -X POST "$BASE/folders" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"name":"Tai lieu"}'

# 4. Upload file vào folder
curl -X POST "$BASE/documents/{folderId}" -H "Authorization: Bearer $TOKEN" \
  -F "file=@bao-cao.pdf;type=application/pdf"

# 5. Lấy link tải
curl "$BASE/documents/{documentId}/download-url" -H "Authorization: Bearer $TOKEN"
```

## 10. Ghi chú & giới hạn hiện tại

- **Không có refresh token:** mặc dù có biến `REFRESH_EXP`, API chỉ cấp access token; hết hạn phải đăng nhập lại.
- **Không có API đổi mật khẩu / cập nhật hồ sơ / quên mật khẩu.**
- **Tìm kiếm chỉ trong tài nguyên sở hữu:** `filter`, `/folders/root` và `tree` (khi không truyền `parentId`) không trả folder/document được chia sẻ.
- **`/shared-with-me` chỉ trả quyền trực tiếp**, không gồm quyền kế thừa từ folder cha.
- **`GET /folders/tree?parentId=`** chưa kiểm tra quyền truy cập của folder cha — client chỉ nên dùng với folder của mình.
- **Tài khoản `LOCKED`:** request kèm JWT bị coi là chưa đăng nhập (401); `POST /auth/login` hiện trả `500` vì `DisabledException` chưa được map (nếu dùng tính năng khoá user cần bổ sung handler).
- **`GET /users/me/upgrade`** thay đổi dữ liệu (nâng PREMIUM) dù dùng method GET.
- **`GET /folders/{id}/contents`** phân trang trong bộ nhớ (gom folder + document rồi cắt trang), không phân trang ở DB.
- **Xoá là hard delete**, không có thùng rác/khôi phục.
- **Lỗi ở tầng Security filter** (thiếu/sai token) trả body lỗi mặc định của Spring Boot, không theo `AppResponse`.
- **403 do `@PreAuthorize`** (ví dụ gọi `/admin/**` bằng user thường) đi qua handler chung nên có thể trả code `ERR_FOLDER_002` (message nói về folder), không phải mã riêng cho admin.
- **Presigned URL** hết hạn sau 300 giây; client cần gọi lại endpoint lấy URL mới.
- **Upload** giới hạn 10 MB mặc định (`spring.servlet.multipart.max-file-size` và `storage.max-file-size` phải khớp).
