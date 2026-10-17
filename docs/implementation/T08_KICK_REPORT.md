# T08 — Kick theo Game, OPEN participation và outbound fence

Ngày 17/10/2026. Hoàn tất phạm vi Kick của T08 theo gate T00. Test 37 về Award tiếp tục **PENDING T11**, không được tính PASS trong báo cáo này.

## Gate, phạm vi và lock graph

Lệnh T08 hiện tại duyệt P-KICK của kế hoạch T00: non-Host PLAYER trong Game ACTIVE, restriction riêng Game+User, release occupancy X và detach đúng membership generation, giữ scoring của câu đã OPEN. Không duyệt thêm ban, award hay friendly. Giữ các thay đổi T00–T07 và file `1.txt`; không commit/push, chỉ migration trên DB test.

```text
ADMIN POST /api/admin/games/X/kick
  -> role DB + session + CSRF
  -> GameRuntime.administrative(X), cùng SessionQueue Answer/Close/timer
  -> AdminKickTransactions.apply
       Room X lock -> Game X lock -> sorted User locks (admin + target)
       requestId + fingerprint receipt
       validate ACTIVE / non-Host PLAYER / not ADMIN
       provisional send-start fence
       restriction + immutable action receipt/audit
       DELETE occupancy WHERE user=U AND game=X
       detach WHERE memberId=M AND generation=g1 AND JOINED
       room/game revisions -> COMMIT
  -> refresh runtime view, preserve current gate/ready/deadline
  -> enqueue control notice, release old Room subscription
  -> KICK_APPLIED only after all durable effects COMMIT

START Y: Room Y -> sorted Quiz locks -> sorted User locks -> occupancy check
FINISH X: Room X -> Game X -> sorted Users -> delete only occupancy of X
```

Không có split restriction/release/detach transaction nên không có durable reconciliation job cho Kick. Replay cùng requestId dùng receipt; payload khác bị 409; request mới cho cặp đã restricted là ALREADY_APPLIED, không detach/release hoặc notify lượt mới. Game không có runtime ACTIVE từ chối an toàn, không áp dụng lén ngoài queue. RoomMember tái sử dụng row nhưng rejoin tăng generation; GameMember chụp generation lúc START.

## Participation và các phase

V9 bổ sung `game_question_participation` cùng marker `participation_captured`, độc lập QuestionCloseGate. OPEN giữ User locks theo thứ tự, loại restriction đã commit, ghi đủ Player đủ quyền (kể cả offline) rồi commit. SCORE chỉ dùng identities đã chụp; không lọc restriction/account hiện tại để bỏ outcome. `scored_at_ms` và unique Answer bảo vệ retry. Không tạo Answer tại OPEN và không tạo NO_ANSWER cho câu chưa mở.

GameLifecycle giữ `phaseParticipants` cố định cho INTRO/DECISION/QUESTION. Kick không tự READY, shrink gate, reset deadline hoặc xóa PlayerSession. Phase sau lấy tập mới. CLOSE, chuyển SCORING và job SCORE có các vị trí continuation riêng trong **cùng queue**; Kick có thể nằm giữa hai commit đó. `SessionQueue.defer` vẫn giữ các ingress sau operation đang retry, không cho Kick vượt transaction chưa hoàn tất. `game_decision_action` giữ Spin/Star đã tiêu trước OPEN, kể cả khi không có Answer; History FINISHED thêm decisions/restrictions, không sửa điểm/hạng.

V9 backfill participation của dữ liệu trước restriction từ câu đã OPEN, Answer đã lưu và elimination boundary; không backfill câu chưa OPEN. Không sửa V1–V8 hay ScoreEngine/MultimodeScoreEngine/RankingEngine. Restart vẫn kết thúc Game bằng SERVER_INTERRUPTED theo policy cũ, không tự resume/scoring/award.

## Cổng đọc/ghi và send-start

REST command, queued WS/replay, reconnect, runtime snapshot và DB fallback đều kiểm tra marker. Image/video theo Game ACTIVE cũng kiểm tra; History của member sau FINISHED vẫn đọc theo quyền cũ. Không thay policy ngân hàng câu hỏi PUBLIC/PRIVATE.

SocketOutbound giữ một FIFO/writer. Envelope mang pre-send guard đóng trên Game/User/socket binding generation; recipient selection chặn marker và writer kiểm tra DB/session/current binding rồi claim permit CAS. CAS này là điểm bắt đầu send. Kick đóng gate **trước commit** và rollback mở lại provisional gate; không có cửa sổ từ DB COMMIT đến callback mới dựng fence. Không giữ lock hoặc chờ network I/O khi Kick. Writer drop frame X chưa claim permit, giữ thứ tự frame Y. Frame đã claim send-start trước fence có thể hoàn tất sau commit, không thể thu hồi dữ liệu đã gửi.

Thông báo GAME_ACCESS_REVOKED là control frame, không chứa snapshot/reason nội bộ. Client xóa cache/timer/media của X và về lobby nếu đang ở X; notice/error mất không làm mất guard Server. Không logout hoặc chặn Game Y. Admin xác nhận reason, hiển thị pending/error, retry cùng requestId, chỉ báo applied sau ACK Server; không tự gửi lại khi reconnect.

## Contract và kiểm chứng

| File/owner chính | Trách nhiệm T08 |
|---|---|
| `V9__game_kick_and_question_participation.sql` | Generation, OPEN snapshot, decision evidence, action receipt/audit và restriction |
| `admin/controller/AdminKickController`, `dto/KickRequest/KickResponse` | HTTP + validation; receipt không chứa gameplay |
| `admin/service/AdminKickService`, `AdminKickTransactions` | Guard/admission và TX nguyên tử; callback thông báo sau commit và sau cập nhật view |
| `game/service/GameAccessPolicy` | Guard DB chung + permit send-start, rollback fence và dọn cache terminal |
| `game/repository/QuestionParticipationStore` | Append OPEN identities và resource evidence; History projections |
| `game/service/GameTransactions`, `GameLifecycle`, `realtime/session/GameRuntime` | Capture/score, phase sets/continuations và cùng queue authoritative |
| `room/entity/RoomMember`, `game/entity/GameMember`, `RoomMemberRepository` | Rejoin generation, snapshot khi START, detach CAS theo row/generation |
| `realtime/connection/SocketOutbound`, `AuthenticatedSocketRegistry` | FIFO scoped pre-send guard, current socket/session và control notice |
| `questionbank/service/QuestionBankService` | Guard media theo Game ACTIVE, không đổi ngân hàng câu hỏi |
| `game/service/GameHistoryService`, `dto/response/GameHistoryDetail` | History FINISHED thêm decisions/restrictions, giữ constructors/fields cũ |
| `static/app.js`, `features/admin/{page,kick}.js`, `features/game/page.js`, `features/results/history.js` | Confirm/pending/retry, dispose/routing bị Kick và evidence History |

Không chuyển file cũ, không folder rỗng, không dependency/build step mới hoặc manager/engine/socket nguồn sự thật thứ hai. Core HTTP/CSRF/transport/router không đổi; protocol chỉ thêm thông điệp lỗi.

- POST `/api/admin/games/{id}/kick`: `{requestId,userId,reason}`; ADMIN + CSRF + DB role/session. Response request/action/game/user/status/effectiveAt/revision/release/detach/replayed; không gameplay payload.
- GET `/api/admin/games/{id}/players`: metadata Host/Player/Spectator/account role/restriction từ DB, không credential/câu hỏi.
- Existing snapshots/commands của target trả GAME_KICKED; non-Host rule kiểm tra tại Server.

Baseline trước T08: 320 unit/176 MySQL IT PASS. Lượt full cuối `target/t08-verified.log`, XML suffix `T08-verified`: **327 unit + 189 MySQL IT, 0 failure / 0 error / 0 skipped**. Bao gồm 12 GameKickIT, 1 KickMigrationIT và 7 unit mới về send-start/rollback/FIFO. Sau lượt full, chỉ tăng cường assertion SQL trong GameAfkV2IT rồi chạy lại **14/14 PASS** (`target/t08-ac40-final.log`, suffix `T08-ac40`). Đây là 14 test chạy lại trong tập 189, không cộng thành 203 IT.

```powershell
mvn -o -B --no-transfer-progress -Pmysql-smoke verify '-DargLine=-Djava.io.tmpdir=D:/BTL_LTM/target' '-Dsurefire.reportNameSuffix=T08-verified'
mvn -o -B --no-transfer-progress -Pmysql-smoke test-compile '-Dit.test=GameAfkV2IT' '-DargLine=-Djava.io.tmpdir=D:/BTL_LTM/target' '-Dsurefire.reportNameSuffix=T08-ac40' failsafe:integration-test failsafe:verify
python -X utf8 scripts/build-client.py
```

Static build/import validation PASS: **36 JS modules / 55 files**. Không thêm build step vào ứng dụng. `git diff --check` PASS. Hash baseline T08 xác nhận ScoreEngine/MultimodeScoreEngine/RankingEngine, migrations V1–V8, application configs và pom không bị sửa bởi T08.

### Đối chiếu acceptance và barrier thực chạy

| V3.4 | Bằng chứng runtime | Kết quả/phạm vi |
|---|---|---|
| Tests 4–5 | GameKickIT OPEN/ingress + servlet guards, DB fallback, browser old socket/media/reload/restart | PASS: DB role/CSRF, restriction bền, commands/snapshot/replay bị chặn; không logout user |
| Tests 6–7, 29 | GameKickIT OPEN no-answer và accepted answer; frozen wait set, scorer retry; browser History | PASS: câu đã OPEN chấm một lần; N+1 không outcome; không fake ELIMINATED |
| Test 8 | GameKickIT INTRO/DECISION với fake clock, frozen participants và next stage | PASS: Kick không READY/đóng sớm/reset deadline; phase sau loại target |
| Test 9 | Ba GameKickIT release/generation và START Y ở hai thứ tự khóa; browser X→Y thật | PASS: trước release START Y trả USER_ACTIVE_GAME; sau release Y hợp lệ; finish/replay X không xóa Y |
| Test 10 | GameKickIT HTTP target guards, finished/replay và browser Host disabled | PASS: Host/Spectator/outsider/ADMIN/finished bị từ chối; không auto-cancel X |
| Test 11 | Cùng SessionQueue, CLOSED/SCORING barrier, rollback và late finish/replay tests | PASS phần game result/score ordering; eligibility/award decision vẫn thuộc T11 |
| Tests 26–27 | GameKickIT CLOSED → Kick → SCORING và SCORING-before-commit/retry | PASS: ACCEPTED_UNSCORED không bị bỏ, outcome/điểm một lần; không cho Kick vượt transaction đang defer |
| Test 28 | GameKickIT DECISION tiêu Spin + Star trước Kick; own FINISHED History | PASS phần Kick: evidence bền, không refund/NO_ANSWER cho câu chưa OPEN; nhánh Ban thuộc T09 |
| Test 37 | Chưa có awardDecision/ledger, không triển khai award trong T08 | **PENDING T11**, theo task order T00 |
| Test 38 | SocketOutboundTest + GameAccessPolicyTest + slow-socket GameKickIT, browser old socket | PASS phần Kick: queued X drop sau fence, Y FIFO giữ; Game worker không đợi I/O; nhánh Ban thuộc T09 |
| AC-40a | GameAfkV2IT 14 test, SQL participation/captured marker + đủ bốn outcome | PASS: người offline ngoài Early Close wait set vẫn ở scoring set và nhận NO_ANSWER/penalty theo V2 |
| AC-40b | GameKickIT DECISION trước OPEN và OPEN sau commit | PASS: trước OPEN không participation/outcome; sau OPEN giữ nghĩa vụ hiện tại, từ câu sau loại; actions bị chặn |
| Rollback/durability | GameKickIT injected commit failure; KickMigrationIT V8→V9 và rerun; browser restart thật | PASS: rollback marker/receipt/release/detach/fence; constraints/backfill OPEN-only; restart giữ restriction và History |
| Deadline | GameKickIT phase clock chờ User lock với clock tăng trong lúc chờ | PASS: clock/deadline lấy sau lock, không mất thời gian câu hỏi do chờ lock |

Race tests dùng latch/barrier và khóa MySQL thật tại User/phase commit; không dựa sleep để suy đoán thứ tự. OPEN/Close/Kick/Answer dùng queue hiện có; phép thử generation chỉ rejoin sau release Y và Room WAITING hợp lệ. Không dựng reservation giả để đóng AC-42.

### Browser và LAN-origin

Chrome headless 155, profile riêng, tám browser context độc lập trên cùng máy; HTTP/WS qua `http://192.168.1.234:8088`, Server mysql riêng, DB **quizz_task2_test**, media fixture trong `target/t08-browser-media`. Browser skill gặp lỗi bootstrap đã biết; dùng helper Chrome/CDP sẵn có của repo, không thêm dependency app. Đây là LAN interface trên một máy; **chưa xác minh nhiều thiết bị LAN vật lý**.

| Suite | Report | Kết quả |
|---|---|---|
| Kick ADMIN/target/peer, mất receipt, retry, media, Y và restart | `target/t08-kick-client-tests.json` | **10 nhóm PASS**, Game X 3806/Room 4338, Game Y 3807/Room 4339 |
| Bảy mode hiện có | `target/t08-lan-seven-client-tests.json` | **93 assertions / 7 network smoke PASS** |
| Auth/lobby | `target/t08-auth-lobby-client-tests.json` | **15 assertions / 22 network smoke PASS** |
| Lifecycle/dispose | `target/t08-lifecycle-client-tests.json` | **19 assertions PASS**, timer/listener và delayed callback cleanup |

Runner Kick: `python -X utf8 scripts/test-kick-client.py --origin http://192.168.1.234:8088 --port 9227 --restart-check`. Cờ restart cần vòng điều phối dừng/khởi động lại đúng Server test do phiên chạy sở hữu rồi ghi tín hiệu `.continue`; không điều khiển dịch vụ khác. Lượt này đã restart process thật, đăng nhập lại rồi xác nhận X vẫn 403, own History có marker và Y vẫn đọc được.

Browser cố ý bỏ receipt HTTP đầu tiên **sau khi Server đã trả KICK_APPLIED**. ADMIN giữ pending, thử lại cùng UUID/body; target rời gameplay về lobby và session vẫn dùng được. Target tạo/chơi Y trong khi X ACTIVE; old socket không nhận thêm gameplay X sau fence. Video đã được cấp trước Kick bị 403 sau Kick, peer vẫn 200; ảnh câu tương lai chỉ peer truy cập được. X FINISHED vẫn giữ Answer/score10 của target, không có outcome câu tiếp theo; historical media theo quyền cũ vẫn 200. Không cấp award do mở History.

Ảnh bằng chứng: [pending](../assets/kick-2026-10-17/admin-pending-1440.png), [applied](../assets/kick-2026-10-17/admin-applied-1440.png), [ADMIN 360px](../assets/kick-2026-10-17/admin-360.png), [target lobby](../assets/kick-2026-10-17/target-lobby-1440.png), [own History](../assets/kick-2026-10-17/target-history-1440.png). Assertion browser xác nhận assets 200 và không tràn ngang viewport 360px.

Server test/Chrome riêng đã dừng sau kiểm chứng; MySQL và các process IDE có sẵn được giữ nguyên. V9 chỉ chạy trên DB test và private migration schema; **chưa áp dụng migration hay provision vào DB vận hành**. Không lưu password ADMIN/secret mới trong repo.

Các lỗi trung gian được giữ lại trong target: fixture Kick đầu giả định có thể chạy giữa `SessionQueue.defer` nên timeout; đã dùng continuation cùng queue và barrier tại phase đã commit. Hai test race đầu dùng `callRealMethod` trên interface Spring Data nên không hợp lệ; instrumentation hiện lấy khóa MySQL thật bằng EntityManager, không fake dữ liệu. Một lượt discovery lỗi do class output không có superclass; đã xóa **chỉ** `target/classes` và `target/test-classes`, Maven biên dịch lại bằng javac, không thay settings IDE. Không báo những lượt đó PASS.

Test §18.12 số 37 (crash gameResultCommit → awardDecisionCommit/emit) phụ thuộc T11: **PENDING T11**, không có Award contract/ledger để kiểm chứng và không tạo fake Award trong T08. Ban/AC-41, B3/B4 và friendly reservation AC-42b/c tiếp tục ở T09/T11/T15/T18. B1/B2, membership generation/CAS và AC-40 **phần Kick** đã được kiểm chứng như bảng trên; không dùng kết quả này để đánh dấu toàn bộ ADMIN V3.4 hoàn tất. Không tự chuyển T09.
