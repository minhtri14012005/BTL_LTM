# T07 — ADMIN role, shared login và dashboard chỉ đọc

Khởi đầu 16/10/2026, hoàn tất 17/10/2026. Scope H1/T07; giữ các thay đổi T00–T06 đang có trong workspace. Không triển khai kick/ban/unban, audit ledger hoặc award.

## Cấu trúc và contract

| Owner/file | Thay đổi |
|---|---|
| `user/enums/UserRole.java`, `user/entity/UserAccount.java` | Role DB `USER`/`ADMIN`; Host vẫn là quyền theo Room |
| `db/migration/V8__server_account_roles.sql` | Thêm role mặc định USER và CHECK case-sensitive; không seed password/account, không sửa V1–V7 |
| `auth/security/AuthPrincipal.java`, `auth/service/AuthService.java`, `auth/dto/response/UserResponse.java` | Authority từ account DB; login/me trả role; registration luôn gán USER; kiểm tra ADMIN bằng account DB còn active |
| `auth/security/SecurityConfiguration.java` | Guard HTTP ADMIN trước các matcher cũ; giữ CSRF/session/origin và security context hiện có |
| `admin/controller`, `service`, `repository`, `dto` | Explicit projection, phân trang, no-store, kiểm tra role DB + session trước đọc; không trả entity hoặc hash |
| `admin/provision/AdminProvisionCommand.java`, `admin/service/AdminProvisionService.java` | CLI non-web riêng, không component scan Game runtime/startup cleanup; provision transaction idempotent |
| `realtime/websocket/*` | `/ws/admin` dùng cùng handler/registry/FIFO, kiểm tra DB ADMIN tại handshake/connect/frame; chỉ AUTH_READY, đóng 1008 khi gửi command |
| `static/js/features/admin/page.js`, `styles/features/admin.css` | Ba tab dữ liệu thật; DOM text an toàn, pagination, refresh, empty/error state, table scroll trên mobile |
| `static/app.js`, `features/auth/page.js` | Form login chung; ADMIN vào `/admin`, USER vào lobby; ADMIN không mở transport gameplay tự động; logout về login chung |

Không di chuyển file Java cũ, không thêm dependency/build step/framework. Core HTTP/CSRF, transport, router, SocketOutbound/session registry và Game engines giữ owner cũ. UI ADMIN không đăng ký socket/listener/timer riêng.

| Contract | Dữ liệu / guard |
|---|---|
| POST `/api/auth/register` | Input cũ; role/authorities dư không có tác dụng, response role USER |
| POST `/api/auth/login`, GET `/api/auth/me` | `id, username, displayName, role`; không credential; ADMIN redirect bằng role response Server |
| GET `/admin`, `/admin/` | Entry HTML chung, ADMIN mới được đọc; anonymous 401, USER/Host 403 |
| GET `/api/admin/users?page=0&size=20` | ID, username, displayName, role, created/deleted timestamps; không hash/session |
| GET `/api/admin/games?page=0&size=20` | Game ACTIVE: ID/Room/Host/title/phase/question index/count/schema/start; không câu hỏi, đáp án, Room code |
| GET `/api/admin/orphan-rooms?page=0&size=20` | Room WAITING có Host thiếu/đã soft-delete, không Game ACTIVE; reason từ DB. Offline/disconnect không phải orphan |
| GET Upgrade `/ws/admin` | Cùng allowed origins/no query/session registry; ADMIN DB còn active, AUTH_READY; không feed quản trị hay command mutations |
| Các endpoint GET admin | `items,page,size,totalElements`, size 1–100, page không âm/offset không overflow; `Cache-Control: no-store` |
| Các method ghi `/api/admin/**` | Deny 403; không có endpoint kick/ban. Không có `/admin/login` hoặc `/api/admin/audit` |

AuthPrincipal chỉ giữ authority tại login; mỗi endpoint dữ liệu và admin WS còn kiểm tra DB hiện tại. Hạ role trong DB chặn session cached ADMIN; nâng role từ USER cần login mới để nhận authority. Không có API thay role trong T07. ADMIN không tự có membership/Host và không bypass guard snapshot/cancel của Game khác. T07 không thay chính sách account cutoff/ban/session epoch của T09.

## Provision an toàn và cách sử dụng

Build JAR rồi chạy từ PowerShell; chọn DB đích rõ ràng:

```powershell
mvn -o -B --no-transfer-progress package
.\scripts\provision-admin.ps1 -Database quizz_task2_test
```

Thay tên database bằng DB đích đã chọn khi triển khai. Script tự đọc `config/admin.local.psd1` nếu có (hoặc `-ConfigFile <path>`), gồm `Username`, `DisplayName`, `Password`. File local chứa mật khẩu rõ để chủ máy tra cứu, bị Git bỏ qua, nằm ngoài static; chỉ lưu trên máy chủ và giới hạn người được đọc. Không đưa mật khẩu vào command line/log. Nếu không có file hoặc truyền `-Username`/`-DisplayName`, script hỏi phần còn thiếu, password bằng `Read-Host -AsSecureString`. DB credentials lấy theo cấu hình external hiện có. Chạy script là bước tạo tài khoản vào DB; sửa file không tự reset mật khẩu ADMIN đã tồn tại. Đăng nhập trang chung bằng username/password đã provision; ADMIN được chuyển tới `/admin`.

CLI nhận ba env vars `MULTIGAME_ADMIN_USERNAME`, `MULTIGAME_ADMIN_DISPLAY_NAME`, `MULTIGAME_ADMIN_PASSWORD` trong process con. Script restore/clear env và zero/free BSTR khi xong. Password ít nhất 16 ký tự, tối đa 72 UTF-8 bytes, ít nhất ba nhóm ký tự; từ chối username/các từ mặc định dễ đoán. Dùng password manager tạo secret ngẫu nhiên. Hash bằng BCrypt cost 10 theo auth hiện có, không lưu plaintext.

Unique username + transaction/row lock serialize provision đồng thời. Chạy lại cùng ADMIN active giữ nguyên ID/hash/display name; không reset password mỗi restart. Username đã là USER hoặc đã deleted thì từ chối và rollback, không tự promote/reactivate. Không có bootstrap tự động khi web server khởi động. CLI dùng datasource/Flyway/JPA tối thiểu, không chạy web socket/game recovery/demo data.

Việc chạy CLI có thể áp dụng migration chưa chạy trên DB đích. Trong T07 chỉ chạy trên `quizz_task2_test` và các schema test riêng; chưa áp dụng migration/provision vào DB vận hành `quizz`. Không lưu/in password fixture và không cung cấp tài khoản/mật khẩu mặc định cho deployment.

## Kiểm chứng

Java chạy bằng Java 21.0.9/Maven offline, Spring Boot 3.5.16 và MySQL thật:

```powershell
mvn -o -B --no-transfer-progress -Pmysql-smoke verify '-DargLine=-Djava.io.tmpdir=D:/BTL_LTM/target' '-Dsurefire.reportNameSuffix=T07-verified'
```

- **320 unit tests PASS**, 0 failure/error/skipped, gồm AuthPrincipal role/credential erasure, provisioning validation/wipe và architecture guard.
- **176 integration tests PASS**, 0 failure/error/skipped. Gồm 170 baseline và 5 AdminSecurityIT + 1 AdminRoleMigrationIT mới. Evidence: `target/t07-verified.log`, XML/text suffix `T07-verified` trong surefire/failsafe reports.
- AdminSecurityIT dùng HTTP/raw WS/MySQL thật: anonymous 401, USER/Host 403, forged registration USER, ADMIN 200, no-store/projection/paging, orphan deleted vs offline, denied mutations/audit, BCrypt/idempotency/concurrent provisioning, không promote/reactivate, DB demotion với cached authority, revoked session, WS origin/read-only/logout/demotion.
- Migration test dùng schema riêng `quizz_t07_roles_5fc07ae74502`: V7 → V8 giữ nguyên account ID/hash/timestamps/deleted rows; role cũ USER, CHECK từ chối OWNER và lowercase admin bằng MySQL error 3819; Flyway rerun 0 migration. Không dùng lỗi SQL syntax làm bằng chứng constraint.
- Lần full verify đầu phát hiện fixture MultimodeRoomStartIT còn kỳ vọng V7 và test mới kỳ vọng sai subtype JDBC exception cho CHECK. Đã cập nhật version 8 và kiểm tra SQL error code 3819; full verify sau đó PASS toàn bộ. Không đổi engine để làm test qua.

Browser Chrome headless 155 với profile riêng; origin HTTP/WS `192.168.1.234:8088`, DB `quizz_task2_test`, storage media test dùng lại `target/t05-media`:

- `scripts/test-admin-client.py`: **10 nhóm checks PASS**. CLI chạy thật, hai lần provision rồi login bằng password đầu; không secret trong file/command line. USER/Host/sửa JS bị 403; ba tab GET thật; Game ACTIVE tạo qua bốn phiên thật hiện metadata; ADMIN không được snapshot/cancel Game khác; chạy CLI trong lúc Game ACTIVE không khởi động cleanup; canceled Game rời danh sách; layout 360/1440px và text injection; native keyboard Enter refresh chỉ GET; logout → 401. Báo cáo `target/t07-admin-client-tests.json`, log `target/t07-admin-browser-checked.log`.
- Auth/lobby regression **15 assertions + 22 network checks PASS**, lifecycle **19 checks PASS**. Báo cáo được lưu riêng tại `target/t07-auth-lobby-client-tests.json`, `target/t07-lifecycle-client-tests.json`.
- Seven-mode regression **93 assertions + 7 network checks PASS**: private media, INTRO/replacement, Quiz Spin/Star/replay, reconnect SONG/clues, Answer ACK bị drop rồi retry sau FINISHED, final/history, single/no-Quiz và Host cancel. Báo cáo `target/t07-stable-lan-seven-client-tests.json`, log `target/t07-seven-verified.log`.

Lần smoke ADMIN đầu dừng ở helper dùng `crypto.randomUUID()` trên HTTP LAN (API đó chỉ có ở secure context), trước khi tạo Game. Helper đã đổi sang UUID Python; production vẫn dùng `core/protocol.js` với `crypto.getRandomValues()` như trước, không đổi flow vì lỗi fixture.

Một lần package đè lên JAR của **server test đang chạy** trên Windows thất bại rename, làm smoke seven-mode timeout tại video do classloader của process bị ảnh hưởng. Giữ báo cáo FAIL `target/t07-final-lan-seven-client-tests.json`; dừng đúng process test, package lại thành công (`target/t07-final-package-verified.log`), chạy bằng bản sao cố định `target/t07-runtime.jar` rồi rerun seven-mode PASS đầy đủ. Không dùng lần lỗi này làm bằng chứng regression PASS. Check keyboard bổ sung ban đầu timeout do tab Chrome headless chưa có focus; harness dùng Page.bringToFront/focus emulation và native Enter, sau đó PASS; không sửa handler UI để né test.

Các lệnh browser cuối (Chrome owned debug port 9227, server test port 8088):

```powershell
python -X utf8 scripts/test-admin-client.py --origin http://192.168.1.234:8088 --port 9227
python -X utf8 scripts/test-client.py --origin http://192.168.1.234:8088 --port 9227 --smoke
python -X utf8 scripts/test-lifecycle-client.py --origin http://192.168.1.234:8088 --port 9227
python -X utf8 scripts/test-seven-modes-client.py --origin http://192.168.1.234:8088 --port 9227 --smoke --task 7 --output-prefix t07-stable-lan-seven
```

Chọn output-prefix mới khi chạy lại để không đè evidence cũ. Script ADMIN chỉ provision trong `quizz_task2_test`; không chạy trên máy dùng DB test này cho vận hành.

Ảnh từ HTTP/API thật, đã mở xem để kiểm tra bố cục: [Users desktop](../assets/admin-2026-10-17/users-1440.png), [Game mobile](../assets/admin-2026-10-17/games-360.png), [Orphans mobile](../assets/admin-2026-10-17/orphans-360.png). Tên chứa `<img ...>` là fixture escape và được hiển thị thành text, không tạo HTML/image.

Static inventory: `python scripts/build-client.py` xác minh **35 modules/54 files**; `git diff --check` không lỗi whitespace. So với `target/t07-before/manifest.json`, V1–V7, application configs, pom và core transport/API/router giữ nguyên. Các thay đổi implementation chỉ ở role/auth/admin/handshake+channel và frontend ADMIN chung login; không sửa game/room engines.

PowerShell provision script đã parse syntax PASS; CLI/env/password hashing và idempotency đã chạy thật. Chưa kiểm chứng thao tác gõ secret tương tác bằng bàn phím người vận hành, trình duyệt engine khác, screen reader hoặc nhiều thiết bị LAN vật lý. Các browser contexts LAN-origin ở trên cùng máy. Không gán các giới hạn này là runtime PASS. Dừng các process server/Chrome test do task tạo sau kiểm tra; không dừng MySQL, không commit/push và không đụng `1.txt`.

## Giới hạn và policy gates

Orphan definition hiện có chỉ dựa vào missing/deleted account. Source chưa có ban state nên không gán nhãn “Host bị ban”, không cung cấp close orphan. Audit không có bảng/API nên không dựng tab hay dữ liệu giả. Danh sách là GET tại thời điểm refresh, không tự suy diễn presence.

Lệnh T07 hiện tại cho phép triển khai role/provision/read-only H1. Các gate P-KICK, P-BAN/HOST, B1–B4, AC-40/41/42 và test31 vẫn chưa triển khai/được đóng bởi T07. Không tự chuyển sang H2/H3, không thay occupancy, Answer commit, CloseGate, RoomMember generation, award eligibility hoặc history.
