# 🛠️ Hướng Dẫn Setup Local Development

Tài liệu hướng dẫn thiết lập và chạy dự án môi trường Local cho lập trình viên.

---

## 📋 1. Yêu Cầu Tiền Đề (Prerequisites)

Trước khi bắt đầu, đảm bảo máy bạn đã cài đặt sẵn các công cụ sau:

| Công cụ | Phiên bản / Yêu cầu |
| :--- | :--- |
| **Java** | OpenJDK 21 trở lên |
| **Build Tool** | Maven 3.x (hoặc dùng Maven Wrapper `mvnw` sẵn trong repo) |
| **Container Engine** | Docker & Docker Compose |
| **IDE** | IntelliJ IDEA (Khuyến nghị) hoặc VS Code |

---

## ⚙️ 2. Hướng Dẫn Cấu Hình `.env`

1. Tại thư mục **root** của dự án, tạo một file mới tên là `.env`.
2. Sao chép toàn bộ nội dung cấu hình dưới đây vào file `.env`:

```env
# =========================
# Database
# =========================

DB_NAME=app_db
DB_USERNAME=postgre
DB_PASSWORD=secret
DB_PORT=5432
DB_HOST=localhost


# =========================
# Application
# =========================

APP_PORT=8081
ALLOWED_ORIGINS=http://localhost:5500


# =========================
# Security
# =========================

SECRET_KEY=7xK92mQaP4vLzN8sT1yR5uW0bH3cDfGk
TOKEN_EXP=604800000
REFRESH_EXP=604800000


# =========================
# Object Storage
# =========================

STORAGE_BUCKET=mini-drive
STORAGE_REGION=us-east-1
STORAGE_ACCESS_KEY=minioadmin
STORAGE_SECRET_KEY=minioadmin123

MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin123

MINIO_API_PORT=9000
MINIO_CONSOLE_PORT=9001

STORAGE_ENDPOINT=http://localhost:9000
```

> ⚠️ **LƯU Ý QUAN TRỌNG:**
> - Có thể thay đổi `SECRET_KEY` hoặc mật khẩu tùy ý dưới local.
> - **Tuyệt đối KHÔNG commit file `.env`** lên Git repository.

---

## 🐳 3. Khởi Chạy Infrastructure (Docker Services)

Dự án có 3 dịch vụ trong `docker-compose.yml`: `postgres`, `adminer`, và `backend`.

### Thao tác thường ngày:
Trong quá trình phát triển (Local Dev), chỉ cần chạy 2 dịch vụ **PostgreSQL** và **Adminer**:

```bash
docker compose up -d postgres adminer
```

*(Chỉ khi nào bạn cần build và kiểm thử toàn bộ ứng dụng bằng Docker Container thì mới bật dịch vụ `backend`).*

### Đăng nhập Adminer (Quản lý Database):
- **URL truy cập:** [http://localhost:8083](http://localhost:8083)
- **Thông số đăng nhập:**
  - **System:** `PostgreSQL`
  - **Server:** `postgres` *(tên container database)*
  - **Username:** `postgre`
  - **Password:** `secret`
  - **Database:** `app_db`
- *(Tùy chọn)* Tích vào **Permanent Login** để lưu phiên đăng nhập cho lần sau.

---

## 🚀 4. Hướng Dẫn Chạy Application (Spring Boot)

### Cách 1: Sử Dụng IntelliJ IDEA (Khuyến nghị)

1. Mở dự án trong **IntelliJ IDEA**.
2. Trên thanh công cụ, chọn **Edit Configurations...**
3. Bấm dấu **`+`** ➔ Chọn **Spring Boot**.
4. Chọn **Modify options** ➔ Tích chọn **Enable EnvFile** (hoặc thêm cấu hình nạp file `.env` ở thư mục root).
5. Nhấn **Run** hoặc **Debug**.
- Application sẽ lắng nghe tại: [http://localhost:8081/api](http://localhost:8081/api)

---

### Cách 2: Sử Dụng Terminal / VS Code (Windows CMD)

Nếu bạn không dùng IntelliJ, thực hiện theo các bước sau trên cửa sổ **CMD**:

1. **Build file JAR:**
   Di chuyển vào thư mục ứng dụng `backend` và thực hiện build bỏ qua test:
   ```cmd
   cd backend
   mvnw.cmd clean package -DskipTests
   ```

2. **Nạp biến môi trường từ `.env`:**
   Nạp toàn bộ biến từ file `.env` ở thư mục cha (`root`) vào Terminal session hiện tại:
   ```cmd
   for /f "tokens=*" %i in (..\.env) do set %i
   ```

3. **Khởi chạy ứng dụng:**
   Chạy file JAR vừa được đóng gói trong thư mục `target` kèm profile active:
   ```cmd
   java -jar target backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
   ```
