# ClassQuest

**ClassQuest** là hệ thống học tập tương tác qua trò chơi, dành cho giảng viên và sinh viên trong cùng lớp. Khẩu hiệu: **Hành trình thử thách trong lớp học**. Tên kỹ thuật hiện tại trong source và file JAR vẫn là **MultiGame**.

Ứng dụng chạy trên một máy chủ cá nhân với MySQL. Người học mở trình duyệt trên máy tính hoặc điện thoại cùng mạng Wi-Fi/LAN, không cần cài ứng dụng riêng.

## Chức năng

- **Quản trị viên:** quản lý năm học, học kỳ, môn học, lớp, số buổi bắt buộc; cấp tài khoản và phân công giảng viên; quản lý trạng thái sinh viên.
- **Giảng viên:** duyệt yêu cầu vào lớp, tạo buổi học, quản lý ngân hàng câu hỏi riêng/Public, chọn và chỉnh bản sao nội dung trong trò, điều khiển trò chơi, chấm bài và cấu hình Rank của lớp.
- **Sinh viên:** đăng ký tài khoản, nhập mã lớp, tham gia hoạt động, xem điểm, bảng xếp hạng, lịch sử và danh hiệu.
- Mỗi buổi có ba trò: **Vòng quay may mắn** trước, sau đó là hai loại khác nhau trong **Quiz**, **Điền từ vào chỗ trống**, **Bài tập**.
- Bài tập được nhập và nộp lại trực tiếp trên web khi còn hạn. Server quyết định thời hạn, kết quả, điểm và đồng bộ realtime qua WebSocket.

## Công nghệ

Backend: Java 21, Spring Boot, Spring Security, JPA/Hibernate, Flyway và MySQL. Frontend: React, TypeScript, Vite, Tailwind CSS và các component dựa trên Radix UI.

Sau khi build, frontend được đóng gói trong JAR Spring Boot: cùng một địa chỉ phục vụ giao diện, `/api` và `/ws`.

## Cài đặt trên Windows

Các lệnh dưới đây chạy trong **PowerShell**, tại thư mục gốc project nếu không ghi khác.

### 1. Chuẩn bị

Cài Git, **JDK 21**, **Node.js 22 từ phiên bản 22.12 trở lên** kèm npm, và **MySQL Server 8.x**. Bật dịch vụ MySQL. Không dùng MariaDB thay MySQL cho các migration hiện tại.

Kiểm tra các chương trình đã có trong PATH:

```powershell
git --version
java -version
node --version
npm.cmd --version
```

Không cần cài Maven riêng: project có Maven Wrapper. Lần build đầu cần Internet để tải Maven và thư viện Java/npm.

### 2. Clone repository

```powershell
git clone https://github.com/minhtri14012005/BTL_LTM.git
cd BTL_LTM
```

Clone branch chứa phiên bản source mới nhất. Những thay đổi chỉ có trên máy người phát triển, chưa commit/push, sẽ chưa xuất hiện khi người khác clone.

### 3. Tạo database riêng cho ứng dụng

Mở MySQL Workbench hoặc MySQL CLI bằng tài khoản quản trị MySQL, rồi chạy SQL sau. Thay mật khẩu minh họa bằng mật khẩu riêng của bạn:

```sql
CREATE DATABASE lopvui CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'lopvui_app'@'localhost' IDENTIFIED BY 'THAY_BANG_MAT_KHAU_DB_RIENG';
GRANT ALL PRIVILEGES ON lopvui.* TO 'lopvui_app'@'localhost';
```

Nếu tên database hoặc tài khoản đã tồn tại, hãy chọn tên mới hoặc dùng tài khoản phù hợp đang có. Không xóa database có dữ liệu để làm lại cài đặt. Flyway sẽ tự tạo/cập nhật các bảng khi khởi động; không cần nhập file dump hay tạo bảng thủ công.

### 4. Cấu hình kết nối MySQL

Trong PowerShell tại thư mục project:

```powershell
$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '3306'
$env:DB_NAME = 'lopvui'
$env:DB_USER = 'lopvui_app'
$dbSecret = Read-Host 'Nhap mat khau MySQL da dat o buoc 3' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $dbSecret).Password
Remove-Variable dbSecret
```

Giữ cửa sổ PowerShell này cho các bước tiếp theo. Biến môi trường chỉ áp dụng trong cửa sổ hiện tại; mở cửa sổ mới thì cần đặt lại. Cách này tránh ghi mật khẩu vào source. Không commit thông tin đăng nhập của máy bạn vào Git.

Ứng dụng cũng đọc `config/application-local.properties` nếu có. Các biến môi trường ở trên được ưu tiên hơn giá trị cùng tên trong file đó; hãy đặt đủ cả tên database và tài khoản để tránh dùng nhầm cấu hình có sẵn trong repository.

### 5. Build

```powershell
.\build.cmd
```

Script cài dependency frontend khi cần, build React, chạy kiểm thử Java và đóng gói:

```text
target/multigame-0.0.1-SNAPSHOT.jar
```

Build thông thường không chạy bộ tích hợp MySQL. Không cần chạy `npm install` riêng để build bằng script này.

### 6. Cấp tài khoản ADMIN đầu tiên

Database mới chưa có tài khoản quản trị. Chạy một lần trong cửa sổ đã cấu hình DB:

```powershell
$env:MULTIGAME_ADMIN_USERNAME = 'admin'
$env:MULTIGAME_ADMIN_DISPLAY_NAME = 'Quan tri vien'
$adminSecret = Read-Host 'Dat mat khau ADMIN' -AsSecureString
$env:MULTIGAME_ADMIN_PASSWORD = [System.Net.NetworkCredential]::new('', $adminSecret).Password
java -jar target/multigame-0.0.1-SNAPSHOT.jar --provision-admin
Remove-Item Env:MULTIGAME_ADMIN_PASSWORD
Remove-Variable adminSecret
```

Mật khẩu ADMIN cần ít nhất **16 ký tự**, tối đa **72 byte UTF-8**, có ít nhất ba nhóm trong chữ thường, chữ hoa, số và ký tự đặc biệt. Không chứa tên đăng nhập hoặc các từ `password`, `changeme`, `multigame`.

Lệnh cấp ADMIN tự chạy migration cần thiết và kết thúc sau khi cấp tài khoản. Nếu username đã thuộc một ADMIN hợp lệ, lệnh giữ nguyên mật khẩu cũ; đây không phải lệnh đặt lại mật khẩu hay nâng quyền tài khoản sinh viên.

### 7. Chạy ứng dụng

```powershell
.\run.cmd
```

Mở **http://localhost:8080**. Đăng nhập bằng tài khoản ADMIN vừa cấp. Dừng ứng dụng bằng `Ctrl+C` trong cửa sổ đang chạy.

Script mặc định dùng cổng `8080`, lắng nghe các giao diện mạng và cho phép những origin cụ thể của máy. Muốn đổi cổng, đặt trước khi chạy:

```powershell
$env:SERVER_PORT = '8081'
.\run.cmd
```

## Sử dụng lần đầu

1. ADMIN tạo năm học → học kỳ → môn học → mở môn theo học kỳ → tạo lớp và quy định số buổi.
2. ADMIN cấp tài khoản giảng viên và phân công vào lớp.
3. Giảng viên vào lớp, lấy mã tham gia; tạo các ô thử thách vòng quay và ngân hàng câu hỏi/đề bài phù hợp.
4. Sinh viên chọn **Đăng ký sinh viên** tại trang đăng nhập, nhập họ tên và mã SV duy nhất, rồi đăng nhập và gửi mã lớp.
5. Giảng viên duyệt sinh viên, tạo buổi, chọn nội dung đúng loại trò, công bố và bắt đầu buổi.
6. Sinh viên tham dự buổi và từng trò; xem điểm, lịch sử và bảng xếp hạng trong giao diện của mình.

Không có mật khẩu ADMIN mặc định được chia sẻ trong README. Giảng viên dùng tài khoản ADMIN cấp; sinh viên tự đăng ký trên web.

## Truy cập từ điện thoại hoặc máy khác qua LAN

- Chỉ máy chủ cần chạy Java và MySQL. Các thiết bị còn lại dùng trình duyệt và kết nối cùng mạng LAN/Wi-Fi.
- Khi chạy `run.cmd`, xem dòng `URLs allowed`, chọn địa chỉ IPv4 của máy chủ, ví dụ `http://192.168.1.10:8080`. Địa chỉ này chỉ là ví dụ; dùng đúng IP được in trên máy bạn.
- Trên thiết bị khác, mở địa chỉ IP đó. **Không dùng `localhost`** trên điện thoại để truy cập máy chủ.
- Cho phép Java hoặc cổng ứng dụng đi qua Windows Firewall trên mạng **Private** nếu cần. Không cần mở cổng MySQL cho sinh viên; các thiết bị chỉ gọi Spring Boot.
- Nếu tự đặt origin, khai báo đúng giao thức, IP và cổng trước khi chạy:

```powershell
$env:ALLOWED_ORIGINS = 'http://localhost:8080,http://192.168.1.10:8080'
.\run.cmd
```

Nếu thay IP, cổng hoặc Wi-Fi, khởi động lại server để cập nhật danh sách origin. Không cấu hình origin `*`.

Kiểm thử tự động đã dùng nhiều phiên sinh viên độc lập và truy cập IP của chính máy chạy server. Kết nối từ nhiều thiết bị vật lý còn phụ thuộc firewall/router, cần kiểm tra trên mạng thực tế của bạn.

## Cập nhật source

Dừng ứng dụng, cập nhật source rồi build và chạy lại:

```powershell
git pull
.\build.cmd
.\run.cmd
```

Giữ nguyên database và cấu hình DB của bạn. Nếu Git báo có thay đổi cục bộ, xử lý/lưu các thay đổi đó trước khi pull; không dùng reset để bỏ dữ liệu cấu hình chưa lưu. Sao lưu database trước khi nâng phiên bản.

Giao diện trong JAR chỉ đổi khi **build lại và khởi động lại JAR mới**. Nếu vẫn thấy giao diện cũ, nhấn `Ctrl+F5` sau khi server đã khởi động lại.

## Phát triển và kiểm thử

Chạy frontend ở chế độ phát triển, trong cửa sổ PowerShell khác:

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

Backend vẫn cần chạy riêng. Vite mặc định proxy `/api` và `/ws` tới `http://localhost:8080`. Nếu dùng cổng khác, đặt `$env:BACKEND_URL = 'http://localhost:8081'` trước khi chạy Vite. Origin Vite, thường là `http://localhost:5173`, phải có trong `ALLOWED_ORIGINS` của backend; đặt biến đó trước khi khởi động backend.

Kiểm thử frontend và Java không cần database:

```powershell
npm.cmd --prefix frontend test
.\mvnw.cmd test
```

Bộ tích hợp dùng profile `baseline`:

```powershell
.\mvnw.cmd -Pbaseline verify
```

Chỉ chạy bộ này khi đã cấu hình đúng máy MySQL kiểm thử và tài khoản có quyền tạo/xóa schema `multigame_test_*`. Tài khoản ứng dụng được cấp quyền riêng trên `lopvui.*` ở trên **không đủ quyền** cho bộ tích hợp. Nên dùng tài khoản kiểm thử riêng trên MySQL dành cho phát triển; không mở rộng quyền tài khoản chạy ứng dụng chỉ để chạy test.

Test tự tạo schema tên ngẫu nhiên, kiểm tra quyền sở hữu rồi xóa schema đó sau khi đóng ứng dụng. Không dùng database chính làm database test, không chạy `flyway clean`, và không sửa các migration đã áp dụng.

## Xử lý lỗi thường gặp

| Hiện tượng | Cách kiểm tra |
| --- | --- |
| Không tìm thấy `java`, `node`, `git` | Kiểm tra cài đặt/PATH, JDK 21 và phiên bản Node; mở lại terminal sau khi cài. |
| Lỗi kết nối MySQL / Access denied | Kiểm tra dịch vụ MySQL, `DB_HOST`, `DB_PORT`, `DB_NAME`, username, mật khẩu và quyền trên database. |
| Cổng 8080 đã được dùng | Dừng tiến trình đang dùng cổng hoặc đặt `SERVER_PORT` khác rồi chạy lại. |
| Trang mở được nhưng WebSocket không kết nối | Kiểm tra origin thực tế của trang có trong `ALLOWED_ORIGINS`, gồm cả cổng; khởi động lại backend sau khi đổi. |
| Điện thoại không vào được | Kiểm tra IP máy chủ, cùng mạng, Firewall Private và tùy chọn cô lập thiết bị/AP isolation của Wi-Fi. |
| Sinh viên không thấy buổi học | Giảng viên phải duyệt thành viên và công bố buổi; sinh viên cần tham dự buổi và tham gia trò trước khi gửi bài. |
| Cấp ADMIN thất bại | Kiểm tra quy tắc mật khẩu, kết nối MySQL và username chưa thuộc tài khoản khác. |
| Flyway báo sai checksum | Không sửa/xóa lịch sử migration để bỏ qua lỗi; đối chiếu source migration đúng phiên bản và bản sao lưu. |

Database và thư mục media `data/quiz-images`, `data/quiz-videos` cần được sao lưu cùng nhau nếu có sử dụng ảnh hoặc dữ liệu lịch sử liên quan. Không chia sẻ mật khẩu, file cấu hình riêng hoặc dữ liệu cá nhân qua Git.
