# Backend testing

## Các nhóm test

Mỗi test có một tag cấp độ chính:

- `unit`: class/hàm độc lập, không khởi động Spring.
- `integration`: API, security, service và persistence qua Spring + database.
- `e2e`: gọi backend qua cổng HTTP thật.

Các tag phụ hiện có: `api`, `security`, `persistence`, `filesystem`, `smoke`.

Chạy riêng từng cấp độ:

```powershell
.\gradlew.bat :server:unitTest
.\gradlew.bat :server:integrationTest
.\gradlew.bat :server:e2eTest
```

## Chạy mặc định bằng H2

Từ thư mục gốc dự án:

```powershell
.\gradlew.bat :server:test
```

Task `test` chạy `unit` và `integration`, loại trừ `e2e`. Integration test mặc định dùng
H2 in-memory, tạo schema mới cho mỗi Spring context và không truy cập MySQL local của backend.

## Chạy bằng MySQL thật

Instance local có schema riêng `online_testing_test`. Chạy:

```powershell
powershell -ExecutionPolicy Bypass -File .\server\mysql-local\run-mysql-tests.ps1
```

Script gọi task `mysqlTest`, chỉ chạy nhóm `integration` trên `online_testing_test`; database
ứng dụng `online_testing` không bị xóa hoặc thay đổi.

Trước mỗi lần chạy, script xóa và tạo lại riêng schema `online_testing_test` để không giữ bảng
hoặc foreign key cũ sau những lần đổi entity/package.

## Coverage

JaCoCo tự tạo HTML report sau task `test`:

```text
server/build/reports/jacoco/test/html/index.html
```

Task `check` giữ baseline tối thiểu 60% line coverage và 40% branch coverage:

```powershell
.\gradlew.bat :server:check
```

Baseline sau khi hợp nhất các module student, teacher và WebSocket từ `main` là 60,39% line
và 42,53% branch. Gate này ngăn coverage giảm thêm; khi bổ sung test cho các module mới, hãy
nâng dần hai ngưỡng trong `server/build.gradle`.

## Phạm vi hiện có

- Authentication, validation, BCrypt, JWT và security filter.
- CRUD đề thi, role/ownership, trạng thái và điều kiện publish.
- Câu hỏi, lựa chọn, không lộ đáp án đúng cho học sinh.
- Attempt trắc nghiệm, deadline, lưu/đổi đáp án, scoring và auto-submit.
- Upload/download/chấm tự luận và chống truy cập file trái phép.
- PDF validation, path confinement, replace/delete/cleanup file.
- Unique constraints và optimistic versioning trên database.
- Cùng một suite được xác minh trên H2 và MySQL 8.
- Một auth E2E flow chạy qua embedded HTTP server trên random port.
