# ClassQuest

**Hành trình thử thách trong lớp học**

ClassQuest là hệ thống học tập tương tác dành cho giảng viên và sinh viên trong cùng lớp. Giảng viên tổ chức Vòng quay may mắn, Quiz, Điền từ và Bài tập; sinh viên tham gia trên trình duyệt, nhận kết quả realtime và theo dõi bảng xếp hạng.

Hệ thống sử dụng Java 21, Spring Boot, MySQL, React và TypeScript. Frontend được đóng gói cùng Backend trong một file JAR. Các thiết bị trong cùng mạng LAN/Wi-Fi có thể tham gia bằng trình duyệt.

## Cài đặt nhanh trên Windows

### 1. Chuẩn bị

Cài các phần mềm sau:

- **Git**.
- **JDK 21**, có `java` trong PATH.
- **Node.js 22.12 trở lên**, kèm npm và có `node` trong PATH.
- **MySQL Server 8.x** và bật dịch vụ MySQL. Không dùng MariaDB thay MySQL.

Không cần cài Maven riêng; project đã có Maven Wrapper. Lần setup đầu cần Internet để tải Maven và các thư viện Java/npm.

### 2. Clone project

Mở PowerShell:

```powershell
git clone https://github.com/minhtri14012005/BTL_LTM.git
cd BTL_LTM
```

### 3. Kiểm tra cấu hình có sẵn

Repository đã có sẵn hai file, **không cần tạo hoặc copy lại**:

| File | Nội dung |
| --- | --- |
| `config/application-local.properties` | Kết nối MySQL trên máy chạy ứng dụng. |
| `config/admin.local.psd1` | Tài khoản ADMIN dùng cho lần cài đặt đầu. |

Mở `config/application-local.properties`, sửa username/mật khẩu cho khớp MySQL trên máy bạn. Ví dụ cấu hình được cung cấp:

```properties
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=quizz
DB_USER=root
DB_PASSWORD=14012005
SERVER_ADDRESS=0.0.0.0
```

`DB_PASSWORD` là mật khẩu **MySQL**, không phải mật khẩu đăng nhập ClassQuest. Nếu MySQL của bạn có mật khẩu khác, chỉ cần sửa dòng này. Có thể đổi `DB_NAME` nếu muốn dùng database riêng.

Tài khoản ADMIN có sẵn trong `config/admin.local.psd1`:

```powershell
@{
    Username = 'admin'
    DisplayName = 'Administrator'
    Password = 'Abc!1234567890Xyz'
}
```

Bạn có thể sửa thông tin ADMIN trước lần setup đầu. Mật khẩu ADMIN cần ít nhất 16 ký tự, tối đa 72 byte UTF-8, có ít nhất ba nhóm trong chữ thường, chữ hoa, số và ký tự đặc biệt; không chứa username hoặc các từ `password`, `changeme`, `multigame`.

### 4. Chạy setup

```powershell
.\setup.cmd
```

Script tự thực hiện:

1. Đọc cấu hình MySQL và tạo database nếu chưa có.
2. Cài thư viện frontend, build giao diện và Backend, chạy kiểm thử Java.
3. Chạy Flyway để tạo/cập nhật bảng.
4. Tạo tài khoản ADMIN từ `config/admin.local.psd1` rồi kết thúc.

Không cần nhập SQL tạo bảng hoặc import database thủ công. Script tìm `mysql.exe` trong PATH hoặc thư mục cài MySQL thông thường dưới `C:\Program Files\MySQL`.

Database đã có không bị xóa. Nếu username ADMIN đã tồn tại hợp lệ, script giữ nguyên tài khoản và mật khẩu cũ, không tạo trùng và không đặt lại mật khẩu.

**Setup không khởi động web server.**

### Dữ liệu mẫu môn Lập trình mạng

Sau `setup.cmd`, có thể nạp [data/ltm-seed.sql](data/ltm-seed.sql) vào **database mới** để có ngay môn Lập trình mạng (`LTM`), học kỳ, lớp `LTM01`, tài khoản giảng viên `gv1`, một sinh viên mẫu, phân công giảng dạy, thành viên lớp, 15 bộ câu hỏi công khai và 105 câu hỏi. File SQL chỉ chứa dữ liệu; cấu trúc bảng đã được Flyway tạo trong bước setup. Chạy trong PowerShell tại thư mục project, sau khi sửa cấu hình MySQL:

Tài khoản `gv1` là **tài khoản giảng viên do ADMIN tạo**. Sau khi import, đăng nhập ADMIN để xem thông tin tài khoản hoặc đặt lại mật khẩu cho giảng viên.

```powershell
$db = Get-Content config/application-local.properties | ConvertFrom-StringData
$env:MYSQL_PWD = $db.DB_PASSWORD
try {
    & 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' --no-defaults --default-character-set=utf8mb4 -h $db.DB_HOST -P $db.DB_PORT -u $db.DB_USER -D $db.DB_NAME -e 'source data/ltm-seed.sql'
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}
```

Nếu MySQL cài ở vị trí khác, thay đường dẫn `mysql.exe` cho phù hợp. File từ chối nạp khi database đã có dữ liệu môn/lớp/bộ câu hỏi liên quan; không import đè database đang sử dụng. Tài khoản được chuyển cùng **hash mật khẩu hiện có**; SQL không chứa mật khẩu gốc. Mật khẩu giảng viên và sinh viên cần được người tạo tài khoản cung cấp riêng hoặc ADMIN đặt lại mật khẩu giảng viên trong ứng dụng. Sau khi import, chạy `run.cmd`: đăng nhập ADMIN để kiểm tra môn, lớp và tài khoản; đăng nhập `gv1` để xem 15 bộ câu hỏi trong mục của giảng viên.

### 5. Chạy ClassQuest

```powershell
.\run.cmd
```

Mở **http://localhost:8080**, đăng nhập bằng ADMIN đã cấu hình. Với database mới và cấu hình mặc định: username **admin**, mật khẩu **Abc!1234567890Xyz**.

Dừng ứng dụng bằng **Ctrl+C** trong cửa sổ đang chạy.

## Tạo ADMIN riêng hoặc chạy lại setup

Nếu đã có file JAR và database, chỉ cần chạy:

```powershell
.\create-admin.cmd
```

Lệnh đọc hai file cấu hình, chạy migration cần thiết và cấp ADMIN mà không khởi động web server. Đây không phải lệnh đặt lại mật khẩu cho tài khoản đã có, không nâng quyền tài khoản sinh viên/giảng viên đang trùng username.

Nếu đã build và chỉ muốn chuẩn bị database/ADMIN:

```powershell
.\setup.cmd -SkipBuild
```

Sau khi sửa source, dừng ứng dụng rồi build và chạy lại:

```powershell
.\build.cmd
.\run.cmd
```

File JAR nằm tại `target/multigame-0.0.1-SNAPSHOT.jar`. Nếu xóa `target`, cần build lại trước khi chạy. Giữ nguyên các file cấu hình, script, Maven Wrapper và cấu trúc source khi chia sẻ project; không cần dọn hoặc xóa các file đang hỗ trợ việc cài đặt. Những thư viện/kết quả build chưa có trên máy mới sẽ được tải hoặc tạo lại khi chạy setup.

## Sử dụng lần đầu

1. ADMIN tạo năm học, học kỳ, môn học, mở môn và tạo lớp; cấp tài khoản, phân công giảng viên.
2. Giảng viên tạo nội dung, chia sẻ mã lớp, duyệt sinh viên và tổ chức buổi học.
3. Sinh viên đăng ký tài khoản trên web, nhập mã lớp và chờ duyệt để tham gia.

Có sẵn dữ liệu mẫu môn Lập trình mạng tại `data/learning-banks/question-banks.json`: 5 bộ Quiz, 5 bộ Điền từ và 5 bộ Bài tập. Sau khi tạo môn học, có thể nhập bằng `scripts/Import-LearningBanks.ps1` với tài khoản giảng viên; dữ liệu này không tự được nhập khi setup.

## Truy cập qua LAN/Wi-Fi

Chỉ máy chủ cần chạy Java và MySQL. Các thiết bị khác mở trình duyệt và dùng IP máy chủ, ví dụ `http://192.168.1.10:8080`, thay vì `localhost`.

`run.cmd` tự nhận diện IP LAN của máy hiện tại và cấu hình origin cho REST/WebSocket; không cần giữ IP của người viết project trong file cấu hình. Cho phép Java/cổng 8080 qua Windows Firewall trên mạng Private nếu các thiết bị khác chưa truy cập được.

## Phát triển và kiểm thử

Frontend riêng, khi Backend đang chạy:

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

Kiểm thử frontend và Java:

```powershell
npm.cmd --prefix frontend test
.\mvnw.cmd test
```

Bộ tích hợp `-Pbaseline` cần MySQL kiểm thử riêng; không dùng database chính để chạy bộ này.

## Lỗi thường gặp

| Lỗi | Cách xử lý |
| --- | --- |
| Không tìm thấy Java hoặc Node.js | Cài đúng phiên bản, thêm vào PATH và mở lại terminal. |
| Không tìm thấy `mysql.exe` | Cài MySQL Server 8.x hoặc thêm thư mục `bin` của MySQL vào PATH. |
| Access denied / không kết nối được MySQL | Bật dịch vụ MySQL, kiểm tra `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD` trong file cấu hình. |
| Thiếu JAR / không tìm thấy main class sau khi xóa `target` | Chạy lại `setup.cmd` hoặc `build.cmd`. |
| Username ADMIN đã thuộc tài khoản khác | Chọn username khác trong `admin.local.psd1`, rồi chạy `create-admin.cmd`. |
| Cổng 8080 đang được dùng | Dừng ứng dụng cũ; chỉ chạy một Server trên cùng cổng. |
| Giao diện vẫn cũ sau khi sửa source | Build lại, khởi động lại ứng dụng rồi nhấn Ctrl+F5. |
