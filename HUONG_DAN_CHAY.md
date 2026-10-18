# Hướng dẫn chạy MultiGame trên máy khác

Hướng dẫn dành cho Windows và PowerShell. Chạy các lệnh trong thư mục dự án, trừ phần SQL được chạy trong MySQL Workbench hoặc MySQL CLI.

## 1. Chuẩn bị

- Cài Git, **JDK 21** và MySQL Server (có thể dùng MySQL 8.0).
- Bật dịch vụ MySQL.
- Có Internet trong lần build đầu để tải Maven và dependencies.
- Không cần cài Maven riêng: dự án có `mvnw.cmd`.

Kiểm tra Java:

```powershell
java -version
```

Kết quả phải là Java 21. Nếu khác, chỉnh `JAVA_HOME` về JDK 21 và mở lại PowerShell.

## 2. Clone dự án

```powershell
git clone https://github.com/minhtri14012005/BTL_LTM.git
cd BTL_LTM
```

Người gửi cần push thành công phiên bản mới trước khi người nhận clone hoặc `git pull`.

## 3. Tạo database và tài khoản MySQL

Mở MySQL Workbench, kết nối bằng tài khoản có quyền tạo database và chạy SQL dưới đây. Thay `THAY_BANG_MAT_KHAU_RIENG` bằng mật khẩu của bạn:

```sql
CREATE DATABASE IF NOT EXISTS quizz
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'quiz_app'@'localhost'
  IDENTIFIED BY 'THAY_BANG_MAT_KHAU_RIENG';

GRANT ALL PRIVILEGES ON quizz.* TO 'quiz_app'@'localhost';
```

Nếu tài khoản `quiz_app` đã tồn tại, `CREATE USER IF NOT EXISTS` không đổi mật khẩu của tài khoản đó. Hãy dùng đúng mật khẩu hiện có hoặc đổi lại trong MySQL.

Chỉ cần tạo database trống; Flyway sẽ tự tạo và cập nhật bảng khi ứng dụng khởi động.

## 4. Cấu hình kết nối

Trong PowerShell ở thư mục dự án:

```powershell
Copy-Item config/application-local.properties.example config/application-local.properties
```

Chỉ copy khi chưa có file cấu hình riêng để tránh ghi đè cấu hình đang dùng. Mở `config/application-local.properties`, chỉnh:

```properties
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=quizz
DB_USER=quiz_app
DB_PASSWORD=THAY_BANG_MAT_KHAU_RIENG
```

Mật khẩu phải khớp với tài khoản MySQL ở bước 3. File này bị Git bỏ qua vì chứa thông tin riêng của từng máy. Nếu mật khẩu có ký tự đặc biệt, cần tuân theo cú pháp file Java `.properties` (ví dụ dấu gạch chéo ngược phải viết thành `\\`).

## 5. Chạy ứng dụng

```powershell
.\mvnw.cmd spring-boot:run
```

Giữ cửa sổ PowerShell mở. Khi ứng dụng khởi động thành công, truy cập:

**http://localhost:8080**

Nhấn `Ctrl+C` trong PowerShell để dừng server. Giao diện web được phục vụ cùng ứng dụng; không cần chạy npm.

Database mới không chứa tài khoản và câu hỏi từ máy người gửi. Có thể đăng ký tài khoản qua giao diện và tự tạo nội dung, hoặc chuyển dữ liệu theo bước 8.

## 6. Build và chạy bằng JAR (tùy chọn)

Dừng server đang chạy trước khi khởi động một server khác trên cùng cổng.

```powershell
.\mvnw.cmd package
java -jar target/multigame-0.0.1-SNAPSHOT.jar
```

Chạy JAR từ thư mục gốc dự án để ứng dụng tìm đúng `config/` và `data/`.

Thư mục `target/` chứa sản phẩm build và log, sẽ được tạo lại trên từng máy. Không cần đưa `target/` lên Git. Thư mục `.cache/maven/` chứa dependencies tải về và cũng không cần commit.

## 7. Tạo tài khoản quản trị (tùy chọn)

Sau khi đã cấu hình database và build JAR ở bước 6:

```powershell
.\scripts\provision-admin.ps1 -Database quizz
```

Script sẽ hỏi tên đăng nhập, tên hiển thị và mật khẩu quản trị. Dùng mật khẩu ít nhất 16 ký tự, tối đa 72 byte UTF-8, gồm ít nhất 3 nhóm ký tự theo yêu cầu script. Sau đó khởi động ứng dụng và truy cập `http://localhost:8080/admin`.

## 8. Chuyển dữ liệu hiện tại sang máy bạn

Clone chỉ lấy những file đã được commit và push; không sao chép dữ liệu trong MySQL.

Nếu muốn có cùng tài khoản, bộ câu hỏi và lịch sử với máy người gửi:

1. Dừng ứng dụng trên máy người gửi để database và file media được sao chép nhất quán.
2. Dùng MySQL Workbench → **Server → Data Export**, chọn database `quizz`, xuất cả cấu trúc và dữ liệu ra file SQL, bao gồm bảng `flyway_schema_history`.
3. Gửi file SQL cùng bản sao đầy đủ các thư mục `data/quiz-images/` và `data/quiz-videos/` đang sử dụng. Nếu cấu hình đường dẫn media riêng, sao chép các thư mục thực tế đó.
4. Trên máy nhận, import SQL vào database `quizz` trống bằng **Server → Data Import** trước khi chạy ứng dụng lần đầu. Không import chồng lên database đang có dữ liệu cần giữ.
5. Đặt ảnh/video vào đúng đường dẫn cấu hình trên máy nhận, rồi chạy ứng dụng với cùng phiên bản mã nguồn.

Các tài khoản và mật khẩu đăng nhập ứng dụng được giữ theo database đã import. Tài khoản MySQL vẫn cấu hình riêng cho từng máy. Chỉ gửi bản sao database cho người được phép nhận dữ liệu tài khoản và lịch sử bên trong.

## 9. Chơi qua mạng LAN (tùy chọn)

Nếu các bạn cùng chơi trên một server, chỉ máy làm server cần chạy Java và MySQL. Các máy còn lại dùng trình duyệt.

Trên máy server, thêm vào `config/application-local.properties` (thay IP ví dụ bằng IPv4 LAN thực tế của máy server):

```properties
SERVER_ADDRESS=0.0.0.0
ALLOWED_ORIGINS=http://192.168.1.10:8080,http://localhost:8080,http://127.0.0.1:8080
```

Dùng `ipconfig` để xem IPv4, khởi động lại ứng dụng và cho phép cổng TCP 8080 qua Windows Firewall trên mạng riêng. Máy khác cùng mạng mở `http://192.168.1.10:8080` với IP đã thay. Không cần mở cổng MySQL cho các máy chỉ dùng trình duyệt.

## 10. Lỗi thường gặp

| Hiện tượng | Cách xử lý |
| --- | --- |
| Không tìm thấy `java`, hoặc báo sai phiên bản Java | Cài JDK 21, kiểm tra `JAVA_HOME` và `PATH`, mở lại PowerShell. |
| Maven không tải được dependencies | Kiểm tra Internet/proxy và chạy lại lệnh. |
| `Access denied for user` | Kiểm tra `DB_USER`, `DB_PASSWORD` và quyền truy cập database. |
| `Unknown database 'quizz'` | Tạo database ở bước 3 hoặc sửa `DB_NAME` đúng với database đã tạo. |
| `Communications link failure` hoặc connection refused | Bật MySQL, kiểm tra `DB_HOST` và `DB_PORT`. |
| `Port 8080 was already in use` | Dừng server cũ hoặc thêm `SERVER_PORT=8081`; mở web bằng cổng mới và cập nhật `ALLOWED_ORIGINS` tương ứng. |
| Flyway báo checksum không khớp | Kiểm tra mã nguồn có cùng phiên bản với database; không tự xóa lịch sử migration hoặc bảng dữ liệu. |
| Thiếu ảnh/video sau khi import database | Chép đầy đủ media ở bước 8 và kiểm tra đường dẫn lưu trữ. |
| Clone về không thấy dữ liệu mới nhất | Kiểm tra người gửi đã push thành công, sau đó chạy `git pull` khi không có thay đổi cục bộ xung đột. |

Hướng dẫn được đối chiếu với cấu hình trong repository; chưa xác nhận bằng một lần cài đặt trên máy Windows sạch.
