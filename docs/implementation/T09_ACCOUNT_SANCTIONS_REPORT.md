# T09 — Account sanctions, auth epoch và durable effect intents

Ngày 17/10/2026. Đã triển khai và kiểm chứng phạm vi T09 hiện có trong source. Gate T00 P-BAN/P-HOST được lệnh T09 hiện tại duyệt. Reservation/mixed AC-42 chờ T15/T18; award chờ T11.

## Authority, transaction và lock order

`app_user` giữ `ban_kind`, `banned_until_ms`, `ban_version`, `auth_epoch`. TEMP hiệu lực khi giờ Server nhỏ hơn expiry; không cần cron để khôi phục quyền login. UNBAN/expiry không hạ epoch và không xóa target intent Game cũ. Mọi principal giữ epoch lúc cấp; phiên trước ban không thể hồi sinh dù side-effect đóng socket chưa chạy.

```text
POST /api/admin/users/U/{temporary-ban|ban|unban}
  ADMIN DB/session + CSRF + validated UUID/expectedVersion/reason
  AdminSanctionTransactions.apply (READ_COMMITTED, timeout 5s)
    target User U FOR UPDATE (không lấy Room/Game lock)
    immutable receipt lookup trước expectedVersion
    transition/version/epoch + immutable audit receipt
    snapshot ACTIVE GameMember targets, JOINED membership row+generation,
      auth epoch cutoff; reservation adapter snapshot nếu feature tồn tại
    append durable target intents + provisional outbound fences
    COMMIT, thả User lock -> trả receipt/current projection/pendingEffects

BanEffectReconciler (scheduler, tối đa 16 tác vụ đang chạy)
  đọc ID PENDING, không quét roster/targets hiện tại
  GAME ACTIVE -> GameRuntime.administrative, cùng queue Answer/Close
    Room -> Game -> target User -> captured intent
    CAS occupancy(U,X), detach(row,g1), completion cùng TX
  GAME terminal -> Room boundary -> cleanup đúng target (không resume engine)
  MEMBERSHIP -> Room boundary -> Room -> User -> captured intent -> CAS g1
  SESSIONS -> revokeThrough(U, captured cutoff), không giữ User lock/network wait
  RESERVATION -> T15 adapter release đúng captured ID/version, rồi complete
```

OPEN và command Answer/Spin/Star/Continue khóa User theo cùng fence trước đọc quyền/commit. START khóa roster Users theo thứ tự ID tăng; JOIN/REJOIN và CREATE giữ User admission fence khi ghi generation. Các mutation Room xác nhận account/epoch sau khi lấy các khóa Room/Quiz cần thiết. Không mang transaction qua RoomBoundary hoặc đợi Game future trong transaction User. Auth login lưu/register principal mới trong User fence; request filter bảo vệ mọi API xác thực, WS handshake/queued validation và writer đọc authority bền vững.

### Foreign key cũng là khóa

V10 đã được áp dụng **chỉ trên DB test** trước khi phát hiện FK validation của outbox có thể lấy shared lock trên Room/Game/RoomMember khi đang giữ User. V11 additive bỏ các FK cross-aggregate đó và FK actor ADMIN khỏi receipt ghi trong target-only transaction; giữ ID bất biến, target User/action FK, unique key và CAS của owner. Không sửa checksum V10 đã chạy hay V1–V9. Test barrier giữ User trước, cho OPEN sở hữu Game rồi chờ User, sau đó mới insert intent: BAN phải commit mà không chờ Room/Game. Đây là kiểm chứng cho cả implicit FK locks, không chỉ call graph Java.

## Commit order và dữ liệu được giữ

`GameAccessPolicy` đọc union Kick marker và **mọi GAME ban intent**, kể cả PENDING/DONE và kể cả account đã UNBAN/expiry. Do đó Ban commit trước OPEN loại target khỏi participation mới dù ejection chưa chạy. OPEN commit trước Ban giữ frozen participation; SCORE không lọc trạng thái ban hiện tại và không tạo outcome cho câu chưa OPEN. Ban commit trước Answer làm Answer bị từ chối tại User fence; Answer commit trước Ban giữ ACCEPTED_UNSCORED rồi chấm đúng một lần. Không đổi ScoreEngine/MultimodeScoreEngine/RankingEngine.

Intent GAME lưu Game/Room/row/generation từ đúng snapshot tại ban; intent MEMBERSHIP lưu các row JOINED tại ban. Reconciler không dò Game/Room hiện tại của user để thay target. Occupancy chỉ xóa `WHERE user_id=U AND game_session_id=X`; detach chỉ đúng row/user/room/generation/JOINED. Receipt/retry X không xóa claim Y hoặc g2. Không xóa Answer/PlayerSession/History. History FINISHED thêm `BANNED_BY_ADMIN` từ intent, không giả Kick action hay ELIMINATED.

Game terminal được cleanup qua owner Room boundary ngay cả khi runtime terminal còn giữ mode UNAVAILABLE sau interruption. Không đợi actor retention hết mới reconcile, không biến SERVER_INTERRUPTED thành resume. Target đã mất được complete idempotently và chỉ dọn claim đúng Game cũ nếu còn.

## Session và outbound

AuthPrincipal chứa epoch bất biến; `auth_epoch` là DB authority và không được ORM account update ghi đè. Mỗi BAN thay đổi hiệu lực tăng epoch; UNBAN không khôi phục epoch cũ. Reconciler đóng/invalidate các binding có epoch **nhỏ hơn** cutoff đã chụp, không gọi revokeAll theo user. Retry cũ không thu hồi session mới sau UNBAN. Restart không phục hồi auth HTTP session cũ, epoch DB vẫn bảo vệ mọi principal cũ.

`AccountAccessPolicy` có provisional cutoff trước COMMIT và hoàn tác khi rollback. Writer đọc DB/session/binding hiện tại rồi CAS send-start; không giữ khóa khi network send. Ban không đợi socket chậm; frame chưa claim permit bị drop. Frame đã bắt đầu send trước fence có thể hoàn tất sau commit. Game scoped FIFO của T08 được giữ nguyên; không thêm transport/reconnect handler thứ hai.

Mỗi transaction có publication riêng trong provisional fence. Callback rollback chỉ gỡ publication của mình; commit giữ cutoff/restriction đã commit. Không khôi phục cả giá trị cũ vì DB có thể đã thả User lock nhưng callback completion chưa chạy: ban tiếp theo có thể commit cùng epoch sau rollback. Bốn unit test xác định cho account/Game fence chứng minh callback cũ không mở lại fence mới; nếu cả hai transaction rollback thì quyền gửi được khôi phục. CAS send-start vẫn không giữ khóa mạng.

Static assets/trang login vẫn tải được với cookie đã ban. Cookie cũ mất authority nhưng có thể dùng `/api/auth/csrf`/login chung để bắt đầu phiên mới sau khi quyền được khôi phục; login vẫn kiểm tra password và User fence. Logout chỉ thu hồi phiên gọi. Các lỗi ban/epoch có code riêng, không đổi ý nghĩa close 4002 SESSION_REPLACED; close 4001 mang lý do ban/revoked hoặc SESSION_EXPIRED cũ.

## Contract và UI

| Contract | Dữ liệu / guard |
|---|---|
| POST `/api/admin/users/{id}/temporary-ban` | UUID, expectedVersion, minutes, reason; ADMIN + CSRF + target User fence |
| POST `/api/admin/users/{id}/ban` | Cùng contract; minutes phải null |
| POST `/api/admin/users/{id}/unban` | Cùng contract; minutes phải null; không xóa Game intent/cutoff |
| GET `/api/admin/users` | Thêm banKind/bannedUntilMs/banVersion/banned/serverTimeMs; không password/session ID |
| GET `/api/admin/orphan-rooms` | WAITING + Host bị ban/deleted/missing + không ACTIVE Game; offline đơn thuần không orphan |
| POST `/api/admin/rooms/{id}/close-orphan` | UUID/revision/reason; Room boundary + Room→Host User fence; durable audit/replay |
| History FINISHED | Thêm restriction evidence từ ban intent; own History theo quyền cũ khi account dùng được |

Response ban gồm receipt của lệnh gốc **và trạng thái hiện hành** khi replay; pendingEffects chỉ mô tả cleanup, không trì hoãn hiệu lực ban. Cùng UUID khác payload/target/action bị 409. Version cũ bị STALE_VERSION. TEMP thay expiry bằng ServerNow+minutes, không cộng dồn. INDEFINITE→TEMP bị 409; INDEFINITE lặp/no-ban UNBAN là NO_CHANGE, không tăng version/epoch. No-op vẫn lưu một receipt/audit cho request mới. TEMP đã hết hạn cho login mới dù projection chưa cron đổi sang NONE.

Giới hạn đầu vào triển khai: số phút nguyên 1–525600, reason sau trim không rỗng, tối đa 240 ký tự. Đây là cap validation được ghi tường minh; không thêm cơ chế tự động nâng/hạ mức ban. ADMIN/self không là target hợp lệ.

`features/admin/sanctions.js` sở hữu form/confirm/pending request. Mất receipt giữ UUID/body/expectedVersion; không tự retry khi reconnect. STALE_VERSION yêu cầu tải trạng thái mới. Expiry và trạng thái lấy từ giờ Server; không dùng countdown client để tự UNBAN. UI target dọn controller/timer/media và về login với lý do phù hợp. Orphan close không cancel ACTIVE, không chuyển Host, không revoke session Player khác.

## Membership, test 31 và extension T15

Fixture bắt buộc giữ X chưa release rồi thử START Y để xác nhận **USER_ACTIVE_GAME là đúng**. Sau đó chạy effect GAME X hợp lệ để release claim X và detach g1; mới START Y với Host Y độc lập và các thành viên không bận. Replay intent/finish X trong lúc Y ACTIVE không được xóa Y. Chỉ sau khi Y kết thúc/release và Room X WAITING mới JOIN lại cùng row thành g2; replay target g1 phải no-op. Generation chỉ xác định lượt, không tự giải phóng slot.

Chưa có FRIENDLY/reservation trong source. `SanctionReservationPort` là extension contract: snapshot trong User fence, trả immutable reservation ID/version; release sau commit đúng ID/version. Intent hỗ trợ đúng identity này nhưng T09 không tạo reserve/accept/TTL/invitation hay dữ liệu giao hữu giả. Nếu có pending reservation target mà adapter chưa có, reconciler giữ PENDING, không báo hoàn tất giả.

**PENDING T15/T18:** integration r1→r2, hai slot, ACCEPT↔BAN↔START, version cleanup và pending invitation ID khi T15 có schema/API thật. AC-42 ở T09 chỉ đóng membership/occupancy/session, không đóng toàn bộ reservation/mixed extension. Award fence/decision/crash test 37 vẫn PENDING T11.

## Verification

Baseline T08: 327 unit + 189 IT. Kết quả T09: **335 unit và 208 integration test phân biệt đã PASS qua lượt full và lượt chạy lại có mục tiêu**, không cộng các test chạy lại vào tổng IT. Chỉ dùng DB `quizz_task2_test` và schema migration test riêng. V10/V11 chưa áp dụng vào DB vận hành.

| Lượt chạy / bằng chứng trong `target/` | Kết quả thực tế |
|---|---|
| `t09-final.log`, Surefire suffix `T09-final` | 331 unit, 0 failure/error/skipped |
| `t09-fence-unit-final.log`, Surefire suffix `T09-fence-final` | Toàn bộ 335 unit PASS, 0 failure/error/skipped; thêm 4 test callback completion cũ cạnh tranh fence mới |
| Cùng lượt full, Failsafe suffix `T09-final` | 208 IT, 1 failure, 0 error/skipped: fixture Spin ngẫu nhiên trúng HARDSHIP nên Star bị từ chối đúng luật; toàn bộ 189 IT baseline PASS |
| `t09-ban-final.log`, suffix `T09-ban-final` | Sau khi fixture chọn BONUS xác định và bổ sung assertion SCORING khi ejection còn PENDING: 18/18 `AccountBanIT` PASS, BUILD SUCCESS |
| `t09-fence-it-final.log`, suffix `T09-fence-final` | Sau sửa race callback rollback: 18 AccountBanIT + 12 GameKickIT = 30/30 PASS, 0 failure/error/skipped, BUILD SUCCESS |
| `BanMigrationIT` trong lượt full | V9→V10→V11, defaults/constraints/role và loại cross-aggregate FK đúng; chạy lại migration không thay đổi |
| `t09-ban-restart-client-tests.json`, `t09-ban-restart-browser.log` | Chrome thật, LAN origin, 10 nhóm PASS: UI confirm/lost receipt/replay, TEMP expiry/login, pre-ban sessions, UNBAN/X denied/Y hợp lệ, History, g2, orphan close, 360px, commit khi Room/Game bị giữ lock và restart thật |
| `t09-ban-client-tests.json`, `t09-ban-browser-ui-final.log` | 9 nhóm PASS trên JS source cuối: orphan status đổi CLOSED sau receipt, thêm ban non-Host WAITING chỉ detach đúng member, không đóng Room có Host còn quyền |
| `t09-lan-seven-client-tests.json`, `t09-seven-browser.log` | 93 assertions / 7 smoke PASS; chơi đủ 7 mode, private media, lost Answer ACK/reconnect/retry, kết quả/History, single/no-Quiz, cancel và Room reusable |
| `t09-auth-lobby-client-tests.json`, `t09-auth-browser.log` | 15 assertions / 22 smoke PASS: login/register/session/logout/CSRF, socket replacement, author/private sets, Room START thật |
| `t09-lifecycle-client-tests.json`, `t09-lifecycle-browser.log` | 19 component assertions PASS: cleanup controller/listener/timer/media |
| `BanRestartFixture verify 172 17850 4884` | Original intents DONE, membership g2 vẫn JOINED, Room WAITING |
| `BanRestartFixture verify-pending 175 17854 4885` | Ban thực sự PENDING lúc dừng Server đã reconcile DONE sau restart; g1 LEFT, occupancy cũ được giải phóng |
| `python -X utf8 scripts/build-client.py` | 37 JS modules / 56 static files; imports và CSS imports tồn tại |
| `t09-package-final.log` | `mvn -o -B --no-transfer-progress -DskipTests package`: BUILD SUCCESS; JS ADMIN và V10/V11 trong JAR khớp byte với source cuối |
| Kiểm tra workspace cuối | `git diff --check` không lỗi whitespace; 20 hash bảo vệ không đổi; không còn listener test 8088/9227, đã đóng JVM/Chrome do runner tạo |

Không ghi lượt Maven `verify` đầu là xanh: lượt đó thất bại ở fixture Spin nêu trên, rồi suite ban được chạy lại đầy đủ thành công. Không sửa ScoreEngine để ép test qua. Các kết quả runtime nằm trong XML/log/JSON, không suy ra PASS từ review source.

```powershell
mvn -o -B --no-transfer-progress -Pmysql-smoke verify '-DargLine=-Djava.io.tmpdir=D:/BTL_LTM/target' '-Dsurefire.reportNameSuffix=T09-final'
mvn -o -B --no-transfer-progress -Pmysql-smoke test-compile '-Dit.test=AccountBanIT' '-DargLine=-Djava.io.tmpdir=D:/BTL_LTM/target' '-Dsurefire.reportNameSuffix=T09-ban-final' failsafe:integration-test failsafe:verify
mvn -o -B --no-transfer-progress test '-DargLine=-Djava.io.tmpdir=D:/BTL_LTM/target' '-Dsurefire.reportNameSuffix=T09-fence-final'
mvn -o -B --no-transfer-progress -Pmysql-smoke test-compile '-Dit.test=AccountBanIT,GameKickIT' '-DargLine=-Djava.io.tmpdir=D:/BTL_LTM/target' '-Dsurefire.reportNameSuffix=T09-fence-final' failsafe:integration-test failsafe:verify
python -X utf8 scripts/test-ban-client.py --origin http://192.168.1.234:8088 --port 9227 --restart-check
python -X utf8 scripts/test-seven-modes-client.py --origin http://192.168.1.234:8088 --port 9227 --smoke --task 9 --output-prefix t09-lan-seven
python -X utf8 scripts/test-client.py --origin http://192.168.1.234:8088 --port 9227 --smoke
python -X utf8 scripts/test-lifecycle-client.py --origin http://192.168.1.234:8088 --port 9227
```

Restart browser dùng 10 browser contexts cùng một Chrome trên một máy. Fixture giữ **chỉ Room/Game SQL locks**, không giữ User; HTTP BAN vẫn commit và lưu pending intent. Sau đó dừng đúng JVM test, thả SQL lock khi JVM đã tắt, đánh dấu lại completion của action cũ để mô phỏng mất ACK (không sửa target/occupancy/membership), rồi khởi động lại. Game ACTIVE thứ hai kết thúc `SERVER_INTERRUPTED` theo chính sách cũ, không resume; ban INDEFINITE vẫn chặn login. Original Game X vẫn bị chặn và History/Room g2 được giữ. Helper có guard tên DB và tài khoản do runner tạo; không dùng để vận hành.

Một lượt browser trước đã timeout handshake khi Server tắt; log/report FAIL được giữ ở `t09-ban-browser-restart-timeout.log` và `t09-ban-client-tests-restart-timeout.json`. Lượt đầy đủ sau đã PASS. Chrome test cũ không còn chạy khi tiếp tục phiên làm việc nên đã tạo Chrome headless riêng, profile trong `target/`; không dùng profile Chrome cá nhân. Sandbox GPU mặc định của môi trường không khởi động được, Chrome test riêng dùng `--no-sandbox --disable-gpu-sandbox`. Chưa xác minh hai thiết bị LAN vật lý.

Browser restart/7-mode dùng bản JAR được cố định trước lượt test. Lượt UI cuối phục vụ static files trực tiếp từ source bằng tham số test `spring.web.resources.static-locations` để kiểm tra bản sửa trạng thái CLOSED; không đổi config trong repo. Sau browser, sửa race callback fence được kiểm chứng lại bằng toàn bộ 335 unit và 30 Ban/Kick IT, rồi đóng gói JAR cuối; không tuyên bố đã chạy lại browser trên JAR cuối. Một lượt đổi Server test gặp port còn giữ trong shutdown, đã dừng đúng PID JVM do runner tạo rồi chạy lại; không dừng MySQL hoặc tiến trình IDE.

### Đối chiếu acceptance

| V3.4 §18.12 / AC | Bằng chứng và phạm vi |
|---|---|
| 12–14 | TEMP expiry bằng Server clock, login mới sau expiry không cron, nhiều phiên cũ mất quyền qua epoch, slow writer không trì hoãn ban; INDEFINITE còn sau restart thật |
| 15 / B4 | Host PLAYER/SPECTATOR bị ban không cancel ACTIVE; Player khác tiếp tục; close ACTIVE bị từ chối; chỉ orphan WAITING được đóng có audit/replay, phiên người khác còn hợp lệ |
| 15a | Browser thật ban non-Host khi Room WAITING, chỉ detach captured membership, Host/peer còn quyền, healthy Host không orphan-close được; reservation đối thủ chưa tồn tại, PENDING T15 |
| 16–18 | Session mới sau UNBAN, X vẫn denied; TEMP/INDEFINITE/no-op/version/conflicting UUID và replay cũ không tái ban |
| 19 | Effect thất bại còn PENDING/retry; restart thật với pending intent, audit/target không mất, CAS release đúng Game |
| 28, 30 | Spin/Star đã commit được giữ; chưa OPEN không outcome. Answer/BAN cả hai commit order; accepted Answer chấm một lần dù ejection/ACK muộn |
| 31–32 / AC-42 phần membership/occupancy/session | Có negative USER_ACTIVE_GAME khi X chưa release; release X hợp lệ rồi START Y; finish/replay X không xóa Y; release Y rồi Room X WAITING mới JOIN g2; old cleanup/restart không đụng g2 hoặc phiên mới; pending intent chặn X ngay sau UNBAN |
| 39 | Hai ADMIN cùng version chỉ một commit; stale version 409; idempotent audit/intent, replay sau UNBAN trả current state |
| AC-41 | Barrier User fence chứng minh BAN→OPEN và OPEN→BAN; ejection bị hoãn tới sau SCORING vẫn không tạo outcome cho câu chưa có quyền OPEN. User/Room/Game lock graph được test cả implicit FK locking |
| 24, 38 | Baseline engine/ranking/AFK/FIFO/reconnect/session replacement PASS trong full IT; AccountBanIT thêm blocked writer/account fence, T08 giữ Game scope/FIFO |
| B3 / 20–22, 33–37 | PENDING T10/T11: chưa có award decision/ledger; không claim test award PASS |
| AC-42 reservation/mixed, 15a RESERVED | PENDING T15/T18; phải dựng r1/r2 và hai slot bằng lifecycle thật, release r1 hợp lệ trước ACCEPT r2 |

Ảnh đã xem trực tiếp: [ADMIN pending](../assets/ban-2026-10-17/admin-pending-1440.png), [login bị ban](../assets/ban-2026-10-17/banned-login-1440.png), [rejoin hợp lệ](../assets/ban-2026-10-17/rejoined-room-1440.png), [orphan close](../assets/ban-2026-10-17/orphan-closed-1440.png), [ADMIN 360px](../assets/ban-2026-10-17/admin-360.png). Bảng ADMIN cuộn ngang trong vùng riêng trên màn nhỏ, trang không tràn ngang.

## Mapping thay đổi

| Owner/file chính | Thay đổi tối thiểu |
|---|---|
| `auth/service/AccountAccessPolicy`, `auth/security/AccountRequestFilter` | Authority DB, epoch, User admission/commit guard, send-start fence; bảo vệ API, giữ static/login/logout dùng được |
| `AuthPrincipal`, `AuthService`, `AuthController`, `AuthSessionRegistry` | Cấp epoch trong login User fence, kiểm tra epoch, revoke đúng cutoff; giữ password/session/CSRF policy |
| `admin/service/AdminSanctionTransactions`, `dto/SanctionRequest/SanctionResponse` | Transition/version/receipt/audit và capture immutable intent trong một User transaction |
| `admin/repository/BanEffectRepository`, `service/BanEffectTransactions/BanEffectReconciler` | Durable pending discovery, retry, captured CAS cleanup qua owner boundary; không chờ network |
| `admin/service/OrphanCloseTransactions`, `controller/AdminOrphanController` | Đóng orphan WAITING hẹp, version/current Host guard, audit/replay; không cancel ACTIVE |
| `admin/service/SanctionReservationPort` | Extension T15, chưa có friendly implementation |
| `GameTransactions`, `GameAccessPolicy`, `QuestionParticipationStore` | User commit fence và intent-aware OPEN/commands/reads/media/outbound; History ban evidence |
| `RoomService`, `RoomOperations` | JOIN/CREATE/Room mutation User/epoch fence sau Room/Quiz locks |
| `AuthenticatedSocketRegistry`, `AuthenticatedWebSocketHandler` | Account send-start guard, reason phân biệt; giữ một FIFO/writer/socket owner |
| `static/js/features/admin/sanctions.js`, `admin/page.js`, admin CSS | Confirm/duration/pending/retry/expiry và orphan controls; không timer mới |
| `static/js/core/{api,transport,protocol}.js`, `static/app.js` | Account errors/close reason về login + dispose; không DOM trong core |
| `AccountBanIT`, `AccountAccessPolicyTest`, `GameAccessPolicyTest`, `BanMigrationIT` | Commit barriers, generation/session/occupancy, rollback/outbound và callback completion interleaving, migration/defaults/lock graph |
| Existing fixture/reconnect/Kick tests | Cleanup FK mới, pin migration tests V8/V9, READY/login setup phù hợp User fence, giữ assertions cũ |
| `scripts/test-ban-client.py`, `scripts/BanRestartFixture.java` | Browser thật, lost receipt và restart coordination; helper SQL chỉ DB test/owned account prefixes |

Không di chuyển file cũ hoặc tạo folder feature rỗng, không thêm library/framework/build step. Inventory hash theo mốc T08 ở `target/t09-before/manifest.json`; diff theo task ở `target/t09-change-inventory.json`: **33 file sửa, 22 file thêm, 0 file xóa**, ngoài 5 ảnh bằng chứng mới. Hai migration mới additive, không sửa checksum migration cũ. Hash 20 file bảo vệ (V1–V9, application configs, pom, engines/CloseGate và engine tests) trùng mốc T08. `git status` vẫn có toàn bộ thay đổi T00–T08 chưa commit; các dòng xóa `static/client/` là từ T01, T09 không xóa file theo manifest.

Các lỗi trung gian được giữ trong `target/t09-*`: source compile lỗi tên field bị local map che và fixture dùng accessor snapshot không tồn tại; test FK MySQL 3819 được driver dịch thành DataAccessException; một lượt class output trộn compiler IDE/javac gây NoSuchMethodError trên anonymous WS fixture. Đã xóa **chỉ** `target/classes`/`target/test-classes` sau xác minh đường dẫn rồi biên dịch lại, không sửa settings IDE. Test timer T08 cần READY target trước khi giữ User lock vì T09 đã fence cả CONTINUE; không bỏ assertion deadline. Test reconnect giữ Answer transaction phải tạo sẵn phiên HTTP thứ hai trước barrier, rồi thay WS trong lúc Answer còn đang xử lý; login mới không thể vượt User commit fence. Không bỏ assertion ACK/Answer once/replay/replacement. Không báo các lượt fail là PASS.

Không commit/push, không sửa production config/DB. Giữ các thay đổi T00–T08 và file `1.txt` có sẵn. Không tự chuyển T10.
