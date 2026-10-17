# T03 — Login/Register/Lobby

Hoàn tất ngày 2026-10-16 trên kiến trúc T01/T02. Giữ một luồng đăng nhập chung. Source hiện chỉ cấp `ROLE_USER`, response tài khoản không có role; không thêm ADMIN, login riêng hay redirect giả. ADMIN tiếp tục ở T07.

## Thay đổi

| File | Trách nhiệm |
| --- | --- |
| `static/js/features/auth/page.js` | Login/register hai cột, tab điều hướng, hiện/ẩn mật khẩu có aria, loading và lỗi tập trung; giữ IDs, field names và validation hiện có |
| `static/js/features/lobby/home.js` | Hero, số liệu thật từ ba API, phòng gần đây, empty state và link tạo/join/thư viện/lịch sử |
| `static/js/features/lobby/rooms.js` | Renderer thẻ dùng chung, status/PLAYER occupancy/ownership, phân trang; giữ requestId/revision và confirm xóa/rời phòng |
| `static/styles/features/auth.css`, `lobby.css` | Styles theo tokens T02, responsive; không dùng CDN hoặc asset ngoài |
| `static/styles/features/pages.css`, `catalogue.css`, `static/styles.css` | Chuyển khai báo auth/room-card cũ về owner mới, cập nhật imports; không thêm cascade override để che rules cũ |
| `static/README.md` | Ownership và hướng dẫn kiểm tra |
| `scripts/test-auth-lobby-client.py` | Test browser cho pending/error/stale response, policy message và DTO room |

Không di chuyển file hoặc thêm folder production mới. Không sửa Java, backend contracts, core API/transport/router, DB schema/migrations, cấu hình production hoặc nghiệp vụ game. Đối chiếu hash với `target/t03-before/manifest.json`: thay đổi production chỉ ở các JS/CSS nêu trên và README. Các thay đổi T00–T02 và `1.txt` được giữ nguyên.

Auth dùng nguyên POST `/api/auth/login`, POST `/api/auth/register`, CSRF refresh sau login và session cookie hiện có. Nút submit bị khóa khi pending; guard theo từng form chống gửi trùng; không tự phát lại login khi lỗi network. Lỗi credentials dùng message chung của `INVALID_CREDENTIALS`; đăng ký vẫn giữ policy `USERNAME_TAKEN` hiện tại, không gọi endpoint kiểm tra tồn tại tài khoản. Feedback nhận focus; response cũ sau đổi route không redirect/connect. Listener chỉ gắn vào DOM của form, không thêm timer/global listener/reconnect handler.

Lobby giữ GET `/api/quizzes?size=1`, `/api/rooms?size=5`, `/api/system/status` và `/api/rooms?page=N&size=12`. Số phòng là phòng người dùng là thành viên, không phải số phòng công khai/toàn hệ thống. Số bộ câu hỏi là tổng API cho phép xem. Cards lấy tên/status/members/maxPlayers/hostUserId từ DTO; không thêm phòng hoặc số liệu giả vào production. Join qua mã, tạo phòng, public/private packs và author policy dùng nguyên màn/API T01. Loading/error tải trang dùng primitives và render-version guard của App hiện có. Status trên card là snapshot API khi tải trang; vào phòng để nhận cập nhật realtime.

## Kết quả chạy thật

| Kiểm tra | Kết quả và bằng chứng |
| --- | --- |
| Static import build | PASS, 28 JS modules / 43 files; `python scripts/build-client.py` |
| Java unit/web-only | 315 tests PASS, 0 failure/error/skip; `target/t03-unit.log` (bao gồm SystemWebTest kiểm tra anonymous static serving) |
| Package | BUILD SUCCESS; `target/t03-package.log` |
| AuthNetworkIT với MySQL test | 7 tests PASS; `target/t03-auth-network.log` |
| Auth/lobby browser unit + network smoke | 15 unit / 22 smoke PASS; `target/t03-task11a-client-tests.json` |
| T03 UI fixtures | 9 nhóm PASS; `target/t03-auth-lobby-tests.json` |
| Design system | 14 nhóm / 40 layouts PASS ở 360/390/768/1440px, keyboard/focus/validation/contrast/reduced-motion; `target/t03-t02-design-system-tests.json` |
| Lifecycle fixtures | 19 checks PASS; `target/t03-t01-lifecycle-client-tests.json` |
| Room delete network smoke | 6 checks PASS: draft/closed, member leave, ACTIVE guard, End→Room reuse, giữ History; `target/t03-room-delete-browser-tests.json` |
| LAN static HTTP | 42 JS/CSS/HTML × 2 địa chỉ interface = 84 responses HTTP 200 và body khớp source; `target/t03-lan-assets.json` |
| Diff whitespace | `git diff --check` PASS |

Browser smoke dùng Chrome headless 155 với profile riêng, app test port 8088 và DB `quizz_task2_test`, Flyway tắt; tạo dữ liệu smoke trong DB test, không trỏ DB production. Auth/lobby smoke kiểm tra đăng ký/đăng nhập, session, WS ready, packs public/private, create/join và game/results. Không chạy lại toàn bộ 170 MySQL IT hoặc bảy-mode gameplay suite trong T03; không suy diễn PASS cho các suite chưa chạy.

Kiểm tra LAN thực hiện **từ chính máy** qua `172.16.0.2` và `192.168.1.234`, server test tạm bind `0.0.0.0`; không thay allowed-origins production. Chưa xác minh truy cập từ thiết bị LAN khác/firewall, touch trên thiết bị thật hoặc screen reader thật. Touch viewport là browser emulation. Plugin Browser gặp lỗi bootstrap từ phiên trước; dùng helper CDP đã có trong project với browser test riêng theo fallback đã khảo sát. Server và Chrome test được dừng sau kiểm tra.

## Visual QA

Đã mở xem các ảnh sau; đây là **UI DTO fixtures**, không phải bằng chứng network/multiplayer. Nội dung dài ở thẻ phòng/sidebar dùng để kiểm tra wrap; dữ liệu fixtures không nằm trong app production.

- [Login desktop](../assets/auth-lobby-2026-10-16/login-1440.png)
- [Register 360px](../assets/auth-lobby-2026-10-16/register-360.png) — ảnh viewport, form cuộn dọc bình thường
- [Lobby desktop](../assets/auth-lobby-2026-10-16/home-1440.png)

Không có policy gate mới cho T03. Server tiếp tục là nơi quyết định quyền; T07 phải triển khai role thật trước khi hoàn thiện redirect ADMIN.
