# spring-minio-drive

Backend REST API cho dịch vụ lưu trữ "mini drive": quản lý folder/document, upload (multipart + presigned URL), chia sẻ và phân quyền, public link, thống kê dung lượng.

- **Stack:** Java 21 · Spring Boot 4.0.8 · Spring Security + JWT · Spring Data JPA · PostgreSQL 16 · RustFS (S3-compatible) · Flyway · Docker Compose
- **Code backend:** `backend/` — mọi API nằm dưới context path `/api`
- **Tài liệu tham khảo:** `docs/`

## 1. Yêu cầu

| Công cụ | Ghi chú |
| :--- | :--- |
| JDK 21+ | Bắt buộc (pom cấu hình `java.version=21`) |
| Docker Desktop | Chạy PostgreSQL, Adminer, RustFS và Testcontainers |
| Git | Clone repo |

Không cần cài Maven — dùng Maven Wrapper có sẵn (`mvnw` / `mvnw.cmd`).

## 2. Cấu hình `.env`

```powershell
Copy-Item .env.example .env
```

Mở `.env` và điền các biến bắt buộc:

| Biến | Ý nghĩa |
| :--- | :--- |
| `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | Database PostgreSQL được tạo khi khởi động container |
| `SECRET_KEY` | Khóa ký JWT, dùng chuỗi đủ dài (≥ 32 ký tự) |
| `STORAGE_ACCESS_KEY` / `STORAGE_SECRET_KEY` | Phải **trùng** với `RUSTFS_ACCESS_KEY` / `RUSTFS_SECRET_KEY` |
| `STORAGE_BUCKET` | Tên bucket, phải khớp bucket tạo ở bước 4 |
| `STORAGE_ENDPOINT` | `http://localhost:9000` khi chạy backend ngoài Docker |

> `.env` đã nằm trong `.gitignore` — không commit lên Git.

## 3. Khởi động hạ tầng

```powershell
docker compose up -d postgres adminer rustfs
```

| Dịch vụ | URL |
| :--- | :--- |
| PostgreSQL | `localhost:${DB_PORT}` (mặc định `5432`) |
| Adminer (quản lý DB) | <http://localhost:8083> |
| RustFS Console | <http://localhost:9001> |

Đăng nhập Adminer: **System** `PostgreSQL`, **Server** `postgres`, còn user/password/database lấy từ `.env`.

Lần chạy đầu, Flyway tự tạo schema (không cần import SQL thủ công).

## 4. Tạo bucket trên RustFS

Storage S3-compatible **không tự tạo bucket**. Mở RustFS Console (<http://localhost:9001>), đăng nhập bằng `RUSTFS_ACCESS_KEY` / `RUSTFS_SECRET_KEY`, tạo bucket trùng với `STORAGE_BUCKET` trong `.env`.

Nếu bỏ qua bước này, các API upload/download sẽ trả lỗi `NoSuchBucket`.

## 5. Chạy backend (profile `dev`)

**PowerShell:**

```powershell
cd backend
Get-Content ..\.env | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object {
  $key, $value = $_ -split '=', 2
  Set-Item "Env:$($key.Trim())" $value.Trim()
}
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

**IntelliJ IDEA:** chạy `BackendApplication` với profile `dev` và bật EnvFile trỏ tới `.env` ở thư mục root.

Kiểm tra sau khi chạy:

| Mục | URL |
| :--- | :--- |
| API base | <http://localhost:8081/api> |
| Swagger UI (chỉ có ở `dev`) | <http://localhost:8081/api/swagger-ui.html> |
| Health | <http://localhost:8081/api/actuator/health> |

Đăng ký / đăng nhập: `POST /api/auth/register`, `POST /api/auth/login`.

## 6. Chạy test

```powershell
cd backend
.\mvnw.cmd clean verify -Dspring.profiles.active=test
```

Test dùng Testcontainers (PostgreSQL 17 + RustFS), nên **Docker phải đang chạy**; bucket test được tạo tự động trong test.

## 7. Chạy toàn bộ stack bằng Docker

Compose tự override `DB_HOST=postgres` và `STORAGE_ENDPOINT=http://rustfs:9000` cho container backend
(không cần sửa `.env`). Chỉ cần:

```powershell
docker compose up -d --build
```

Backend build từ `backend/Dockerfile`, chạy profile `prod` tại <http://localhost:8081/api> (Swagger bị tắt ở `prod`). Bucket vẫn phải tạo thủ công như bước 4.

Xem log backend:

```powershell
# Log console (stdout của container)
docker compose logs -f backend

# Log JSON mount ra host (profile prod ghi cả console lẫn file)
Get-Content logs\docker-backend\error-json.log -Tail 50 -Wait
```

Thư mục `logs/docker-backend/` trên host được bind vào `/app/logs` trong container
(`app-json.log` cho toàn bộ log, `error-json.log` chỉ log ERROR).

## 8. Cấu trúc dự án

| Đường dẫn | Nội dung |
| :--- | :--- |
| `backend/src/main/java/com/spring/backend/` | `controller`, `service`, `repository`, `entity`, `dto`, `config`, `security` |
| `backend/src/main/resources/db/migration/` | Migration Flyway (`V1`, `V3`, `V4`, ...) |
| `backend/src/main/resources/application*.yaml` | Cấu hình chung + profile `dev`, `prod`, `test` |
| `backend/src/test/` | Unit test và integration test (Testcontainers) |
| `docker-compose.yml` | postgres, adminer, rustfs, backend |
| `.env.example` | Mẫu biến môi trường |
| `docs/` | Ghi chú lý thuyết Spring, storage, Docker, CI/CD |
| `docs/api/api-specs.md` | Đặc tả API (endpoint, request/response, mã lỗi) |

## 9. Lỗi thường gặp

- **Startup fail vì thiếu biến môi trường:** `application-dev.yaml` không có default cho `STORAGE_BUCKET`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY` — nhớ nạp `.env` trước khi chạy.
- **Không kết nối được PostgreSQL:** kiểm tra container `postgres` đang chạy và `DB_NAME`/`DB_USERNAME`/`DB_PASSWORD` khớp.
- **`NoSuchBucket`:** chưa tạo bucket ở bước 4 hoặc `STORAGE_BUCKET` sai.
- **Lỗi chữ ký / 403:** `STORAGE_*` và `RUSTFS_*` không trùng nhau.
- **Cổng bị chiếm:** đổi `APP_PORT`, `DB_PORT`, `RUSTFS_API_PORT`, `RUSTFS_CONSOLE_PORT` trong `.env`.
- **Backend container exit code 1 / `docker compose logs` trống:** xem `logs\docker-backend\error-json.log`; nếu container cũ chưa mount thư mục host thì lấy log bằng `docker cp backend:/app/logs ./logs/backend-container`.
- **`Connection to localhost:5432 refused`:** backend trong container đang dùng `DB_HOST=localhost`; compose đã override thành `postgres`, nếu tự chạy container/IDE thì kiểm tra lại biến này.
- **Schema lệch sau khi sửa migration:** `docker compose down -v` để xóa volume rồi chạy lại (mất dữ liệu).
