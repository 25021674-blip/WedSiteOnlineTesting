# MySQL local cho backend

Instance này độc lập với `MySQL80` có sẵn trên máy:

- Windows service: `OnlineTestingMySQL`
- Địa chỉ: `127.0.0.1:3307`
- Database: `online_testing`
- Application user: `online_testing_app`
- Data directory: `C:\ProgramData\MySQL\OnlineTestingMySQL\Data`
- Cấu hình runtime: `C:\ProgramData\MySQL\OnlineTestingMySQL\my.ini`

Windows service được đặt ở chế độ tự khởi động. Spring Boot đọc thông tin kết nối từ
`server/src/main/resources/application-local.properties`; file này chứa thông tin local và
được `.gitignore` loại trừ.

Chạy backend từ thư mục gốc:

```powershell
.\gradlew.bat :server:bootRun --args="--spring.profiles.active=local"
```

Kiểm tra service:

```powershell
Get-Service OnlineTestingMySQL
```

## Chạy test bằng MySQL thật

Test mặc định dùng H2 để chạy nhanh và cô lập. Khi cần kiểm tra tương thích MySQL, dùng schema
riêng `online_testing_test` bằng lệnh:

```powershell
powershell -ExecutionPolicy Bypass -File .\server\mysql-local\run-mysql-tests.ps1
```

Script đọc credential từ `application-local.properties`, chỉ dùng schema test và không truy cập
database `online_testing` của backend.
