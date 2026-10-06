# Project status

Ngày cập nhật: **06/10/2026, Asia/Bangkok**. Task hiện tại: **TASK 15 — PARTIAL, dừng để review**. Kỹ thuật local/benchmark/docs đã hoàn thiện; hồ sơ môn, LAN/máy mới và contribution cá nhân còn thiếu. Không commit/push.

## Task 15 — Tài liệu, thực nghiệm và bàn giao

Đầu vào Task14 review fixes/Task11A-11B-12 UI thật có evidence; giữ code production/stack/schema/dependency/transport. README viết lại cho trạng thái hiện tại, entry points/ownership/trace/install/DB/config/server-client/test/experiment/limitations/references. Tạo final-report.md đầy đủ architecture/ERD/phase/sequence/scoring/setup/Results/Discussion và team-contributions.md:4 nhãn, thông tin cá nhân trống, phân công dự kiến cân bằng gần25%/người, contribution thực tế trống. Topics/Instruction/Submission/mẫu README môn gốc vẫn thiếu, không thay bằng1.txt/prompt; không xác nhận đạt môn/format nộp.

Harness opt-in test-classpath ExperimentServer +Python Client/rawWS: mỗi Client Account riêng, baseline receipt erasure sau commit/không giữ replay giữa các trial, reconnect socket+Room subscribe không fullsnapshot; proposed production replay/RECONNECT. Không baseline trong production JAR hoặc message/endpoint mới. Mẫu config copy byte-identical không credential, password inherited environment; fresh schema FlywayV1/V2 đã migrate thật; production JAR readinessUP/MySQL8.0.45.

**Kết quả mới:** final affected Maven verify BUILD SUCCESS, **47 unit+26 MySQL/network integration=73 pass**,0fail/error/skip; build frontend11modules/13assets; Chrome154 production smoke **32 tests/9 flow groups PASS**,4 cookie contexts,3 trận riêng (10 câu COMPLETED, SpectatorCancel, eliminatedHostCancel), real HTTP/rawWS/MySQL. Trace Register/Login→Quiz/ảnh→Room/Start→Spin/Star/Answer→lostACK/reconnect/NO_ANSWER→Final/History/Cancel/RoomWAITING đạt. Baseline/proposed mỗi mode22 Game/Room tuần tự,30trials cho từng5 scenario reliability,3round ở3/5/10/20 Player (228 accepted load samples/mode). Proposed original response/snapshot30/30 mỗi scenario, baseline0/30;0 duplicate side effect/0 durable invariant violation qua44 Game checks. Server p95 proposed20Client451.814ms, baseline285.865ms: không claim proposed luôn nhanh hơn. Loopback cùng máy i5-13500HX/RAM~15.73GiB, không LAN/Internet inference.

Lệnh/evidence: [handoff](../experiments/handoff/evidence-index.md) có full command/regression XML/readiness/SQL/browser/screenshots/source/JAR hash; [experiment protocol](../experiments/experiment-protocol.md), [CSV/results/figures](../experiments/results/2026-10-06-loopback/results.md). MySQL SELECT sau51 tổng Game (4pilot+44official+3browser):0 ACTIVE/UAG/duplicate Answer/cross-game Answer/Spin pool inconsistency/invalidunscored;50CANCELLED/1COMPLETED. Đã dừng đúng Java/Chrome do scripts tạo, không ngắt MySQL. Reuse Task13 full224+92 và Task1447+77 cho phần không đổi, không chạy/cộng trùng full suite. Contract chỉ sửa nhãn UI/Task11B planned cũ, không field/protocol mới.

**Chưa kiểm chứng/còn thiếu:** LAN3–4 máy thật, build/run máy mới, Compilatio, thông tin4 thành viên, xác nhận phân công/công việc thực tế và evidence cá nhân, Instruction/Submission/mẫu môn/định dạng final. Đã có kịch bản/lệnh/ô trống để hoàn thiện, không blocker kỹ thuật local còn biết. Nhóm/Compilatio/môn không được giả điền. **PARTIAL toàn Task15**, technical local PASS, chưa hồ sơ bàn giao đầy đủ. Flow bảo vệ: auth/generation→ingress→FIFO→replay/guard→service/engine→DB commit→runtime/cache/event→UI revision/snapshot→History. Dừng review.

## Task 14 — Technical review

Trace rộng một lượt trên implementation/requirements và evidence Task11A/11B/12/13; giữ architecture Task2.5. Không phát hiện thêm lỗi nghiêm trọng có căn cứ ngoài các lỗi đã sửa dưới đây; đây không phải chứng minh hệ thống không thể có bug.

| Severity / finding | Tái hiện và sửa / evidence |
|---|---|
| **P1 — REST Start dùng phiên đã Logout khi chờ Room** | Giữ Room boundary bằng latch, request đã preauthorize, Logout204 trước release: trước sửa vẫn200/tạo Game. GameStartAuthorizationIT nay401/không Game/event, login lại cùng request tạo đúng một Game và replay nguyên response sau Cancel/FINISHED. AuthSessionRegistry.validation capture phiên gốc; GameController + RoomController revalidate trong boundary trước replay/transaction; RoomOperations Create có callback. RoomNetworkIT thêm4 case Create/Edit/Open/Close sau Logout:401, không đổi DB/revision |
| **P2 — Game broadcast gộp auth rejection vào lỗi DB** | Inactive account thật trong test DB, QUESTION_START đã commit: trước sửa đóng1011. Registry tách AuthFailure4001 SESSION_EXPIRED khỏi DataAccessException1011 AUTH_UNAVAILABLE. Raw WS test xác nhận4001, User khác vẫn nhận câu/Game ACTIVE/UAG không bị reset;2 unit phân biệt auth/DB error |
| **P2 — Canonical contract drift** | Bảng WS close code còn ghi CANCEL_GAME unsupported dù parser/adapter/Task11B đã code. Sửa dòng này và trigger auth khi broadcast; REST bổ sung session revalidation trước Room replay/mutation. Không endpoint/message/field mới |

Trace kiến trúc: **HTTP session/Principal + Origin/CSRF → strict parser/connection generation → ingress timestamp/sequence/enqueue nguyên tử → processor per Game → GameLifecycle → GameTransactions/ScoreEngine → proxy commit → copied runtime/receipt → registry event → UI revision/receipt guards → History DTO**. Start đi RoomBoundary trước Game ID và handoff sau commit; User+Room cache không chuyển scope, Game cache giữ10 phút sau retire. Game service/engine không import wire/queue/WS; controller và realtime adapter nối các port, game/room có repository reference theo use-case đã freeze, không vòng bean DI. Engine không clock/random/JPA; enum theo owner, root scan11 Entity/Repository, không Entity trả qua API. Quiz metadata non-owner/PRIVATE organizer, released image reference, OPEN/SCORING và History unscored được đối chiếu source/contract/test; không đổi luật.

Trace race/consistency: handler chạy ngoài monitor; queue lock order states→state, timer cùng FIFO/generation/question/phase/token. DB lock Room→Quiz→Users tăngID cho Start và Room→Game→Users tăngID cho terminal; UAG gồm Spectator, roster bất biến. Tính toàn câu trước mutation/commit; rollback không cache ACCEPT/partial runtime, finite retry/cleanup phân biệt pending với durable success. Cancel/scoring cùng queue; Host eliminated vẫn Cancel. Snapshot cá nhân/cùng actor ordering, public correctness chỉ sau scoring. Session A/B isolation, old socket disconnect/replacement, stale timer và retry close/FINISHED vẫn được regression.

```powershell
$env:MAVEN_USER_HOME = Join-Path $PWD '.cache/maven-home'
.\mvnw.cmd -B --no-transfer-progress verify -Pmysql-smoke '-Dtest=AuthenticatedSocketRegistryTest,CodebaseStructureTest,SystemWebTest,GameReplayCacheTest,GameCommandParserTest,SessionQueueTest,RoomBoundaryTest' '-Dit.test=AuthNetworkIT,RoomNetworkIT,GameStartAuthorizationIT,GameLifecycleIT,GameCommandNetworkIT,GameReconnectNetworkIT,GameHistoryCancelIT,GameIsolationIT,MySqlSmokeIT' '-Dspring.flyway.enabled=false' -Ddebug=false
```

**Diff cuối BUILD SUCCESS:47 unit +77 MySQL integration =124 tests, failures/errors/skipped=0.** Focused reproductions trước sửa thất bại đúng4001≠1011 và401≠200; focused sau sửa đạt rồi chạy regression/build liên quan một lượt. MySQL8.0.45/HTTP/raw WS thật; real context readiness/JPA và fresh startup cleanup đạt. SELECT sau regression: test DB0 Game/0 UAG; cả quizz/quizz_task2_test0 ACTIVE Game/active-user. Evidence `target/task14-regression.log`, JUnit XML, `task14-review-evidence.json`, `task14-mysql-evidence.txt`; reproduction logs `task14-reproduce.log`, `task14-start-auth-reproduce.log`. Chỉ5 production file sửa,157 baseline backend/build/task files còn hash cũ; migration/schema/dependency/enums/engine không đổi.13 UI assets giữ hash: reuse Task13 phần không bị ảnh hưởng và Task12 browser History/Cancel/expired behavior; không chạy lại full suite, frontend build/browser, benchmark hoặc packaged startup không cần thiết.

[Course checklist](course-requirements-checklist.md) đã cập nhật requirement→module→test/evidence từ Overview§19. Topics/Topic2/Instruction/Submission/README môn gốc, Proposal/Technical Design V2 vẫn thiếu; **chưa đối chiếu trực tiếp/không xác nhận đạt môn, experiment chưa đo**. Không blocker kỹ thuật còn biết. Chưa LAN nhiều máy/load dài/outage toàn dịch vụ giữa COMMIT; không crash recovery guarantee. Flow bảo vệ: **Logout trước boundary→401 không side effect; valid Start→Room commit/handoff→Session queue→score commit→receipt/event→UI/History**. **Task14 DONE, dừng review.**

## Task 13 — Bộ test hệ thống

Đầu vào Task11A/11B/12 đã DONE. Bổ sung `GameIsolationIT` hai case: Start hai Room độc lập đồng thời; giữ transaction scoring A sau SQL flush trong khi B vẫn chấm/mở câu/Cancel/replay, rồi A tiếp tục với deadline mới đầy đủ. Case thứ hai rollback/exhaust A thành SERVER_INTERRUPTED nhưng B vẫn ACTIVE và chấm câu tiếp. Kiểm tra score/resources, scope receipt/event/History, UAG/Room cleanup và tách FORBIDDEN dự kiến khỏi GAME_UNAVAILABLE. Dùng latch/ThreadLocal và clock điều khiển trên fixture/MySQL/raw WS thật; không engine thứ hai hoặc sleep ngẫu nhiên để chứng minh race.

`CodebaseStructureTest` kiểm tra package sở hữu và engine thuần; mở rộng MySqlSchemaIT xác nhận đủ 11 Entity/Repository, MySqlSmokeIT xác nhận Spring context chạy với circular references bị cấm. `GameSnapshotAssertions` bổ sung exact fields/nullability/recipient scope vào các test reconnect DECISION/OPEN/CLOSED/SCORING/RESULT/FINISHED và Cancel có thật; giữ đáp án kín trước scoring, Spectator player=null và public result không lộ resource riêng. Frontend test cũ được tăng kiểm tra hai co-winners; report-name tùy chọn giữ evidence Task12 khi chạy Task13. Không đổi code production, schema/dependency/config, gameplay hay canonical communication.

| Nhóm bắt buộc | Test tái sử dụng/bổ sung, đều đạt trong full suite |
|---|---|
| Bảng điểm, Spin/Star, Recovery, streak/effect/elimination/ranking | ScoreEngineTest113, RankingEngineTest6, GameplayRulesSnapshotTest16; expected theo Overview |
| Deadline −1/đúng/+1, processor chậm, thứ tự, stale timer/close lặp, cleanup | SessionQueueTest21 và GameLifecycleIT/GameCommandNetworkIT với clock/latch |
| Auth/permission, author restriction, Host eliminated Cancel | PermissionPolicyTest27, AuthNetworkIT, AuthorizationRepositoryIT, QuizNetworkIT, RoomNetworkIT và GameHistoryCancelIT |
| Start cùng/chéo Room, roster/config snapshot, scoring một lần | GameLifecycleIT18, MySqlSchemaIT12; thêm GameIsolationIT2 cho hai ACTIVE Room |
| Duplicate/retry, Spin commit/draw một lần, retry sau FINISHED | GameCommandNetworkIT11, GameReplayCacheTest4, RoomBoundaryTest5 |
| Reconnect/Replacement, resource không reset, phase/revision/privacy | GameReconnectNetworkIT11, AuthenticatedSocketRegistryTest3 và GameSnapshotAssertions |
| Cancel vs scoring, rollback/terminal/pending cleanup/startup, History | GameHistoryCancelIT12 và GameLifecycleIT; có KILL đúng connection transaction test MySQL thật, không dừng dịch vụ |
| UI/contract/JPA/package/DI | 32 frontend tests Chrome; reuse 9 nhóm browser Task12, 3 structure tests, schema discovery và real HTTP readiness |

```powershell
$env:MAVEN_USER_HOME = Join-Path $PWD '.cache/maven-home'
.\mvnw.cmd -B --no-transfer-progress verify -Pmysql-smoke '-Dspring.flyway.enabled=false' -Ddebug=false
python scripts/build-client.py
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-client.ps1 -Gameplay -UnitOnly -ReportName task13-client-tests.json
# Muốn chạy lại browser flow thật trên Server đang sẵn sàng: bỏ -UnitOnly.
```

**Kết quả cuối: BUILD SUCCESS; 224 unit +92 integration =316 Java tests, failures/errors/skipped=0; frontend build11 modules/13 assets và32 tests Chrome PASS.** Chạy full suite một lượt sau khi test bổ sung ổn; không disable test. MySQL8.0.45 thật: quizz_task2_test cho fixture integration, quizz cho readiness; Flyway tắt vì schema đã chuẩn bị. SELECT sau suite: test DB còn0 Game/0 UAG; cả hai DB còn0 ACTIVE Game/0 active-user. Fixture isolation lỗi ban đầu đã sửa và hai case chạy lại đạt trước full suite; không còn test thất bại. Log ERROR do fault injection/KILL/cleanupPending là đường lỗi chủ động đã assert, không phải lỗi bị bỏ qua.

Evidence `target/task13-full-suite.log`, Surefire/Failsafe XML, `task13-test-summary.json`, `task13-client.log`, `task13-client-tests.json`, `task13-evidence.json` và `task13-mysql-evidence.txt`. Reuse `task12-client-tests.json`/screenshot cho 9 nhóm browser thật gồm History/Cancel, ba Player, Host Spectator, ACK mất/retry, offline/replacement và Room sau End: 162 backend/build/task files giữ hash, 13 static assets khớp JAR Task12, không sửa UI. Đây là evidence được đối chiếu, không ghi là chạy lại 9 flow trong Task13. JAR review đã startup/readiness200/MySQL UP; dừng đúng process review trước package để tránh Windows khóa artifact. Full suite khởi động các Spring context/server thật, gồm fresh-server stale cleanup; không cần startup lại production không đổi.

Không blocker. Chưa kiểm chứng LAN nhiều máy, load dài hoặc outage toàn dịch vụ giữa COMMIT; không cam kết server-crash recovery. Tài liệu môn vẫn chưa có để xác nhận đạt môn. Flow bảo vệ: **authenticated command → ingress/queue theo Session → transaction MySQL/engine → commit → runtime/replay/event → UI snapshot/History**; Room B tiến triển độc lập khi A bị chặn hoặc lỗi. **Task13 DONE, dừng review.**

## Task 12 — Gameplay, Reconnect, Final và History UI

Tái dùng client ES modules/cookie/CSRF/raw WS Task11A và Backend Task11B. `static/client/game-state.js` giữ envelope GAME, guards và ordering; `game.js` hiển thị phase/question/ảnh, score/streak/effects/resources, Spin→optional Star, Answer/ACK/error/retry, leaderboard/elimination/Host Cancel và Final; `history.js` list/detail/pagination và timeline đã lưu. `app.js` nối Start/Game/History/Home/Waiting, dispose countdown khi rời trang/đổi User. Auth/Room giữ nghiệp vụ cũ; chỉ thêm navigation/điểm nối controller. Countdown chỉ hiển thị từ deadline/server time, không đóng/chấm câu. Giữ frame/requestId khi retry; ACK/snapshot cũ không ghi đè revision mới, same revision vẫn nhận event. RECONNECT chờ snapshot/subscribe trước khi mở action; SESSION_REPLACED dùng chính sách explicit reconnect cũ. Kết quả câu vừa chấm vẫn xem được khi DECISION tiếp mở ngay. Spectator player=null, eliminated chỉ quan sát nhưng Host vẫn Cancel. Final lấy hasOfficialWinner/winners của Server; unscored không bị hiển thị như kết quả chấm.

```powershell
python scripts/build-client.py
$env:MAVEN_USER_HOME = Join-Path $PWD '.cache/maven-home'
.\mvnw.cmd -B --no-transfer-progress package -DskipTests '-Dspring.flyway.enabled=false' -Ddebug=false
java -jar target/competitive-quiz-0.0.1-SNAPSHOT.jar --spring.flyway.enabled=false --debug=false
# Sau khi readiness200; Chrome kiểm thử riêng, không dùng profile/tabs người dùng.
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-client.ps1 -Gameplay
# Chỉ unit frontend, không tạo fixture:
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-client.ps1 -Gameplay -UnitOnly
```

**Diff cuối đạt:11 modules/13 assets build,32 unit frontend +9 nhóm flow Chrome154 thật, exit0; Maven package BUILD SUCCESS.** Bốn browser context tách cookie/đúng bốn User, ba Player chơi đủ10 câu với Host Participate; Spin/Star và reconnect sau Star, một ACK Answer thật bị bỏ rồi retry giữ nguyên UUID/frame sau scoring, offline NO_ANSWER/phase advance, OPEN không correctAnswer/ảnh được tải thật, Player thứ ba có điểm112 theo luật. Host Author Spectator với ba Player khác/player=null, reconnect Spin không trả lại tài nguyên, Cancel giữ ACCEPTED_UNSCORED/no Official Winner và Room WAITING. Sáu Wrong thường loại Host ở −1; quan sát, replacement chặn socket cũ, socket mới vẫn Cancel. History đủ10 câu/traces, permission outsider, FINISHED reload/reconnect, Logout và session bị Server thu hồi đưa Game về Login; desktop/mobile390px không overflow. 32 unit gồm15 regression Account/Quiz/Room/transport và17 gameplay guards/order/countdown/render, gồm HARDSHIP/abnormal End/RESULT snapshot. RESULT reconnect riêng và SERVER_INTERRUPTED render có bằng chứng component + Backend Task10/11B; không tuyên bố đã ngắt DB/Server thật trong flow UI này.

Evidence: `target/client-build.json`, `task12-final-client.log`, `task12-client-tests.json`, `task12-package.log`, `task12-verification.json`; đã xem trực tiếp `task12-final.png` và `task12-history-mobile.png`, sửa optional DOM null render. Script Chrome xử lý process đã thoát khi cleanup. JAR cuối PID89116 tại http://127.0.0.1:8080, readiness200/Server UP/MySQL8.0.45 UP;13 packaged assets khớp source,162 backend/migration/build/task files giữ hash. Không đổi canonical communication/schema/dependency/engine hoặc chạy migration; không chạy lại backend tests vì không có backend change/integration mismatch. Đã dọn đúng16 User fixture/four prefix của các lượt test, kiểm tra không outsider/ACTIVE trước transaction và giữ FK checks; fixture User/UAG/ACTIVE còn0 (`task12-fixture-cleanup.sql`, `task12-mysql-evidence.txt`). Script chạy lại tạo fixture riêng, không cần thao tác DB thủ công để kết thúc trận.

Không blocker. Chưa kiểm chứng LAN/load/browser khác Chrome hoặc DB outage UI end-to-end; không cam kết crash recovery/replay qua restart. Flow bảo vệ: **Start → RECONNECT snapshot/subscribe → DECISION Spin/Star ACK → OPEN Answer ACK → Server Result/leaderboard → Final → History/Waiting**, retry giữ frame, countdown không quyết định gameplay. **Task12 DONE, dừng review.**

## Task 11B — History, Cancel và durable cleanup

History list/detail trong `game/controller/GameHistoryController`, `game/service/GameHistoryService` và DTO/query cùng feature: chỉ FINISHED của GameMember, phân trang có giới hạn, standings và timeline Answer/result/streak/effect/elimination. Giữ snapshot khi Quiz sửa/xóa, chỉ trả câu đã mở; ACCEPTED_UNSCORED giữ lựa chọn/thời gian, scoring fields/result null, không cộng elapsed vào tổng. CANCELLED/SERVER_INTERRUPTED có standings, `hasOfficialWinner=false`, winners rỗng.

REST Cancel trong GameController và raw WS CANCEL_GAME dùng cùng GameRuntime/processor/cache User+Game/requestId, fingerprint type/target/null index/payload. GameLifecycle/GameTransactions chốt Cancel theo thứ tự queue, Host Spectator/ELIMINATED vẫn được phép; terminal transaction giải phóng UAG/Room rồi mới runtime/cache/ACK/End/RoomUpdated. Giữ retry cache10 phút. Kế thừa policy3 attempts100/300ms Task8, không framework mới; cleanup thất bại vẫn UNAVAILABLE/cleanupPending, không báo persisted success. Startup cleanup ACTIVE bỏ lại thành SERVER_INTERRUPTED, không khôi phục engine. Phase continuation đã enqueue trước Cancel no-op sau terminal; timestamp kết thúc không trước input/scoring đã commit khi wall clock lùi. Canonical REST/WS và decisions §23 đã cập nhật; gameplay/engine/schema/dependency/frontend giữ nguyên.

```powershell
$env:MAVEN_USER_HOME = Join-Path $PWD '.cache/maven-home'
.\mvnw.cmd -B --no-transfer-progress test '-Dtest=GameCommandParserTest,GameReplayCacheTest,SessionQueueTest,AuthenticatedSocketRegistryTest,SystemWebTest' -Ddebug=false
.\mvnw.cmd -B --no-transfer-progress test-compile failsafe:integration-test failsafe:verify -Pmysql-smoke '-Dit.test=GameHistoryCancelIT,GameLifecycleIT,GameCommandNetworkIT,GameReconnectNetworkIT' '-Dspring.flyway.enabled=false' -Ddebug=false
# Sau thay đổi test fixture, chỉ chạy lại nhóm History/Cancel bị ảnh hưởng.
.\mvnw.cmd -B --no-transfer-progress test-compile failsafe:integration-test failsafe:verify -Pmysql-smoke '-Dit.test=GameHistoryCancelIT' '-Dspring.flyway.enabled=false' -Ddebug=false
.\mvnw.cmd -B --no-transfer-progress package -DskipTests '-Dspring.flyway.enabled=false' -Ddebug=false
java -jar target/competitive-quiz-0.0.1-SNAPSHOT.jar --spring.flyway.enabled=false --debug=false
```

**Đạt:37 unit +52 MySQL integration =89 tests**, failure/error/skip0. GameHistoryCancelIT12 dùng HTTP/cookie/CSRF/raw WS thật: History permission/pagination/privacy, Accepted-Unscored/Spin/Star, Cancel trước Close hoặc sau full scoring, concurrent duplicate và REST↔WS replay sau terminal, Host eliminated, normal co-winners/effect timeline, rollback rồi retry, terminal cleanup/pending, startup rollback và Server mới cleanup stale state. Một case chủ động KILL đúng connection MySQL của transaction test rồi SELECT gặp lỗi kết nối thật; rollback không ACCEPT và connection mới cleanup SERVER_INTERRUPTED. Regression giữ18 lifecycle/race +11 command/retry +11 reconnect/resource tests. Không mock/H2 thay MySQL; không chạy lại nhóm đã đạt sau thay đổi chỉ test fixture.

Evidence: `target/task11b-final-unit.log`, `task11b-final-integration.log` (40 regression), `task11b-final-history-cancel.log` (12 History/Cancel), Surefire/Failsafe XML và `task11b-test-summary.json`; package BUILD SUCCESS tại `task11b-package.log`. JAR cuối PID91780 phục vụ http://127.0.0.1:8080, readiness200/server UP/MySQL8.0.45 UP; anonymous History401, asset frontend200. `task11b-server.out.log`, `task11b-readiness.json`, `task11b-mysql-evidence.txt`: runtime ACTIVE/UAG0, test Game/UAG0. Bảy frozen engine/rules/migration/build/task files và10 frontend assets giữ nguyên hash/content. Không chạy migration.

Không blocker. Chưa kiểm chứng ngắt toàn dịch vụ MySQL giữa COMMIT, LAN/load hoặc History/gameplay UI; không cam kết crash recovery hay replay sau restart. Flow bảo vệ: **auth → Cancel ingress/fingerprint/replay → session queue → terminal transaction/Room/UAG → commit → copied runtime/receipt/ACK/End/RoomUpdated → History đọc durable state**. **Task11B DONE, dừng review.**

## Task 11A — Account, Quiz, Room và Waiting UI

Tái dùng HTML/CSS/vanilla ES modules do Boot phục vụ cùng origin, cookie/CSRF và `/ws` Task3–10. `static/app.js` điều hướng/phiên, `static/client` chia Account/Quiz/Room, validation, REST và transport. Có Register/Login/Logout/Home, Quiz Create/List/Get/Edit/Delete/ảnh theo Owner/PUBLIC/PRIVATE; My Rooms, Draft Create/Edit/Open/Close, Join/Leave/Remove và roster Waiting qua WS. Host chọn PLAYER/SPECTATOR; tác giả chỉ Spectator. Start kiểm tra số Player/số câu phía UI rồi dùng START_GAME thật; GAME_STARTED/ACK điều hướng tất cả thành viên đang kết nối tới trang vào Game, chưa màn trả lời/history.

Room state lấy snapshot Server, revision scope Room không giảm bởi ACK replay. Retry mutation Room giữ requestId/revision/payload; thử mất một ACK thật rồi replay receipt, không tạo member thứ hai. SESSION_REPLACED dừng socket cũ, chỉ kết nối lại khi người dùng bấm. Không lưu password/token ở storage, không render nội dung/đáp án cho non-owner; lỗi/loading/validation và xác nhận Delete/Close có UI. Backend chỉ thêm public GET `/client/*.js` trong SecurityConfiguration và kiểm tra assets trong SystemWebTest; không đổi nghiệp vụ/schema/dependency/transport. REST contract cập nhật allowlist assets; WS contract/message giữ nguyên. Decisions mới §22, README thêm lệnh build/test client.

```powershell
python scripts/build-client.py
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-client.ps1
$env:MAVEN_USER_HOME = Join-Path $PWD '.cache/maven-home'
.\mvnw.cmd -B --no-transfer-progress package '-Dtest=SystemWebTest' '-Dspring.flyway.enabled=false' -Ddebug=false
.\mvnw.cmd -B --no-transfer-progress test-compile failsafe:integration-test failsafe:verify -Pmysql-smoke '-Dit.test=AuthNetworkIT,RoomNetworkIT' '-Dspring.flyway.enabled=false' -Ddebug=false
# Static-only sửa cuối: đóng gói lại assets, không chạy lặp backend tests đã đạt.
.\mvnw.cmd -B --no-transfer-progress package -DskipTests '-Dspring.flyway.enabled=false' -Ddebug=false
java -jar target/competitive-quiz-0.0.1-SNAPSHOT.jar --spring.flyway.enabled=false --debug=false
```

**Đạt:** build8 ES modules/10 assets; Chrome154 native modules **15 unit +22 smoke flow**, bốn incognito browser context tách cookie với bốn User thật trên MySQL8.0.45. Kiểm tra auth persistence/logout/bad Login/register validation, Quiz validation/owner/public/private/List/CRUD/ảnh thật, Host Participate hoặc Author Spectator, Draft/Waiting config, ba Player và bốn roster cập nhật trực tiếp, Leave/Remove/rejoin/participation, mất ACK/retry/mismatch/old revision, replacement/explicit reconnect, N9/N11 bị chặn/N10 Start thật, điều hướng Game entry, desktop/mobile390px không overflow. Không mock API hoặc tuyên bố đã chơi UI end-to-end.

Backend regression theo ảnh hưởng: **SystemWebTest4 +AuthNetworkIT6 +RoomNetworkIT9**, failure/error/skip0, package BUILD SUCCESS. Không chạy lại full suite298 của Task10 vì gameplay/persistence không đổi. Evidence: `target/task11a-client-build.json`, `task11a-client-tests.json`, `task11a-final-client-tests.log`, `task11a-final-package.log`, `task11a-final-package-assets.log`, `task11a-backend-regression.log`, XML Surefire/Failsafe, `task11a-waiting.png`/`task11a-mobile.png` đã xem trực tiếp. JAR cuối PID90236 phục vụ http://127.0.0.1:8080, readiness200/server UP/MySQL UP tại `task11a-readiness.json` và `task11a-final-server.out.log`. Đã dọn đúng bốn prefix fixture của phiên sau FINISHED, giữ FK checks/không migration; owned fixture User/Room/Quiz, Game và UAG còn0 (`task11a-fixture-cleanup.sql`, `task11a-mysql-evidence.txt`). Lệnh smoke chạy lại tạo fixture riêng ghi prefix/IDs trong JSON; không phải seed production.

Không blocker. Chưa kiểm chứng LAN trên thiết bị khác, browser khác Chrome, tải/DB outage; gameplay/history UI ngoài Task11A. Flow bảo vệ: **form → validation/CSRF hoặc WS auth → requestId cố định → Room transaction/commit → ACK +ROOM_UPDATED → chọn revision mới nhất/roster → START_GAME → GAME_STARTED → Game entry**. **Task11A DONE, dừng review.**

## Task 10 — Socket replacement, reconnect và ordering

Đọc đúng Task10/quy tắc chung, status Task9/decisions generation/snapshot và source registry/adapter/processor/projection liên quan; Overview chỉ phần disconnect/reconnect/scoring dùng trong expected. Không business blocker. Tái dùng `/ws`, envelope v1, cookie/Origin, GameLifecycle.snapshot/GameProjection/GameSnapshot, SessionQueue và receipt Task9; không đổi gameplay/schema/dependency/engine hoặc dựng UI.

- AuthenticatedSocketRegistry publish một owner/User theo generation; AUTH_READY thêm generation, SESSION_REPLACED/close4002 cho socket cũ. Shared Waiting/gameplay `/ws` dùng chung ownership. Stale disconnect/logout session cũ không gỡ owner mới; remove so đúng binding. Command cũ chưa bắt đầu processor bị chặn; operation đã bắt đầu được hoàn tất một lần, không gửi ACK cho connection bị thay, retry trên socket mới đọc receipt cũ.
- RECONNECT command/ACK mới trong realtime/message, strict parser/adapter nối GameRuntime.reconnect: auth/generation → GameMember → snapshot committed/subscribe/send trong actor. Payload dùng nguyên business snapshot hiện có, Host Spectator player=null; RESULT đủ trace/effects, OPEN không đáp án/câu tương lai. FINISHED sau TTL đọc DB, không tạo runtime mới. Không reset Answer/score/streak/resources/deadline; offline vẫn NO_ANSWER.
- Canonical WS ghi field/type/required/nullability/error, generation, read fresh không receipt và ordering theo Game/Room revision/eventId. REST chỉ cập nhật mô tả capability/snapshot, không endpoint mới. Decisions mới §21; README/lệnh/config giữ nguyên.

Kiểm tra đạt:32 unit parser/cache/queue/registry; 11 reconnect MySQL/raw WS cases gồm ngắt trước/sau Spin/Star/Answer, lost ACK, Momentum/Recovery/streak, pending generation bị chặn/started transaction vẫn commit, RESULT snapshot trước DECISION, CLOSED/SCORING privacy, offline NO_ANSWER, eliminated/finished/TTL và terminal actor còn bận replay cũ. Giữ nguyên18 lifecycle/race tests. Hai case concurrent Task9 dùng hai caller trên cùng socket hợp lệ, giữ assertions một Answer/replay/FINISHED; Auth/Room tests kiểm tra owner mới/retry qua replacement thay giả định hai socket cùng hoạt động.

```powershell
$env:MAVEN_USER_HOME = Join-Path $PWD '.cache/maven-home'
.\mvnw.cmd -B --no-transfer-progress test '-Dtest=AuthenticatedSocketRegistryTest,GameCommandParserTest,GameReplayCacheTest,SessionQueueTest' -Ddebug=false
.\mvnw.cmd -B --no-transfer-progress test-compile failsafe:integration-test failsafe:verify -Pmysql-smoke '-Dit.test=GameReconnectNetworkIT' '-Dspring.flyway.enabled=false' -Ddebug=false
.\mvnw.cmd -B --no-transfer-progress verify -Pmysql-smoke '-Dspring.flyway.enabled=false' -Ddebug=false
# Tiếp tục packaging/integration sau khi dừng JAR review cũ bị Windows giữ khóa;
# unit đã đạt, chỉ tránh chạy lặp ở bước đóng gói.
.\mvnw.cmd -B --no-transfer-progress package -DskipTests '-Dspring.flyway.enabled=false' -Ddebug=false
.\mvnw.cmd -B --no-transfer-progress failsafe:integration-test failsafe:verify -Pmysql-smoke '-Dspring.flyway.enabled=false' -Ddebug=false
# Chỉ nhóm Room thay đổi cần chạy lại sau khi cập nhật ca ownership cũ.
.\mvnw.cmd -B --no-transfer-progress test-compile failsafe:integration-test failsafe:verify -Pmysql-smoke '-Dit.test=RoomNetworkIT' '-Dspring.flyway.enabled=false' -Ddebug=false
java -jar target/competitive-quiz-0.0.1-SNAPSHOT.jar --spring.flyway.enabled=false
```

**Diff cuối đạt:** 220 unit +78 MySQL integration =**298 tests**, failure/error/skip0 theo XML/target/task10-test-summary.json. `verify` unit đạt rồi repackage bị khóa JAR review cũ trên Windows; dừng đúng PID82984, packaging BUILD SUCCESS. MySQL regression phát hiện một Room test còn giả định hai active socket; cập nhật đúng replacement/replay và chạy lại RoomNetworkIT9 đạt, giữ kết quả các nhóm đã đạt, không lặp full suite. Evidence: target/task10-final-verify.log (220 unit), task10-final-package.log, task10-final-integration.log, task10-room-regression.log, Surefire/Failsafe XML. JAR mới PID77556 tại http://127.0.0.1:8080; readiness HTTP200/server UP/MySQL8.0.45 UP/GAMEPLAY_WS, Spring wiring/JPA validate đạt (target/task10-server.out.log, task10-readiness.json). SQL runtime/test Game/UAG và owned fixture User/Quiz đều0 (target/task10-mysql-evidence.txt). 42 source gameplay/lifecycle test và10 frozen engine/rules/migrations/pom/TASKS giữ hash. Không migration/H2/mock thay DB. **Task10 DONE, dừng review.**

Chưa kiểm chứng/ngoài scope: LAN/load/UI/Cancel/History, cắt MySQL vật lý giữa COMMIT và Server-crash recovery. Flow bảo vệ: **socket mới/auth → publish generation/thay owner → RECONNECT ingress → kiểm tra generation/membership trong queue → snapshot/subscribe/send → events tiếp theo; old queued command bị chặn, started transaction vẫn commit/replay một lần**.

---

## Lịch sử Task 9 — Gameplay WS, replay và commit

Đọc đúng scope Task9/quy tắc chung/status/architecture/replay/phase/Spin–Star và source WS/cache/GameService liên quan. Kế thừa Task8 có evidence đạt; không business blocker, giữ G-01/G-02/gameplay/schema/dependency/Score Engine.

- File chính: realtime/websocket/GameCommandAdapter/GameCommandParser, message/command/GameCommand, common/GameAck/GameWireError, idempotency/GameReplayCache; GameRuntime nối processor/cache với GameLifecycle/GameTransactions/SpinSelector. Handler/Waiting parser/adapter/registry cũ chỉ thêm route/field/fan-out đúng điểm nối, không socket server/envelope mới.
- ANSWER ACK không correctness; Spin/Star atomic resources, draw weighted một lần/capture qua retry, Spin→Star được/Star→Spin và HARDSHIP+Star bị chặn. Auth/membership + fingerprint lookup trước phase/resources, put cache chỉ sau commit. Retain queue/cache tới FINISHED+10m;128/User/Game,8192/Game,65536 toàn Server, không evict sớm; replay không cần subscription admission. Game/Room cache scope riêng.
- Result lưu/đưa đủ streak/time/effect before/after/consume/grant/elimination qua JSON snapshot đã có. Events cá nhân hóa own Player từ copied commit, Spectator null; không lộ lựa chọn/pool người khác. Result→Elimination→Leaderboard→End→RoomUpdated WAITING sau commit. Canonical WS/REST cập nhật field thực tế; System capability đổi GAMEPLAY_WS. Quyết định mới ở §20 decisions; README/lệnh/config không đổi.

Kiểm tra liên quan:145 unit cũ và10 unit mới parser/cache/weighted selector đạt; Task8 lifecycle18 cases giữ nguyên và đã chạy lại đạt. GameCommandNetworkIT có11 cases trên raw WS/cookie/MySQL8.0.45 thật: concurrent same ID/different ID một Answer, fingerprint type/index, User/Game scope, replay close/ELIMINATED/FINISHED/TTL, Spin rollback capture/no ACCEPT trước commit, Answer rollback, Star order/HARDSHIP/permission/client identity giả, queued replay sau logout, strict deadline−1/exact/+1 và admission không chặn cached ACK. Full WS10 câu/3 Player ra143/121/138 theo bảng Overview với BONUS+Star/SAFE/Star-only, đủ Momentum, Leaderboard/End/RoomUpdated; không chấm hai lần. Clock/latches điều khiển race; fault injection sau SQL thật, không H2/mock DB.

```powershell
$env:MAVEN_USER_HOME = Join-Path $PWD '.cache/maven-home'
.\mvnw.cmd -B --no-transfer-progress test '-Dtest=GameReplayCacheTest,GameCommandParserTest,SpinSelectorTest' -Ddebug=false
.\mvnw.cmd -B --no-transfer-progress test-compile failsafe:integration-test failsafe:verify -Pmysql-smoke '-Dit.test=GameCommandNetworkIT,GameLifecycleIT' '-Dspring.flyway.enabled=false' -Ddebug=false
.\mvnw.cmd -B --no-transfer-progress verify -Pmysql-smoke '-Dspring.flyway.enabled=false' -Ddebug=false
java -jar target/competitive-quiz-0.0.1-SNAPSHOT.jar --spring.flyway.enabled=false
```

**Diff cuối đạt:** `verify` BUILD SUCCESS, 1m17s; **216 unit +67 MySQL integration =283 tests**, failure/error/skip0, gồm11 gameplay WS và18 lifecycle/race tests cũ. JAR mới chạy PID82984 tại http://127.0.0.1:8080; readiness HTTP200/server UP/MySQL8.0.45 UP/GAMEPLAY_WS, Spring wiring/JPA validate đạt. SQL cuối: runtime/test Game=0, USER_ACTIVE_GAME=0, fixture User/Quiz còn sót=0. Evidence: target/task9-final-verify.log, Surefire/Failsafe XML, target/task9-server.out.log, target/task9-readiness.json, target/task9-mysql-evidence.txt. 10 frozen files engine/rules/migrations/pom/TASKS giữ hash. Flyway disabled trên schema hiện có; không migration/schema/dependency/gameplay change. Fixture Task8/9 dùng chung helper, không bỏ18 lifecycle/race tests cũ. **Task9 DONE, dừng review.**

**Chưa kiểm chứng/ngoài scope:** UI/Cancel/History/reconnect/socket replacement, LAN/load, cắt MySQL vật lý giữa COMMIT; hiện nhiều gameplay socket/User vẫn được phép theo Task3/5/8, Task10 mới xử lý replacement. Flow bảo vệ: **WS auth/strict parse → ingress queue → auth lại/membership/fingerprint/replay → guards/draw một lần → transaction → commit → runtime/cached ACK → private ACK/result/leaderboard/end → RoomUpdated; retry trả ACK gốc kể cả FINISHED**.

---

## Lịch sử Task 8 — Start, snapshot và lifecycle

Kế thừa Room boundary Task5, pure Score/Ranking Engine Task6, queue/timer Task7 và package-by-feature đã freeze. Đọc đúng scope Task8/quy tắc chung/architecture/phase/start/end và source/contract liên quan; không khảo sát lại repository. G-01/G-02 đã xác nhận từ người dùng/mục8 decisions, không business blocker.

- File chính: game/service/GameTransactions (Start/score toàn câu/terminal transaction), GameLifecycle (phase/retry), GameProjection/CommittedGame (snapshot chỉ sau commit), GameStartupCleanup; game/controller/GameController + DTO request/response. realtime/session/GameOperations/GameRuntime nối Room → processor; SessionQueue thêm defer/stopTimer cho retry; realtime/timer/GameTimerConfiguration nối clock/scheduler. Adapter/parser/registry hiện có mở rộng START_GAME và lifecycle events; security/System readiness/repositories cập nhật đúng điểm nối.
- Start atomic snapshot roster/config/câu/resources/UAG kể cả Host Spectator; Room ACTIVE không sửa roster. Scoring tính state tạm rồi persist cả câu; Answer/effect before+after/elimination/ranking không nửa chừng. Result/Start/runtime chỉ sau commit; replay Room receipt không khởi tạo trận lại. EndReason/Winner đúng G-01/G-02; interrupted không Winner/không đổi ACCEPTED_UNSCORED thành kết quả.
- Retry rollback chắc chắn tối đa3 lần100/300ms, giữ FIFO/nhường worker. Terminal runtime UNAVAILABLE khác durable GAME_END; cleanup hữu hạn, chỉ retire/release sau commit. Nếu DB tiếp tục mất, giữ cleanupPending/UAG, khôi phục MySQL/restart để startup cleanup trước ready. Handoff lỗi sau Start commit có compensation hữu hạn/receipt vẫn giữ. Chi tiết mới: decisions §19; canonical REST/WS đã đồng bộ.

Kiểm tra liên quan: **145 unit tests đạt** (Score119/RoomBoundary5/SessionQueue21); **GameLifecycleIT18 đạt trên MySQL8.0.45 thật**, HTTP/raw WS Server random port. Có hai Start cùng Room/chéo Room dùng chung Spectator, Join chờ Start, score cạnh tranh chỉ áp dụng một lần, Start rollback không event, immutable Quiz snapshot, trận10 câu bằng timer/Server/internal Answer port, đóng sớm, offline NO_ANSWER, final ALL/ONE ưu tiên COMPLETED và co-Winner. Clock/latch chứng minh ingress deadline−1 được chấm dù processor trễ; đúng deadline/+1 bị reject. Fault injection sau SQL flush chứng minh rollback/retry/exhaust/pending cleanup; outbound lỗi không chấm lại; handoff lỗi và startup cleanup có kiểm chứng. Fixture Task8 chỉ ở quizz_task2_test và đã dọn.

```powershell
$env:MAVEN_USER_HOME = Join-Path $PWD '.cache/maven-home'
.\mvnw.cmd -B --no-transfer-progress test '-Dtest=SessionQueueTest,ScoreEngineTest,RankingEngineTest,RoomBoundaryTest' -Ddebug=false
.\mvnw.cmd -B --no-transfer-progress test-compile failsafe:integration-test failsafe:verify -Pmysql-smoke '-Dit.test=GameLifecycleIT' '-Dspring.flyway.enabled=false' -Ddebug=false
.\mvnw.cmd -B --no-transfer-progress verify -Pmysql-smoke '-Dspring.flyway.enabled=false' -Ddebug=false
java -jar target/competitive-quiz-0.0.1-SNAPSHOT.jar --spring.flyway.enabled=false
```

**Diff cuối đạt:** `verify` BUILD SUCCESS, 1m18s; **206 unit +56 MySQL integration =262 tests**, failure/error/skip0; GameLifecycleIT18 và constraints12 đều đạt. JAR mới chạy PID87200 tại http://127.0.0.1:8080, readiness HTTP200/server UP/MySQL8.0.45 UP/gameplay LIFECYCLE_ONLY; Spring wiring/JPA validate/startup cleanup đạt. Log: target/task8-final-verify.log, Surefire/Failsafe XML, target/task8-server.out.log, target/task8-readiness.json, target/task8-mysql-evidence.txt. DB cuối: runtime/test không Game hay occupancy còn sót, fixture Task8=0. Manifest76 file trước sửa,10 frozen engine/config snapshot/migrations/pom/TASKS giữ hash; không schema/dependency/gameplay change. Flyway disabled vì V1/V2 đã tồn tại; MySQL thật, không migration/H2. README giữ nguyên vì lệnh/config không đổi.

**Chưa kiểm chứng/ngoài scope:** full gameplay WS Answer/Spin/Star/Cancel/dedup/reconnect, UI/History API, LAN2 máy/load; không chủ động tắt MySQL service giữa COMMIT. Lỗi DB được tiêm có kiểm soát trong transaction thật và auth/network dùng MySQL thật, không coi injection là đo mất kết nối vật lý. Flow bảo vệ: **Room dedup/lock → Start transaction → commit/receipt → session ingress/queue → phase timer/Answer ACCEPT → close gate → scoring transaction cả câu → commit → runtime snapshot/event → terminal release**.

---

## Lịch sử Task 7 — Queue, ingress và timer

Đã đối chiếu Task 7/quy tắc chung, trạng thái Task 6, architecture/time decisions và source session/timer/game; chỉ đọc gameplay phase/deadline/đóng câu. Không có business blocker. TASKS.md được cập nhật từ bên ngoài trong lúc làm; đã đọc lại phần Task 7, giữ nguyên bản mới.

| File thêm | Vai trò |
|---|---|
| realtime/session/SessionQueue.java, Ingress.java | FIFO/sequence/timestamp theo GameSession, callback và shared worker pool |
| realtime/timer/ServerClock.java, TimerScheduler.java, ScheduledTimerScheduler.java, PhaseTimer.java | Monotonic/epoch clock, scheduler và timer identity/version |
| game/runtime/PhaseWindow.java, QuestionCloseGate.java | Deadline, roster PLAYING gồm disconnected, chốt đóng một lần |
| test/realtime/session/SessionQueueTest.java, test/realtime/timer/ScheduledTimerSchedulerTest.java | 21 cases clock/latch và scheduler thật; handler tối giản |

Ingress là enqueue thành công dưới cùng lock với timestamp/sequence; chờ lock chưa là ingress. Command/timer cùng FIFO, handler xét receivedAt < deadline dù xử lý trễ. Phase mở dùng clock mới tại lúc mở, không lấy mốc command cũ; epoch chỉ hiển thị. Mặc định 4 worker, 512 Session, 64 command chờ + 1 slot timer/Session, batch32 rồi nhường. Retention sau retire 10 phút đã chốt; cleanup chỉ terminal expired/idle, giữ handler retry, chưa cache Task 9. Timer stale/duplicate/cancel không tác động phase hoặc generation mới.

Lệnh đã chạy:

```powershell
$env:MAVEN_USER_HOME = Join-Path $PWD '.cache/maven-home'
.\mvnw.cmd -B --no-transfer-progress test '-Dtest=SessionQueueTest,ScheduledTimerSchedulerTest' -Ddebug=false
.\mvnw.cmd -B --no-transfer-progress test -Ddebug=false
jar --create --file target/task7-compiled-classes.jar -C target/classes .
```

- **21 tests liên quan và 205 tests mặc định đạt**, fail/error/skip 0. Compile 123 production/17 test files; BUILD SUCCESS, 24.497s. Log: target/task7-related-final.log, target/task7-build-unit.log; Surefire XML. JAR lớp riêng đã tạo; không repack JAR Server đang chạy.
- Biên kiểm chứng: deadline 99/100/101 với hạn100 → true/false/false khi timer chưa chạy; pre-deadline Answer trước timer dù handler blocked; timer sai question/phase/token và close lặp no-op; phase mở trễ/epoch jump; FIFO, A chậm/B tiến triển, batch fairness, reserved timer slot, retention/cleanup/ID reuse, shutdown/failure. Player disconnected vẫn chặn đóng sớm khi chưa Answer; đủ valid Answer thì close/scoring callback giả lập đúng một lần. Không dùng sleep ngẫu nhiên.
- Manifest: 48 source/build/contracts cũ giữ hash; TASKS.md đổi từ bên ngoài. Score Engine/schema/dependency/contracts không sửa. Mockito/JDK warnings cũ không làm fail.

**Chưa kiểm chứng:** Start, GameService/scoring transaction/lifecycle, gameplay WS/auth/dedup và runtime Spring wiring. Class mới thuần Java chưa đăng ký bean; không đổi persistence/config/wiring nên không chạy MySQL IT hoặc restart Server. Bộ tests mặc định có SystemWebTest web-only sẵn có, không Failsafe/MySQL. QuestionCloseGate chỉ chốt callback runtime, chưa chứng minh exactly-once DB hoặc full trận. Task 8 sẽ nối sau commit và gọi periodic cleanup; chưa triển khai phần đó. Quyết định mới ở implementation-decisions.md §18.

Flow: **ingress nguyên tử → FIFO/worker → game handler kiểm tra window/gate → timer callback vào cùng ingress → handler bỏ stale và close một lần**. Task 7 DONE; dừng review.

---

## Lịch sử Task 6 — DONE

## Task 6: phạm vi và file thay đổi

Đã đọc TASKS.md/Quy tắc chung, đầy đủ Cách chơi Overview §8 và thứ tự scoring §9, status/decisions/architecture đã freeze, hai contract canonical, source game/enums/snapshot/tests/build. Overview §1–13 khớp nguyên văn bản trích; G-01/G-02 giữ nguyên. Task 2.5 có evidence đạt; baseline Task 5 kiểm tra lại 103 tests pass. Không AGENTS trên đĩa; tiếng Việt theo chat. Proposal, Technical Design V2, Topics, Instruction, Submission vẫn thiếu; không xác nhận đã đọc hoặc đạt môn. Không business blocker mới, không Git repository.

| File thêm/sửa | Hành vi |
|---|---|
| game/engine/ScoreEngine.java | Scoring một Player/câu theo snapshot: normal/Spin/Star, Momentum/Recovery, streak, elimination, thời gian |
| game/engine/PlayerScore.java | State scoring immutable và kiểm tra tính nhất quán; không ORM, identity hoặc resources |
| game/engine/ScoringInput.java | Outcome đã đánh giá, Spin đã chọn, Star flag, elapsed ms; reject ACCEPTED_UNSCORED |
| game/engine/ScoringResult.java | Base/final delta, thời gian, state mới, effect consumed/granted và elimination flags |
| game/engine/RankingEngine.java | Score giảm, time tăng, đồng hạng 1,1,3; output immutable |
| test/game/engine/ScoreEngineTest.java | 113 cases từ bảng luật, Recovery, streak, coexistence/nonstack, elimination, time và validation |
| test/game/engine/RankingEngineTest.java | 6 cases về thứ tự, đồng hạng, eliminated, tính bất biến và dữ liệu không hợp lệ |
| docs/project-status.md, implementation-decisions.md, course-requirements-checklist.md; README.md | Quyết định, evidence, giới hạn và hướng dẫn kiểm tra |

Tổng 115 production Java/15 test files, vẫn 11 Entity/11 Repository. Manifest trước–sau kiểm tra 136 file hiện có: **0 file đổi**, gồm source/test cũ, resources, pom, TASKS, gameplay và hai communication contracts. Không sửa Entity/snapshot/schema/migration/dependency/frontend/auth/Waiting networking. Chưa có game/service để nối engine; không tạo orchestration, transaction, lifecycle hay endpoint/message mới.

## Task 6: quyết định thực tế

Engine nhận GameplayRulesSnapshot immutable hiện có. Constructor hỗ trợ luật version 1 đã freeze và reject snapshot khác luật đó; calculate chỉ đọc input/snapshot, không factory, clock, random hoặc DB. Outcome phải là CORRECT/WRONG/NO_ANSWER do Server xác định khi scoring; ACCEPTED_UNSCORED không được chấm giả. Spin/Star là lựa chọn đã được caller chấp nhận; HARDSHIP + Star bị reject. Phase, quyền, tài nguyên, random/pool và thứ tự chấp nhận action nằm ngoài engine.

Thứ tự tính: bảng điểm → effect đang có → điểm/thời gian → elimination → chỉ Player còn sống mới cập nhật streak và cấp effect cho câu sau. Initial 20; score 0 còn sống, score < 0 bị loại. Recovery xét basePenalty âm, min(base+3,0), tiêu thụ kể cả final 0; Correct/base 0 giữ Recovery. NO_ANSWER reset cả hai streak khi còn sống; nếu câu đó làm Player bị loại, giữ streak đầu câu theo Overview §9: loại và dừng streak. Effect boolean không stack, có thể cùng tồn tại. Tiêu thụ effect cũ rồi cấp lại ở threshold vẫn chỉ áp dụng một lần trong câu hiện tại. Input đã ELIMINATED bị reject, không trả state mới.

CORRECT/WRONG dùng elapsed ms caller đo: 0 <= elapsed < duration. NO_ANSWER nhận elapsed null và cộng full duration từ snapshot. Đây là kiểm tra tính nhất quán của input; chưa chứng minh ingress/deadline Task 7/9. Math.addExact chặn overflow; input/output immutable, lỗi không làm thay đổi state đầu vào. Ranking nhận mọi Player, gồm eliminated; score giảm/time tăng/đồng hạng 1,1,3. User ID chỉ sắp thứ tự hiển thị trong nhóm đồng hạng, không đổi rank hoặc Winner.

Calculator chưa persist, publish hoặc bảo đảm chấm đúng một lần. Task 8 phải xác định outcome từ Answer/Question snapshot, skip eliminated, chấm toàn câu vào state tạm, persist atomic rồi publish. Cancel trước scoring giữ ACCEPTED_UNSCORED; không chuyển thành WRONG/NO_ANSWER để đưa vào engine.

## Task 6: lệnh kiểm tra và evidence

```powershell
$env:MAVEN_USER_HOME = Join-Path $PWD '.cache/maven-home'
# Engine riêng, không cần MySQL:
.\mvnw.cmd -B --no-transfer-progress test '-Dtest=ScoreEngineTest,RankingEngineTest' -Ddebug=false
# Build/JAR và toàn bộ regression trên MySQL đã có schema:
.\mvnw.cmd -B --no-transfer-progress verify -Pmysql-smoke -Dspring.flyway.enabled=false -Ddebug=false
# JAR review:
java -jar target/competitive-quiz-0.0.1-SNAPSHOT.jar --spring.flyway.enabled=false --debug=false
```

| Kiểm tra | Kết quả thật |
|---|---|
| Baseline: target/task6-baseline.log | 103 tests pass, 65 thường + 38 MySQL IT, fail/error/skip 0; 1m03s. Dùng test/failsafe goals để giữ JAR review cũ lúc baseline |
| Engine riêng: target/task6-engine-tests.log | 118 tests pass lúc đầu: 112 Score + 6 Ranking. Sau đó thêm một case WRONG final 0 vẫn đếm Wrong; case này đạt trong full verify cuối |
| Final: target/task6-verify-final.log, Surefire/Failsafe XML | **222 tests pass**: 184 thường (119 engine + 65 cũ), 38 MySQL IT; fail/error/skip 0, BUILD SUCCESS, 1m01s |
| Bảng luật | 13 mode hợp lệ × 3 outcome: 39 cases thường + 39 cases cùng Momentum/Recovery; 13 cases threshold mọi mode; Recovery -1/-4/-7/-22/0; HARDSHIP + Star reject cả 3 outcome |
| Sequence/effect/elimination | 20→16/12/8/4/0 cấp Recovery, Wrong tiếp→-1/eliminated; 5 Correct→70 cấp Momentum, câu tiếp→83; không áp dụng effect mới ngược; coexistence/nonstack; chết ở câu thứ 5 không cấp effect; already-eliminated reject |
| Ranking | Score/time, 1,1,3 và 1,1,3,3,5; all eliminated vẫn có ranking; không mutate input; output immutable; không quyết định Winner |
| Standalone javac 21 | 5 file engine + GameplayRulesSnapshot + 3 enum biên dịch **không framework classpath**; 15 class files tại target/task6-pure-classes |
| Manifest | target/task6-before-manifest.json: 136 file cũ, 0 đổi; source/build/TASKS/gameplay/REST/WS giữ hash |
| MySQL/dump | MySQL 8.0.45, REPEATABLE-READ. target/task6-schema-after.sql normalized khớp Task 5 (bỏ AUTO_INCREMENT counters, normalize CRLF); V1/V2 checksums -1128648242/1908529616, success 1 cả hai schemas |
| Dữ liệu | Test DB User/Quiz/Room/GameSession/UAG đều 0 sau cleanup; runtime giữ 4 User, 1 Quiz/10 câu, 0 Room/0 Game. Không migration hoặc scoring persistence |
| JAR/startup | 8 class files engine trong JAR, gồm nested/synthetic; PID57628, default mysql, localhost:8080. JPA validate/11 repos, readiness 200/MySQL UP, error log rỗng; target/task6-server.log, target/task6-server-error.log và target/task6-server.pid |

Expected gold lấy từ bảng Overview và ví dụ tính tay, không lấy output/table production để sinh expected. Pure tests chứng minh calculator; MySQL IT kiểm tra regression data/auth/network/schema hiện có, **chưa scoring transaction trong trận thật**. Readiness vẫn gameplay NOT_IMPLEMENTED vì chưa có network/lifecycle trận. Cảnh báo JDK/Mockito/deprecation cũ không làm fail kiểm tra; không nâng dependency ngoài phạm vi.

Lệnh standalone đã chạy, có thể chạy lại:

```powershell
$sources = @(Get-ChildItem src/main/java/vn/edu/quiz/game/engine -Filter '*.java' | ForEach-Object FullName) + @(
  'src/main/java/vn/edu/quiz/game/dto/GameplayRulesSnapshot.java',
  'src/main/java/vn/edu/quiz/game/enums/AnswerStatus.java',
  'src/main/java/vn/edu/quiz/game/enums/PlayerState.java',
  'src/main/java/vn/edu/quiz/game/enums/SpinEffect.java')
New-Item -ItemType Directory -Path target/task6-pure-classes -Force | Out-Null
javac --release 21 -d target/task6-pure-classes @sources
```

## Task 6: review và phần còn lại

**Task 6 DONE**, không còn blocker nghiệm thu pure engine. Server review http://127.0.0.1:8080 PID57628 phục vụ API đã có; không scoring endpoint giả. Chưa engine→GameService/DB integration, Start, whole-question transaction, deadline/timer, Spin selection/resources, Cancel/endReason/Winner, game history/reconnect, UI/LAN/load. Những phần này thuộc task sau; không xem calculator đã test là một trận đã hoạt động. Tài liệu môn vẫn chưa đối chiếu trực tiếp, không xác nhận đạt môn.

Luồng cần học để bảo vệ: GameplayRulesSnapshot → ScoringInput → chọn table → effect cũ → score/time → elimination → alive streak/effect mới → ScoringResult; đưa toàn Player score/time vào RankingEngine → competition ranks. Cần giải thích được: Wrong thứ 5 từ 20 đạt 0/cấp Recovery; câu 6 base -4→final -1/eliminated; effect mới ở câu 5 không giảm phạt chính câu 5.

Task 7 chỉ khi được giao mới triển khai ingress/queue/timer/time abstraction. **Dừng review; chưa Task 7, không commit/push.**

---

## Lịch sử Task 5 — DONE

Phần dưới giữevidence lúc bàn giaoTask5; PID61208/những câu chưaScoreEngine là lịch sử, đãđược thaybởi hiệntrạng Task6 trên.

## Task 5: phạm vi và file thay đổi

Đã đọc TASKS.md/Quy tắc chung/phụ lục Overview, status/decisions/architecture frozen và canonical REST/WS; kiểm tra source Room/Quiz/User/Game/auth/realtime, frontend/build/config/MySQL/schema/tests trước sửa. Task2.5 có evidence đạt, Task4 baseline89 tests kiểm tra lại đạt. Không AGENTS trên đĩa; tiếng Việt theo chat. Proposal, Technical Design V2, Topics, Instruction, Submission vẫn thiếu; course checklist chưa đối chiếu trực tiếp, không claim đạt môn. Không Git repository. Không có business blocker mới; giữ G-01/G-02 và gameplay.

| File/owner thay đổi | Thực hiện |
|---|---|
| room/repository/RoomRepository.java | Paging chỉ own JOINED Rooms; touchRevision cho roster-only mutation |
| room/dto/request: RoomConfigRequest, CreateRoomRequest, EditRoomRequest, RoomRevisionRequest | Config/UUID/revision/validation, không client identity |
| room/dto/response: RoomResponse, RoomPreviewResponse, RoomListResponse, RoomError | Typed metadata/config/JOINED roster, không câu/options/correctAnswer/Player state |
| room/service: RoomService, RoomFailure, RoomMutation, RoomChanged | Persistence/Host/state/Author/visibility/capacity/Leave-rejoin; domain event sau commit |
| room/controller: RoomController, RoomRequestDecoder, RoomExceptionHandler | Create/List/Get/by-code/Edit/Open/Close REST, strict feature JSON/error/CSRF |
| realtime/session: RoomBoundary, RoomOperations | Chung boundary REST+WS, admission/replay/fingerprint/commit/failure/retention |
| realtime/idempotency/CommandFingerprint.java | CanonicalSHA256 type/target/index/payload |
| realtime/message/command.WaitingRoomCommand, common.RoomTarget/RoomAck/RoomWireError, event.RoomEvent | Wire COMMAND/ACK/ERROR/RoomUpdated/revocation thật |
| realtime/websocket: WaitingRoomCommandParser, WaitingRoomAdapter; AuthenticatedWebSocketHandler sửa | Join/Leave/Remove/Subscribe trên transport/cookie/Origin hiện có; revalidate sau queue |
| realtime/connection/AuthenticatedSocketRegistry.java sửa | Room subscriptions/authorized broadcast, send serialization/finite bounds, logout binding giữ nguyên |
| auth/security/SecurityConfiguration.java sửa | Allow đúng Room method/path authenticated, giữ CSRF/origin allowlist |
| test/realtime/session/RoomBoundaryTest.java; test/room/controller/RoomNetworkIT.java | 5 pure runtime tests +9 real network/MySQL tests |
| docs/rest-api-contract.md, websocket-contract.md, project-status.md, implementation-decisions.md, course-requirements-checklist.md; README.md | Canonical fields/quyền/state/fingerprint/retry/failure/evidence/run/limitations |

Tổng110 production Java/13 test files, vẫn11 Entity/11 Repository. Room/RoomMember Entity và enum Task2 tái sử dụng, không duplicate User/Account. Không nâng dependency/đổi stack/scaffold/new migration, không GameSession/gameplay implementation, không sửa frontend. TASKS/gameplay-rules/pom/migrations giữ hash; MySQL schema/history unchanged. Runtime vẫn một Server/MySQL database quizz; schema riêng quizz_task2_test phục vụ test.

## Task 5: quyết định và hành vi thực tế

Create atomically tạo DRAFT + Host JOINED; một User lưu nhiều Draft, Host PLAYER/SPECTATOR trước Start. Organizer chọn PUBLIC hoặc ownPRIVATE; tác giả chỉ SPECTATOR, người Join bằng mã không phải Owner Quiz. REST by-code chỉ WAITING preview tối thiểu để lấy roomId, không roster/Quiz. Get/List/WS snapshots chỉ JOINED (DRAFT Host only), không đáp án. Edit kiểm tra toàn roster khi đổi Quiz/participation/maxPlayers, không hạ capacity dưới Player count.

WAITING Join/Leave/Remove qua WS. LEFT/rejoin giữ cùng membershipId và UNIQUE(room,user); Remove không ban. Host không tự Leave/Remove, dùng Close DRAFT/WAITING; CLOSED terminal giữ roster cuối cho members đọc. ACTIVE khóa config/Quiz/participation/Join/Leave/Remove/Close, disconnect không Leave; bảo toàn roster/Host Cancel policy. Không có Start/Cancel fake endpoint, ACTIVE guards kiểm tra bằng fixture rõ ràng. Min3 Player/Quiz10–50 và UAG claim là điều kiện Start Task8; không cấm nhiều Waiting membership hoặc giả vờ đã test Start.

REST/WS side effect cùng RoomBoundary fair lock + PESSIMISTIC_WRITE Room; roster-only/no dirty edit cũng tăng revision một, Join no-op/Subscribe không tăng. RoomService transaction trả sau commit rồi cache receipt/ACK/broadcast trong cùng boundary; callback không mở outer transaction. RoomChanged domain event chuyển wire ở realtime, không Room service→concrete WS/circular DI.

Scope `(User,ROOM,roomId,requestId)`, Create self scope riêng; fingerprint canonical type/fulltarget/indexnull/payload (object key sort/array order giữ). Actor từ Principal, không message userId. Authorization trước replay, replay trước state/revision. Same ID/payload hai sockets chỉ persistence một lần; đổi nội dung cùng key INVALID_REQUEST_ID. Retry Join/Subscribe còn JOINED nhận ACK cũ và current snapshot riêng, không phát mutation/broadcast lần hai. Leave ACK chỉleft; removed sockets chỉ revocation và không nhận roster mới.

Room receipts giữ đến CLOSED+10m, Create10m; cap512scope/1024globalreceipt/65admission-scope/32subscription-socket, check trước side effect. Không evict live receipt; full còn replay/GET nếu admission available. Global cap cụ thể hóa proposal cũ4096/User/Room cho memory bounded. Một DB write attempt; lỗi DB/Tx fence scope tới restart, không retry mù COMMIT. Read/receipt cũ vẫn được phép nếu auth/DB available. Send failure đóng connection, không undo/repeat DB. Gameplay failure3-attempt/ingress/timer/restart cleanup vẫn pending đúng task sau.

## Task 5: kiểm tra và kết quả thật

Các lệnh Maven dùng wrapper/dependency hiện có và config local gitignored đã được người dùng cấp quyền; không ghi credential thật vào source/docs/log. `MAVEN_USER_HOME=./.cache/maven-home`, Flyway disabled để kiểm chứng schema đã có, JPA ddl-auto=validate giữ nguyên.

```powershell
.\mvnw.cmd -B --no-transfer-progress verify -Pmysql-smoke -Dspring.flyway.enabled=false -Ddebug=false
java -jar target/competitive-quiz-0.0.1-SNAPSHOT.jar --spring.flyway.enabled=false --debug=false
# Chạy Task5 tests riêng, không package lại JAR review đang chạy:
.\mvnw.cmd -B --no-transfer-progress test failsafe:integration-test failsafe:verify -Pmysql-smoke -Dtest=RoomBoundaryTest -Dit.test=RoomNetworkIT -Dspring.flyway.enabled=false -Ddebug=false
```

| Check/evidence | Kết quả |
|---|---|
| Baseline Task4: target/task5-baseline.log | BUILD SUCCESS,89 tests (60 thường+29MySQLIT), fail/error/skip0 |
| Full verify cuối: target/task5-verify-final.log | BUILD SUCCESS,103 tests (65 thường+38MySQLIT), fail/error/skip0; thời gian1m01s |
| Sau thêm assertion strict float/duplicate/trailing JSON: target/task5-focused-final.log | BUILD SUCCESS,14 Task5 tests (5pure+9MySQL network), fail/error/skip0;34.441s. Không source production đổi sau full verify |
| RoomNetworkIT9 | Draft/list/get quyền +Create dedup/validation/CSRF; Join/broadcast/Leave/rejoin/Remove/previewprivacy; PRIVATE/Author/participation; last-slot race; duplicate2socket/current snapshot; ACTIVE/Closed guard; forged/unknownfields/edit revision race; real FK rollback/fence; RESTconfig–WSJoin shared boundary/order |
| RoomBoundaryTest5 | Concurrent duplicate/read serialization; fingerprintfullfields/canonical objects/arrayorder; auth-before-replay/content conflict/send fail aftercommit; DBfence noACK/event; finite globalcap stillreplay |
| Existing89 regression tests | Auth/unauth/forged connection/logout/expiry/HostELIM policy; Quiz/media/sample; MySQLFK/history/activeUserSpectator/activeRoom constraints vẫn đạt |
| MySQL trực tiếp +normalized schema dump | MySQL8.0.45, REPEATABLE-READ; target/task5-schema-after.sql khớp Task4 dump sau bỏ AUTO_INCREMENT counters/normalizeCRLF. V1/V2 sourcehash/historychecksums giữ nguyên; success1 ở cả2schemas |
| DB fixture cleanup | quizz_task2_test User/Quiz/Room/GameSession/UAG đều0 sau test; runtime vẫn4sampleaccounts/1Quiz10câu và0Room, không tạo trận hoặc sửa user data khi smoke |
| JAR thật: target/task5-server.log, task5-server-error.log, task5-server.pid | PID61208, defaultmysql,11repos/JPAvalidate/startup thành công, http://127.0.0.1:8080 readiness200/MySQLUP; Login200→RoomList200(empty)→Logout204. Errorlog empty |

Build/test và MySQL connection đều có bằng chứng riêng, không H2/mock thay MySQL. Network IT dùng Java HttpClient/cookie/WS thật trên localhost; đây là browser auth transport simulation, không manual UI/LAN experiment. Task5 write-failure integration tiêm FK violation trong MySQL transaction: Room update rollback, không ACK/event, scope fenced; không claim đã chủ động cắt kết nối ở giữa COMMIT.

## Task 5: review và phần chưa kiểm chứng

**Task5 DONE**, không còn blocker nghiệm thu Waiting Room. Server review http://127.0.0.1:8080, PID61208; tài khoản demo từ Task4 giữ nguyên, chưa UI Room. Chưa LAN2 máy, slow reader/flood/stress/load, Start cùng/chéo Room/roster Game snapshot, timer/deadline, full phase Game reconnect, HostELIM Cancel lifecycle, DB outage/ambiguous COMMIT thật hoặc startup cleanup. Những phần đó thuộc Task7–10/11B/13, không tạo full engine để test quyền ở Task5. Tài liệu môn chưa đối chiếu trực tiếp, không xác nhận đạt môn.

Luồng cần học để bảo vệ: browser cookie+Origin → AuthenticatedWebSocketHandler revalidate → WaitingRoomAdapter strict parse/Principal → RoomOperations authorize/fingerprint → RoomBoundary serialized scope/cache → RoomService transaction khóa Room/Quiz, đổi membership và revision → DB commit → receipt/private ACK → RoomChanged → registry broadcast chỉ member hợp lệ. Retry đi cache không vào mutation; Leave/rejoin dùng cùng row; rollback không có event.

Task6 chỉ khi được giao: Score Engine thuần Java/input-output theo bảng Overview, không sửa Waiting transport. **Dừng để review.**

---

## Lịch sử Task 4 — DONE

Phần dưới giữ evidence lúc bàn giao Task4; PID78388/những câu chưaRoomAPI là lịch sử, đã được thay bởi hiện trạng Task5 ở trên.

## Task 4: kết quả và file đổi

Đã đọc TASKS.md/Overview/Quy tắc chung, status/decisions/frozen architecture, canonical REST/WS; khảo sát entity/repository/schema/build/auth/frontend hiện có trước sửa. Task2.5 đạt theo evidence đã ghi và baseline80 tests Task3 kiểm tra lại. Không AGENTS trên đĩa; dùng tiếng Việt từ chat. Proposal, Technical Design V2, Topics, Instruction, Submission vẫn thiếu; course checklist chưa đối chiếu trực tiếp, không claim đạt môn. Không Git repository. Không business blocker mới; G-01/G-02 và gameplay giữ nguyên.

| File / owner | Thay đổi |
|---|---|
| quiz/entity/Question.java | JsonIgnore correctOption phòng serialize nhầm; ORM/schema không đổi |
| quiz/repository/QuizRepository.java, QuestionRepository.java | Visible paging, parent lock/revision touch, active count/max allocator, native snapshot media permission projection |
| quiz/dto/request: CreateQuizRequest/EditQuizRequest/QuestionRequest | Validated aggregate input,4 enum options/1 correctAnswer, nullable hashref, revision |
| quiz/dto/response: QuizMetadataResponse/QuizOwnerResponse/QuestionResponse/QuizListResponse/ImageResponse/QuizError | Owner/view mapper riêng, public không câu/options/đáp án/ảnh |
| quiz/service: QuizService/QuizAccessPolicy/QuizImageStore/QuizFailure/SampleDataSeeder | CRUD transactions/permissions/history preservation, immutable PNG storage, Author participation guard, opt-in seed |
| quiz/controller: QuizController/QuizExceptionHandler/QuizUploadExceptionHandler | REST5 CRUD routes +2 image routes, error sanitize, multipart413 trước MVC handler |
| auth/security/SecurityConfiguration.java | Allow đúng method/path Quiz authenticated; giữ session/CSRF/CORS/WS |
| resources/application.yml; .gitignore; config mẫu | Image root/sample password env/multipart2MiB/3MiB, ignored data/quiz-images; không real credential |
| test/quiz/service/QuizAccessPolicyTest, SampleDataIT; test/quiz/controller/QuizNetworkIT | Policy/serializer +MySQL sample +network/validation/concurrency/media/history |
| docs/rest-api-contract.md | Canonical Quiz/ảnh/validation/Owner/public/revision/errors/side effects và scope thật |
| docs/project-status.md, implementation-decisions.md, database-schema.md, course-requirements-checklist.md; README.md | Freeze/evidence/schema allocator explanation/run/seed/limitations |

Giữ Java21/Boot3.5.16/Security6.5.11/JPA/MySQL8.0.45, một Server/database runtime, static Web Client. Không nâng dependency, migrate hoặc đổi schema/bảng điểm/Recovery/effect/streak/endReason. TASKS.md/gameplay-rules/pom/migrations hash giữ nguyên (manifest0 file đổi). Không endpoint/message giả cho Room/Game/lifecycle; websocket-contract không đổi vì WS behavior không đổi.

## Quyết định đã kiểm chứng

Owner do Principal/MySQL User quyết định, không body userId/ownerUserId. PUBLIC non-owner/list chỉ metadata; PRIVATE Owner only selection/read/manage. PUT full replacement dùng lockQuiz + revision; query-only edit vẫn tăng aggregate version. Câu cũ soft-retire, câu mới có DB order_index chưa dùng để không vi phạm UNIQUE dù lịch sử giữ source IDs. Owner view position1..N; GameQuestion order thuộc snapshot Task8. DELETE soft Quiz/câu, không cascade/blob delete.

Quiz quản lý lưu1–50 câu là giới hạn API biên soạn, không thay yêu cầu trận10–50; Start Task8 checkready/roster/config. QuizAccessPolicy cấm Author PLAYER và cho SPECTATOR; Host vẫn Room authority. Guard selection và guard roster tách đúng Overview: người tham gia qua Room code không cần sở hữu Quiz. Wiring thực vào Room/Start thuộc Task5/8.

Ảnh Owner upload PNG/JPEG<=2MiB, normalizePNG, tối đa4096/cạnh và4MP, immutable sha256 perQuiz; publish không overwrite trênNTFS, GET verifyhash. Owner đọc sau softdelete; non-owner phải GameMember đúngGame/Quiz và snapshotref đã opened, phase khôngDECISION. ThayQuizPRIVATE/delete không thay blob hoặc quyền xem ảnh snapshot đã mở. Quyền Game bằng projection rows thật, chưa engine. Không auto cleanup orphan blobs; nguồn DB và files được giữ. sample-data profile opt-in transactional, không overwrite tài khoản/content/password có sẵn.

## Lệnh kiểm tra và kết quả thật

```powershell
$env:MAVEN_USER_HOME = Join-Path $PWD '.cache/maven-home'
.\mvnw.cmd -B --no-transfer-progress verify -Pmysql-smoke -Dspring.flyway.enabled=false -Ddebug=false
$env:QUIZ_DEMO_PASSWORD = 'DemoQuiz2026!'
java -jar target/competitive-quiz-0.0.1-SNAPSHOT.jar --spring.profiles.active=mysql,sample-data --spring.flyway.enabled=false --debug=false
```

| Check | Kết quả |
|---|---|
| Baseline Task3 | 80 tests PASS, target/task4-baseline.log; hiện thực Task2.5/3 sẵn có được giữ |
| Final verify/package | **BUILD SUCCESS;89 tests PASS; fail/error/skip0**.60 Surefire (57 cũ +3 quiz policy/serializer),29 Failsafe (23 cũ +5 QuizNetworkIT +1 SampleDataIT) |
| Auth/scope/validation | PUBLIC metadata không câu/ảnh/correctAnswer; PRIVATE non-owner404, quản lý non-owner403; login/CSRF/active identity giữ. Invalid options3/5/unknown key, blank, incorrect enum/multiple answer/title/image/page/size/count400; không tin ownerUserId |
| Edit/revision/MySQL lock | Hai PUT cùngrev cho đúng một200/một409; question-only edit +1; stale DELETE409; giữ oldQuestion content/id vàretire timestamp, activecount đúng |
| Image network/filesystem | Upload/read/trùng hash trả201/200; non-owner đọc PUBLIC/upload bị403, SVG400, vượt2MiB413 với application code. Gán ảnh của Quiz khác trả400 và rollback. Ảnh khác có hash khác; ảnh cũ giữ nguyên bytes sau sửa/xóa |
| Snapshot/history fixture | FK game_question.source_question_id vẫn tồn tại; content/correct/image không đổi sau sửa/xóa Quiz. GameMember đọc ảnh đã mở200; outsider/câu tương lai DECISION403; kiểm tra đúng Game/Quiz. Fixture tổng hợp, không chạy Game Engine |
| Author/sample tests | Cấm Author PLAYER, cho Author SPECTATOR; seed2 lần trong fixture rollback không trùng dữ liệu.4 tài khoản khác ID, Author sở hữu Quiz10 câu; kiểm tra BCrypt |
| MySQL thực | MySQL8.0.45,11 repos/JPAvalidate, fixtureDB quizz_task2_test; noH2/mock/skip |
| Schema/source freeze | Normalized DDL khớp Task3 (bỏ AUTO_INCREMENT counter/CRLF/trailing newline), V1/V2 source/history không đổi: -1128648242 /1908529616, success1 haiDB; no migration |
| Fixture cleanup | TestDB0 User/0 Quiz; rollback hoặc DELETE đúng ID/namefixture. Runtime sample chủ động có4 User/1 Quiz/10 Question |
| JAR/default deployment | Instance PID78388 tại127.0.0.1:8080, profilemysql,sample-data; JPAinit/ready200/MySQL UP |
| Runtime demo smoke | Login demo_author(ID1), demo_player1/2/3(ID2/3/4) PASS; QuizID1 PUBLIC10 câu. Author có questions/correctAnswer; ba Player chỉ metadata. Logout mỗi session |
| Architecture | Source mới thuộc quiz; không global-layer hoặc entity/repo trùng. Auth không phụ thuộc quiz/realtime, game không phụ thuộc concrete WS; không vòng DI hoặc allow-circular-references |

Các lỗi đã sửa trước kết quả cuối: JAR review Task3 PID41080 khóa repackage trên Windows; Stop-Process tool lỗi nên xác minh command line rồi taskkill đúng PID đó. Sửa một khai báo var ghép trong test. CreateQuiz phải dùng entity do saveAndFlush trả về vì @Version=0 dẫn đến merge tạo managed copy. Multipart vượt giới hạn xảy ra trước MVC handler nên advice riêng trả413/code đã ghi trong contract. Không bỏ hoặc giảm assertion để báo pass. target/task4-verify.log ghi4 fail/1 error; task4-targeted.log còn1 lỗi multipart; target/task4-final-verify.log là kết quả89 đạt.

Evidence: target/task4-before-manifest.json, task4-baseline.log, task4-final-verify.log, target/surefire-reports/failsafe-reports, target/task4-schema-after.sql, target/server-task4.log/server-task4-error.log/server-task4.pid, ảnh fixture target/task4-test-images. Credential MySQL local tái sử dụng theo sự cho phép của người dùng, không in/commit/đưa vào JAR.

## Review và phần chưa kiểm chứng

**Task4 DONE** theo nghiệm thu CRUD/visibility/validation/ảnh/lịch sử/sample. Server review **http://127.0.0.1:8080**, PID78388; tài khoản demo_author và demo_player1..3, mật khẩu mẫu **DemoQuiz2026!**. README có lệnh seed và API contract, không cần chuẩn bị thiết kế/config thêm.

Chưa có Quiz/auth UI, kiểm tra trực quan bằng browser, LAN hai máy, trận hoàn chỉnh, tích hợp Author vào Room/Start, DB outage/load hoặc cleanup ảnh không dùng. Fixture Game chỉ chứng minh lịch sử đã lưu và quyền ảnh; không tuyên bố lifecycle hoạt động. Tài liệu môn thiếu nên chưa đối chiếu trực tiếp hoặc xác nhận đạt môn. Không còn blocker Task4.

Luồng cần học để bảo vệ: Session Principal → QuizController validate DTO → QuizService kiểm tra active User/Owner/revision → QuizRepository khóa Quiz → retire/insert generation câu → commit → Owner DTO hoặc public metadata DTO. Nhánh ảnh: decode/normalize → sha256 → publish file bất biến → kiểm tra quyền snapshot trước GET bytes; sửa Quiz không thay GameQuestion/blob.

Task5 khi được giao sẽ tạo Room/Host/membership và Waiting WS dựa trên auth, Quiz guards và transport đã freeze; không tự chạy Task5.

---

## Lịch sử Task 3 — DONE

Phần dưới giữ evidence lúcTask3bàn giao; câu chưaQuiz/Task4 vàPID41080 là lịch sử, đã được thay bởi hiện trạng Task4 trên.

## Task 3: phạm vi và file thay đổi

Đã đọc TASKS.md/Quy tắc chung/Overview, status, decisions/architecture đã freeze, canonical contracts và source/build/config/entities/repos/test thực tế. Không AGENTS trên đĩa; tuân theo tiếng Việt từ chat. Overview §§1–13 và G-01/G-02 không có mâu thuẫn mới. TASKS.md nguyên hash C3AF23A5E052ECB171B55AD2456D34A57E2FCB94E478EDC7BE65352D4091555C. Proposal, Technical Design V2, Topics, Instruction, Submission vẫn thiếu; checklist chưa đối chiếu trực tiếp, không claim đạt môn. Workspace không Git repository.

| File / owner đổi | Hành vi |
|---|---|
| auth/controller: AuthController, AuthExceptionHandler | Register/Login/Logout/CSRF/me, DTO validation và lỗi sanitize |
| auth/dto/request: RegisterRequest, LoginRequest; response: UserResponse, CsrfResponse, AuthError | Không public Entity/hash hoặc tin userId; request toString không lộ credentials |
| auth/service: AuthService, AuthFailure, AuthorizationService | BCrypt/User lookup/register transaction, active identity và guard repository |
| auth/security: SecurityConfiguration sửa; AuthConfiguration, QuizUserDetailsService, AuthPrincipal, AuthErrorWriter, AuthSessionRegistry, AuthSessionRevoked, PermissionPolicy | CSRF, session fixation/context, authoritative Principal, expiry/logout và Room/Game policy |
| user/entity/UserAccount.java | JsonIgnore getter passwordHash; mapping/table/FK không đổi |
| realtime/websocket: WebSocketConfiguration, AuthenticatedHandshakeInterceptor, AuthenticatedWebSocketHandler | Auth /ws101, cookie/Origin/no query, validation lại, chưa nhận command gameplay |
| realtime/connection/AuthenticatedSocketRegistry; message/event/AuthReady | Session-bound socket revoke và private event xác thực |
| test/auth/security/PermissionPolicyTest | 27 context tests, không Game Engine |
| test/auth/controller/AuthNetworkIT | 6 network/MySQL tests, cleanup user fixture đã tạo |
| test/auth/service/AuthorizationRepositoryIT | 3 guard/real repository tests, synthetic state/Principal, transaction rollback |
| docs/rest-api-contract.md, websocket-contract.md | Canonical request/response/errors/browser/cookie/expiry/logout; chỉ message/API đã code |
| docs/project-status.md, implementation-decisions.md; README.md; course-requirements-checklist.md | Evidence, freeze và hướng dẫn hiện tại/giới hạn |

Giữ Java21/Boot3.5.16/Security6.5.11/BOM/Web Client static, raw WS không STOMP, single localhost/LAN Server/DB. Không nâng dependency, schema/migration/config/build/resources/scripts/TASKS/gameplay đổi (manifest trước–sau0 file freeze đổi). Không Auth UI/Quiz API/Room API/scoring/lifecycle/queue/reconnect gameplay. User entity/repo ở user, không Account/entity/repository thứ hai hoặc package global.

## Quyết định và quyền

Session cookie JSESSIONID HttpOnly/Lax, CSRF REST, BCrypt10, server Principal, raw /ws mandatory Origin được **FROZEN** theo code/evidence Task3; browser truyền cookie tự động, không JWT/localStorage/token URL. Register không auto-login; Login đổi session ID và token CSRF, phải GET csrf mới; login khi đã authenticated409 để tránh đổi identity trên socket cũ. Logout session hiện tại invalidates và đóng mọi socket của nó4001 LOGGED_OUT. Timeout30 phút HTTP inactivity cấu hình được, WS không gia hạn; listener/sweep1s đóng4001 SESSION_EXPIRED. Sweep auth không phải timer/game ingress Task7.

Host là quyền theo Room.hostUserId. GameMember Spectator và ELIMINATED vẫn VIEW; Host ELIMINATED/Spectator vẫn CANCEL khi ACTIVE; Answer chỉ PLAYER/PLAYING/QUESTION_OPEN, Spin/Star chỉ PLAYER/PLAYING/DECISION. User ngoài Room/Game, non-Host và principal giả bị reject. Policy không kiểm tra deadline/resource/aggregate Start vì chưa có use-case tương ứng. Chi tiết và field/error schema trong hai contract canonical, không duplicate schema ở status.

## Kiểm tra và kết quả thật

Chạy bằng Maven wrapper/cache hiện có với credential local đã được người dùng cho phép; không ghi mật khẩu trong test/docs/log. Không H2/mock database.

```powershell
$env:MAVEN_USER_HOME = Join-Path $PWD '.cache/maven-home'
.\mvnw.cmd -B --no-transfer-progress verify -Pmysql-smoke -Dspring.flyway.enabled=false -Ddebug=false
java -jar target/competitive-quiz-0.0.1-SNAPSHOT.jar --spring.flyway.enabled=false --debug=false
```

| Check | Kết quả |
|---|---|
| Baseline trước sửa | 44 tests PASS; log target/task3-baseline.log; evidence Task2.5 đạt trước thêm feature |
| Compile bước đầu | Có2 lỗi Java đã sửa: TextWebSocketHandler binary override không throws Exception nên dùng AbstractWebSocketHandler, và import ThrowingCallable test; build cuối không lỗi |
| Final verify / package | **BUILD SUCCESS,80 tests, fail/error/skip0**;57 Surefire (27 policy +30 existing),23 Failsafe (6 auth network +3 guard repository +14 existing) |
| MySQL thực | MySQL8.0.45; smokeDB quizz, fixtureDB quizz_task2_test; JPA11 repositories validated, không migration mới |
| Register/Login REST | Hash BCrypt10 matches, duplicate case409, DTO/UTF8 limit400, invalid/unknown/deleted401, CSRF403, register không login, sessionID đổi, session/CSRF cũ bị reject, response không password/hash |
| Browser auth transport simulation | Java HttpClient CookieManager truyền cookie vào handshake thật101; AUTH_READY đúng User; thiếu/ngoài Origin403, queryuserId400; unauth/fakecookie/Basic401 |
| Logout / expiry | Hai socket cùng session đóng4001 LOGGED_OUT; cookie replay và reconnect bị401; idle5s cấu hình test đóng4001 SESSION_EXPIRED trong cửa sổ9s, không sleep/mock timer hoặc DB |
| Client giả userId | Login body userId bỏ qua, identity vẫn DB User; frame ANSWER/userId giả đóng1008 UNSUPPORTED_MESSAGE; không parser/game action được tạo |
| Policy27 tests | Matrix Room/Game, outsider/nonHost/phase/finished/Spectator/ELIMINATED; Host ELIMINATED vẫn Cancel |
| Guard repository3 tests | Fixture MySQL chứng minh authoritative membership/Host/elimination/LEFT/deleted; Principal tổng hợp chỉ để test adapter, không bằng chứng network login/lifecycle |
| Fixture cleanup / schema | quizz và quizz_task2_test đều0 User sau test; rollback hoặc DELETE đúng user test. DDL normalized khớp Task2.5; Flyway checksum V1=-1128648242,V2=1908529616, success1 cả hai; source migrations hash không đổi |
| JAR runtime | PID41080, default mysql,127.0.0.1:8080; startup/JPA validate PASS, GET ready200/MySQL UP, GET csrf200, me chưa auth401; gameplay NOT_IMPLEMENTED |
| Source/architecture | Game không import auth/realtime; auth không import concrete realtime; Spring event revocation không vòng DI; không allow-circular-references. Không schema/frontend/dependency đổi |

Verify log cuối: target/task3-final-verify.log; reports target/surefire-reports, target/failsafe-reports. Runtime target/server-task3.log và server-task3-error.log, PID file target/server-task3.pid. Manifest target/task3-before-manifest.json, schema target/task3-schema-after.sql / task3-schema-after-normalized.sql. Schema compare bỏ counter AUTO_INCREMENT, normalize CRLF/trailing newline; không thay DB để so sánh.

Server review đang chạy **http://127.0.0.1:8080**. Password local được giữ ngoài source/JAR. Chạy lại commands trên với MySQL8.0.17+ và quizz/quizz_task2_test đã chuẩn bị theo README; bỏ override Flyway nếu cần áp dụng V1/V2 lần đầu (không repair/drop).

## Giới hạn và nghiệm thu

**Task3 DONE:** auth REST + raw WS handshake/Principal/logout/expiry kiểm chứng qua network thật và MySQL; hash/CSRF/allowlist/identity/permission matrix có evidence đúng mức. Không blocker nghiệp vụ trong task. Chưa visual/manual browser, LAN2 máy, concurrent registration stress, chủ động DB outage, rate-limit/load, User deletion push hoặc auth UI. Không claim đã kiểm chứng gameplay/lifecycle/Start/Cancel/Answer qua một trận thực.

Permission integration thực với queue/lifecycle/gameplay phải bổ sung Task8–10/13; Start aggregate/race/atomic/Quiz-author restriction, deadline/resource thuộc task sở hữu. Context/fixture tests hiện tại không thay integration đó. Tài liệu môn thiếu nên chưa xác nhận yêu cầu môn đạt.

Luồng cần học để bảo vệ: GET csrf → POST login → DaoAuthenticationProvider/UserRepository/BCrypt → SessionAuthenticationStrategy đổi ID/CSRF → SecurityContextRepository lưu Principal → /ws cookie + Origin → HandshakeInterceptor → AuthReady; POST logout → AuthSessionRevoked → AuthenticatedSocketRegistry đóng toàn bộ socket cùng session. Sau đó đọc PermissionPolicy để giải thích vì sao Host ELIMINATED vẫn Cancel nhưng mất Answer/Spin/Star.

Task4 nếu được giao sẽ dùng nền auth/architecture/contract đã freeze để làm Quiz CRUD và visibility. **Hiện dừng review, không tự chạy Task4.**

---

## Lịch sử Task 2.5 — DONE

Phần dưới giữ evidence lúc bàn giao Task2.5; các câu chưa Task3/PID72592 là lịch sử, đã được thay bởi hiện trạng Task3 phía trên.

## Task 2.5: khảo sát và mapping thực tế

Nguồn yêu cầu: file người dùng gửi + TASKS.md bản mới có Quy tắc chung/Task2.5; bản TASKS.md người dùng thay có SHA256 C3AF23A5E052ECB171B55AD2456D34A57E2FCB94E478EDC7BE65352D4091555C, được giữ nguyên. Overview §§1–13 vẫn khớp nguyên văn gameplay-rules.md; decisions G-01/G-02 có nguồn trả lời trong chat/mục8, không hỏi lại. Đã đọc source production/test/build/profile/migrations, README, toàn status/decisions/gameplay; contract riêng chưa tồn tại nên tạo từ code thật. Không có AGENTS trên đĩa; tuân theo chỉ dẫn tiếng Việt trong chat. Không có Git repository (git status báo not a git repository), nên không có diff/history Git để xác minh; dùng manifest trước–sau và giữ sửa đổi người dùng.

Trước refactor: vn.edu.quiz.QuizApplication ở root,34 production Java files/5 test files,11 Entity/11 Repository; root persistence chứa IdentityEntity/DomainTypes, feature.persistence trộn entity/repository/snapshot. Main dùng SpringBootApplication scan mặc định từ root; không EntityScan/EnableJpaRepositories/reflection config hoặc JPQL constructor FQCN. Một JPQL query RoomRepository dùng entity name Room. Chưa Score Engine/WS handler/queue/timer/auth service để di chuyển.

| Old location | New owner/location | Kết quả |
|---|---|---|
| auth.persistence.UserAccount/UserRepository | user/entity, user/repository | Giữ UserAccount entity name/table app_user |
| quiz.persistence | quiz/entity, quiz/repository | Quiz/Question và query giữ nguyên |
| room.persistence | room/entity, room/repository | Room/RoomMember; Leave/rejoin và lock query giữ nguyên |
| game.persistence entities/repos | game/entity, game/repository | 6 entity/6 repo game, không tạo bản trùng |
| game.persistence.GameplayRulesSnapshot | game/dto | Snapshot config, giữ nested records/factory/JSON; không tạo engine |
| persistence.DomainTypes | quiz/enums (2), room/enums (3), game/enums (7) | Tách12 enum, bỏ DomainTypes; giữ tên/value/ordinal. Game dùng Option/Participation từ quiz/room |
| persistence.IdentityEntity | common/util | Utility mapping ID dùng10 entity, giữ MappedSuperclass |
| config.SecurityConfiguration / WebAccessProperties | auth/security / common/config | Giữ SecurityFilterChain/CORS/allowlist, không triển khai login |
| system.* | system/controller, system/service, system/dto/response | Tách response SystemStatus từ record nested; DatabaseStatus vào response. Behavior HTTP không đổi |
| 5 test classes | common/config, game/dto, game/repository, system/controller | Package/import cập nhật; assertions/fixtures giữ nguyên |

Mapping đầy đủ từng FQCN và owner/dependency/DTO convention/freeze table: [Codebase Architecture, decisions §13](implementation-decisions.md#13-codebase-architecture--task-25). Sau refactor46 production files/5 test files (thêm enum top-level/tách record),11 Entity/11 Repository,49 compiled classes khi tính nested records; không package rỗng/placeholder, không bản cũ. Body32 production class di chuyển và5 test giữ nguyên sau khi bỏ package/import, trừ extraction SystemStatus đã đối chiếu record fields. Root main giữ nguyên.

## Task 2.5: bằng chứng trước–sau

| Lệnh/kiểm tra | Kết quả thật |
|---|---|
| Baseline: mvnw.cmd -B --no-transfer-progress verify -Pmysql-smoke -Dspring.flyway.enabled=false -Ddebug=false | BUILD SUCCESS,44 tests,0 fail/error/skip;30 unit/config/HTTP +12 MySQL schema +2 smoke; chưa sửa source |
| Sau refactor: cùng lệnh baseline, sau khi làm sạch output class/compiler/report cũ | BUILD SUCCESS,44 tests,0 fail/error/skip; giữ toàn tests, không H2/mock/disable. Repo scan11; Hibernate ddl-auto validate đạt |
| MySQL preflight | MySQL8.0.45; quizz và quizz_task2_test có V1/V2 success1. Credential local được ignore, không in ra |
| Schema dump before/after hai DB | IDENTICAL SHA256 **589ABF84FAFEC1B07AFD0361765131114DA40F324A6EB96453293A35FE4E673B**; loại counter AUTO_INCREMENT vì fixture rollback vẫn tăng counter, không phải schema diff |
| Migration source/history | V1/V2 file hash không đổi, không thêm migration; checksum history V1=-1128648242, V2=1908529616, success1 ở cả hai DB, không có version mới. Flyway tắt qua override trong test/startup Task2.5, không chạy pending migration |
| Source freeze | main Application, pom/Wrapper, profile config, migrations, scripts, frontend, TASKS.md và gameplay-rules hash không đổi; chỉ Java package/import/DTO extraction và docs thay đổi |
| Inventory/reference | Đúng11 Entity/11 Repository, không tên trùng; không import/reference old persistence/config/feature.persistence trong source/config/scripts. JAR49 classes, không class cũ. 12 enum giữ value/order |
| Dependency/DI | Import graph feature không vòng: game → room/quiz/common; user/quiz/room/auth → common; system độc lập. Startup thành công, không circular bean dependency hoặc allow-circular-references |
| java -jar target/competitive-quiz-0.0.1-SNAPSHOT.jar --debug=false --spring.flyway.enabled=false | PASS: MySQL connection, JPA validate/init,11 repo discover, Server127.0.0.1:8080; không Flyway migrate |
| HTTP runtime | ready200/database UP, Cache-Control no-store; SystemStatus keys application/server/database/serverTimeMs/gameplay và DatabaseStatus status/message giữ nguyên |
| curl.exe WS Upgrade /ws với Sec-WebSocket-Version/Key | HTTP403; chưa có handler/handshake101. REST/assets/CORS/403/web-only behavior giữ qua suite |
| Fixture | quizz/quizz_task2_test đều0 User/0 Game sau tests; transaction rollback, không DROP/TRUNCATE/reset DB |

Lệnh đầu có clean chưa chạy test do Maven clean-plugin3.4.1 chưa trong cache và sandbox chặn tải; đây là lỗi tool/network, không phải baseline source fail. Dùng verify đã có cache để chạy baseline. Sau refactor chỉ xóa các output đã resolve trong workspace: target/classes, test-classes, maven-status, surefire/failsafe-reports; giữ baseline báo cáo riêng, schema dump/manifest/logs. Không đổi plugin/version, không bỏ test. Đã dừng đúng review JAR Task2 PID70864 để tránh Windows khóa build; không dừng MySQL hoặc process khác.

Evidence: target/task25-before-manifest.json, target/task25-baseline.log, target/task25-baseline-reports, target/task25-after.log, target/surefire-reports, target/failsafe-reports, target/task25-schema-before.sql / task25-schema-after.sql, target/server-task25.log. Server review hiện tại **http://127.0.0.1:8080**, PID **72592** lúc bàn giao, chạy Flyway disabled để kiểm chứng schema hiện có.

Docs cập nhật: project-status.md, implementation-decisions.md (Codebase Architecture/mapping/owners/boundary/freeze), [rest-api-contract.md](rest-api-contract.md), [websocket-contract.md](websocket-contract.md), README.md và database-schema.md (link Java path/owner mới, không đổi ERD/schema). Contract System REST ghi đúng code đã có; auth/Quiz/Room/gameplay/WS ghi PLANNED theo TASKS.md mới, không bịa endpoint/message đã implement.

**Nghiệm thu Task2.5 đạt:** refactor thực tế đúng feature; mapping có nguồn; build/tests trước–sau đạt; MySQL startup/JPA discover kiểm chứng; không duplicate/stale class hoặc vòng dependency; schema/migration/gameplay/REST hành vi không đổi. Không có blocker trong Task2.5. Chưa auth/gameplay/WS101/Score Engine/Room service/UI mới; không LAN2 máy/visual browser hoặc chủ động DB outage, không cần để chứng minh package refactor. Course chưa đối chiếu tài liệu gốc.

Luồng cần học để bảo vệ: QuizApplication root scan → auth.security.SecurityConfiguration → system.controller.SystemController → system.service.DatabaseProbe/JdbcDatabaseProbe → SELECT1 MySQL → system.dto.response.SystemStatus/DatabaseStatus → JSON giữ nguyên. Persistence scan root → feature/entity/repository; đổi package không đổi entity name hoặc table.

**Task3 chỉ khi được giao:** triển khai auth/permission theo TASKS.md mới trên architecture đã FROZEN, entity User ở user, auth code ở auth, WS adapter ở realtime khi có; cập nhật canonical contracts cùng code. Không tự chạy Task3 hoặc commit/push.

---

## Lịch sử Task 2 — DONE theo người dùng và evidence Task2

Các kết quả dưới đây giữ trạng thái/bằng chứng lúc Task2 bàn giao; package/link hiện đã cập nhật tới source mới, hiện trạng refactor ở phần Task2.5 trên.

## Task 2: kết quả và file thay đổi

Đọc lại TASKS.md gồm Task 2/phụ lục Overview, gameplay-rules, trạng thái và decisions Task 0/1; tìm lại AGENTS/Overview riêng không có. Tuân theo hướng dẫn tiếng Việt người dùng cung cấp. Proposal, Technical Design V2, Topics, Instruction, Submission vẫn thiếu; không dùng làm điều kiện dừng. Repo có nền Task 1 để tái sử dụng, chưa có entity/migration trước Task 2; database quizz trước migration là rỗng. Không nâng dependency, tạo Git hay sửa tài liệu nguồn.

| Hạng mục | Implementation đã kiểm chứng |
|---|---|
| Schema | 11 domain bảng, **119 cột**, physical USER = app_user; thêm flyway_schema_history. V1 tạo schema, V2 yêu cầu effect snapshot Answer; áp dụng thành công trên quizz và quizz_task2_test |
| Constraint | **18 FK, 14 UNIQUE, 20 CHECK**, 11 domain PK (12 khi tính bảng Flyway), 4 triggers. Answer FK kép cùng GameSession; PlayerSession FK triple chỉ PLAYER; GameQuestion unique(game,order) |
| Active membership | Một ACTIVE Game/Room qua generated UNIQUE; USER_ACTIVE_GAME PK user bao gồm Spectator, FK chỉ Game ACTIVE; finish cần release trước trong transaction |
| JPA/repository | 11 entity/11 repositories feature auth/quiz/room/game; scalar FK, không cascade. Hibernate ddl-auto=validate; @Version Quiz/Room/GameSession/PlayerSession; typed JSON config và enum VARCHAR |
| Leave/Join | JOINED/LEFT; leave/rejoin cập nhật cùng RoomMember id, GameMember participation/role lịch sử bất biến. Permission/phases/REST thuộc Task 5 |
| History/snapshot | Soft delete Quiz/Question; FK RESTRICT. GameQuestion content/options/correct/image/duration, GameSession title/author/N/config snapshot bất biến; source sửa/xóa không cascade history |
| Gameplay snapshot | Factory dữ liệu lưu đủ bảng điểm/trọng số/Star và Momentum/Recovery, hai quyết định G-01/G-02 người dùng đã chốt; 16 test đối chiếu bảng và biên Spin. Không gọi đây là scoring engine |
| Answer | ACCEPTED_UNSCORED có outcome NULL; CANCELLED trước scoring giữ trạng thái này. NO_ANSWER selected_option/received_at_ms NULL; outcome và bốn boolean effect trước/sau bắt buộc khi đã chấm. Tất cả duration/time lưu ms |
| Elimination | Cả has_momentum/has_recovery được lưu; ELIMINATED có time/index, score đóng băng và không phục hồi state. Host role không phụ thuộc player_state |
| Ảnh | Reference tùy chọn sha256:<64 hex>, kiểm tra định dạng và snapshot ổn định; upload/storage/serve/retention file thực tế ở Task 4 |
| Tài liệu | [ERD và dictionary đầy đủ](database-schema.md), [quyết định §12](implementation-decisions.md), [README](../README.md), course checklist cập nhật theo bằng chứng thật |

File thêm: [V1](../src/main/resources/db/migration/V1__quiz_domain.sql), [V2](../src/main/resources/db/migration/V2__require_answer_effect_snapshots.sql), 11 entity + 11 repository tại src/main/java/vn/edu/quiz/{auth,quiz,room,game}/persistence; [IdentityEntity](../src/main/java/vn/edu/quiz/common/util/IdentityEntity.java), [DomainTypes (đã tách enum ở Task2.5)](implementation-decisions.md#mapping-class-production), [GameplayRulesSnapshot](../src/main/java/vn/edu/quiz/game/dto/GameplayRulesSnapshot.java), [MySqlSchemaIT](../src/test/java/vn/edu/quiz/game/repository/MySqlSchemaIT.java), [GameplayRulesSnapshotTest](../src/test/java/vn/edu/quiz/game/dto/GameplayRulesSnapshotTest.java), [prepare-test-database.sql](../scripts/prepare-test-database.sql), database-schema.md. File sửa: [application.yml](../src/main/resources/application.yml) bật Flyway/validate/clean disabled, README.md và ba tài liệu status/decisions/course checklist. pom/dependency/frontend/config credential giữ nguyên.

## Task 2: lệnh và kết quả thực tế

| Kiểm tra / lệnh | Kết quả cuối |
|---|---|
| mysql.exe --protocol=TCP --host=127.0.0.1 --port=3306 ...; SELECT VERSION(),DATABASE() | **MySQL 8.0.45 / quizz**. Credential lấy từ file local được ignore, không in password |
| Chạy scripts/prepare-test-database.sql qua mysql CLI | PASS: tạo quizz_task2_test IF NOT EXISTS trên cùng Server, không DROP hoặc dùng database giả |
| mvnw.cmd -B --no-transfer-progress compile -Ddebug=false | BUILD SUCCESS |
| mvnw.cmd -B --no-transfer-progress -Pmysql-smoke verify -Ddebug=false | **BUILD SUCCESS; 44 tests = 30 unit/config/HTTP + 12 real MySQL schema + 2 real MySQL/JPA/HTTP smoke; 0 failure/error/skip** |
| Flyway migrate + chạy lại validate khi test/startup | V1/V2 success=1 trên cả hai schema; version2; chạy lại không migration thừa, checksum hợp lệ |
| Hibernate/JPA | Init EntityManagerFactory với ddl-auto=validate, đủ11 repositories; repo đọc tất cả bảng, JPA ghi User/RoomMember/GameSession/PlayerSession và JSON/revision roundtrip đạt |
| FK / uniqueness / status | Chặn Answer chéo cả hai hướng và duplicate; PLAYER vs Spectator; cross-Room GameMember; PK active-user kể cả Spectator; một active Game/Room; duplicate GameQuestion order/membership; user không thuộc trận |
| History / representation | Đổi/soft-delete source không đổi snapshot; hard delete bị FK chặn; trigger không cho sửa snapshot và elimination. Cancel DB representation giữ Answer chưa chấm; NO_ANSWER receipt NULL/outcome đầy đủ; thiếu effect snapshot bị CHECK chặn |
| Schema metadata | information_schema xác nhận18 FK/14 UNIQUE/20 CHECK/4 triggers; đối chiếu đủ11 bảng/119 tên cột giữa DDL và DB, không FK ON DELETE CASCADE |
| Rollback fixture | Sau suite, quizz và quizz_task2_test đều0 user/0 game; fixture không lưu lại. Không TRUNCATE/DROP/clean/repair |
| java -jar target/competitive-quiz-0.0.1-SNAPSHOT.jar --debug=false | PASS: Flyway version2 validated, Hibernate mapping validated, Server127.0.0.1:8080; GET /api/system/ready HTTP200 database UP |
| Hash/credential packaging | TASKS.md và1.sql SHA256 không đổi; password local không xuất hiện ở shareable source/docs/scripts/config mẫu; file credential không trong JAR |

Evidence generated: target/task2-verify.log, target/surefire-reports, target/failsafe-reports, target/task2-schema-columns.txt, target/server-task2.log. Server review hiện tại **http://127.0.0.1:8080**, PID **70864** tại bàn giao. Đã dừng đúng Server Task1 PID42200 để giải phóng JAR bị Windows khóa rồi khởi động bản mới; không dừng MySQL.

Lần build đầu fail vì Server Task1 khóa JAR. Hai lần schema suite đầu có fixture/kỳ vọng lỗi chưa tách rõ: đổi identity PLAYER bị trigger1644 trước CHECK; fixture cross-Room ban đầu trùng UNIQUE1062 trước FK. Đã tách INSERT vi phạm CHECK và dùng User mới để test FK; giữ test reject theo lỗi SQL thật, không bỏ constraint hay skip test. Sau chỉnh fixture, toàn suite đạt như bảng trên. V1 đã áp dụng được giữ nguyên; bổ sung effect constraint bằng V2, không sửa checksum hay repair.

Nghiệm thu Task2: schema tạo được; Answer không chéo trận/trùng; PlayerSession đúng participation; history độc lập nội dung Quiz đang sửa; ERD/dictionary khớp DDL/DB; build và MySQL constraint tests đạt. Không còn vướng môi trường/nghiệp vụ trong phạm vi Task2. Race Start thực tế, aggregate đủ ≥3 Player/đủN câu, auth/permission, scoring/duration deadline, Cancel command ordering, image file và history API chưa triển khai/kiểm chứng, thuộc task sau. Browser visual/LAN hai máy vẫn chưa kiểm chứng như Task1. Môn học chưa đối chiếu tài liệu gốc, không xác nhận đạt môn.

Luồng code cần học để bảo vệ Task2: MySqlSchemaIT.answerCannotCrossEitherGameBoundaryOrDuplicate → INSERT Answer → hai FK (game,player)/(game,question) + UNIQUE(player,question) ở V1 → MySQL từ chối1452/1062 → repository chỉ đọc dữ liệu hợp lệ. Khi chấm sau này, Answer được chuyển ACCEPTED_UNSCORED sang outcome đầy đủ trong transaction toàn câu; việc này chưa triển khai ở Task2.

**Task3 chỉ khi được giao:** triển khai Register/Login/Logout, BCrypt, authentication context REST/raw WS và permission theo User/Room/GameMember/PlayerState/phase trên schema này; giữ Host eliminated được Cancel.

---

## Lịch sử Task 1 (05/10/2026)

Các kết quả dưới đây phản ánh Task1 khi chưa có domain schema; hiện trạng Task2 ở trên.

## Task 1: hiện trạng sau triển khai

| Hạng mục | Implementation / bằng chứng |
|---|---|
| Build | Java 21, Spring Boot **3.5.16**, một Maven module; Wrapper plugin **3.3.4**, Maven **3.9.12**, checksum distribution đã pin |
| Dependency thực tế | Spring MVC **6.2.19**, Spring Security **6.5.11**, Hibernate **6.6.53.Final**, MySQL Connector/J **9.7.0**, Flyway core/mysql **11.7.2**, JUnit Jupiter **5.12.2**; lấy từ Boot BOM, không override từng framework |
| Backend | QuizApplication, security/CORS config, SystemController, JdbcDatabaseProbe và startup query check |
| MySQL thật | Người dùng đã cung cấp credential và yêu cầu kết nối. SQL/JDBC xác nhận MySQL **8.0.45**, database **quizz**, SELECT 1 thành công; JPA native SELECT 1 cũng thành công |
| Schema | Database đã tồn tại trước triển khai. Trước/sau kiểm tra vẫn **0 bảng**. `ddl-auto=validate`, SQL init never, Flyway disabled; không tạo domain schema hay Flyway history ở Task 1 |
| Web Client | index.html/styles.css/app.js do Server phục vụ cùng origin, hiện trạng thái Server/DB và nút kiểm tra lại; ghi rõ gameplay chưa triển khai |
| REST | GET `/api/system/status` trả server/database/time/gameplay; GET `/api/system/ready` chỉ HTTP 200 khi query DB thành công, nếu không HTTP 503; no-store, không lộ JDBC URL/credential/exception |
| Authentication/WS | Có dependency Spring Security/WS, session cookie HttpOnly/SameSite=Lax và CSRF. Chưa Register/Login/Logout hay WS handler/game command; ngoài static/status/ready, request bị từ chối. Không sinh tài khoản/password mặc định để giả auth |
| Local/LAN | Mặc định bind 127.0.0.1:8080, origin cụ thể; override bằng SERVER_ADDRESS/SERVER_PORT/ALLOWED_ORIGINS. Đã thử bind 0.0.0.0 trên cổng 8081 với MySQL và CORS thực tế; chưa thử từ máy LAN thứ hai |
| Config local | File `config/application-local.properties` chứa credential local, được .gitignore; không nằm trong JAR. File `.example` không chứa credential thật. Biến DB_* trong môi trường ghi đè được giá trị local |
| README | Đã tạo [README.md](../README.md) với DB/build/run/test/local/LAN, profile và giới hạn. Proposal, Technical Design V2, Topics, Instruction, Submission vẫn thiếu |

File code/config chính: [pom.xml](../pom.xml), [QuizApplication.java](../src/main/java/vn/edu/quiz/QuizApplication.java), [application.yml](../src/main/resources/application.yml), [application-mysql.yml](../src/main/resources/application-mysql.yml), [SecurityConfiguration.java](../src/main/java/vn/edu/quiz/auth/security/SecurityConfiguration.java), [SystemController.java](../src/main/java/vn/edu/quiz/system/controller/SystemController.java), [JdbcDatabaseProbe.java](../src/main/java/vn/edu/quiz/system/service/JdbcDatabaseProbe.java), [Web Client](../src/main/resources/static/index.html).

File hỗ trợ: mvnw/mvnw.cmd, .mvn/wrapper/maven-wrapper.properties, .mvn/maven.config, .gitignore/.gitattributes, [config mẫu](../config/application-local.properties.example), [start-local.ps1](../scripts/start-local.ps1), [prepare-database.sql](../scripts/prepare-database.sql). Script DB chỉ tạo database nếu chưa tồn tại, không tạo bảng gameplay; chưa cần chạy vì quizz đã có. Source/task luật gốc và 1.sql được giữ nguyên.

## Task 1: kết quả kiểm tra thực tế

| Lệnh / kiểm tra | Kết quả |
|---|---|
| `mysql.exe ... -D quizz -e 'SELECT VERSION(), DATABASE(); SELECT 1;'` | PASS: 8.0.45, quizz, 1; password không in ra output |
| `mvn ... maven-wrapper-plugin:3.3.4:wrapper -Dmaven=3.9.12 -Dtype=only-script` | PASS: wrapper chính thức, không cần wrapper JAR trong source |
| `mvnw.cmd --version` với cache distribution mới | PASS: Maven 3.9.12 / Java 21.0.9; SHA-256 distribution khớp. Trước khi pin, đối chiếu SHA-512 file tải với digest Maven Central |
| `mvnw.cmd -B --no-transfer-progress -Pmysql-smoke verify -Ddebug=false` | **BUILD SUCCESS**; **16 tests, 0 failures, 0 errors, 0 skipped** (14 cấu hình/HTTP + 2 MySQL/JPA/HTTP thật) |
| `mvnw.cmd ... dependency:tree` | PASS, đã đọc version thực tế; evidence `target/dependency-tree.txt` |
| `java -jar target/competitive-quiz-0.0.1-SNAPSHOT.jar --debug=false` | PASS: JPA initialized, Hikari kết nối MySQL 8.0.45, Server trên 127.0.0.1:8080; readiness 200/database UP |
| Profile web-only trong SystemWebTest | PASS: asset HTTP 200, database NOT_CONFIGURED, readiness 503, không có DataSource bean; không dùng H2/mock DB để báo MySQL pass |
| CORS và giới hạn endpoint | PASS: origin hợp lệ được phép, origin lạ 403; `/ws`/endpoint chức năng chưa triển khai 403, POST status bị CSRF/quyền từ chối |
| Bind LAN thử trên cùng máy | PASS: netstat 0.0.0.0:8081; readiness 200 và CORS allowlist. Đã dừng Server LAN tạm sau thử; chưa chứng minh truyền giữa hai máy LAN |
| Query `information_schema.TABLES` trước/sau | PASS: 0 bảng, không tự chạy migration/schema |
| Kiểm tra JAR/shareable files | PASS: không package credential local; mật khẩu không xuất hiện trong source/docs/file mẫu; .gitignore loại config local/cache/target |
| Browser QA | **Chưa kiểm chứng visual/click**: Browser plugin bootstrap báo dependency không nằm trong trusted code path. Không gọi đây là UI end-to-end pass; asset/HTTP đã kiểm tra bằng test thật |

Lần MySQL smoke đầu thất bại do import cấu hình local đặt ở file nền bị profile-specific config ghi đè. Đã chuyển import vào application-mysql.yml, dùng khóa DB_USER/DB_PASSWORD trong file local và chạy lại; kết quả cuối như trên. Có lần script PowerShell dừng do xử lý warning stderr của Mockito như lỗi native command; đã chạy lại với redirect qua cmd và đối chiếu exit code/report, không sửa expected test để pass. Warning Mockito/JVM chưa ảnh hưởng JDK 21 hiện dùng.

Evidence generated (không đưa vào source): `target/task1-verify.log`, `target/surefire-reports`, `target/failsafe-reports`, `target/dependency-tree.txt`, `target/server-local.log`. Server localhost khởi động phục vụ review tại **http://127.0.0.1:8080** (PID 42200 tại thời điểm bàn giao); nếu đã dừng, chạy theo README.

Luồng code cần học để bảo vệ Task 1: [app.js](../src/main/resources/static/app.js) fetch status → SecurityFilterChain/CORS → SystemController → JdbcDatabaseProbe lấy connection và SELECT 1 → JSON → client hiển thị state. Readiness khác HTTP liveness; profile web-only không thể báo DB ready. `DatabaseStartupCheck` làm startup thất bại nếu query không thành công.

Không có vướng nghiệp vụ Task 1. Phần chưa kiểm chứng: visual/click trình duyệt và mạng giữa hai máy LAN thật; auth/WS gameplay/constraints/scoring thuộc task sau. Môn học vẫn chưa đối chiếu trực tiếp tài liệu gốc; không xác nhận đạt môn. **Task 2 nếu được giao** sẽ tạo/kiểm tra schema/constraints và migration trên MySQL thật theo TASKS.md, không làm trong Task 1.

---

## Lịch sử Task 0 (04/10/2026)

Ngày khảo sát: **04/10/2026, Asia/Bangkok**. Trạng thái khi kết thúc khảo sát: **TASK 0 — DONE (khảo sát và tài liệu)**. Khi đó sản phẩm chưa triển khai; nội dung bên dưới giữ làm bằng chứng khảo sát ban đầu, không mô tả hiện trạng sau Task 1.

Task 0 đạt tiêu chí biết hiện trạng source, đọc nguồn luật, chọn hướng kỹ thuật và chỉ ra các điểm cần xử lý ở task tương ứng. DONE không có nghĩa ứng dụng hoặc yêu cầu môn đã đạt. Danh sách task chính 0–15 giữ nguyên trong [TASKS.md](../TASKS.md); không tạo roadmap khác.

## 1. Hiện trạng repo đã kiểm tra

Lúc bắt đầu, `rg --files --hidden --no-ignore` và liệt kê thư mục ẩn chỉ tìm thấy **TASKS.md (99.308 bytes)**. Đây là project bắt đầu mới. `git status --short --branch` báo không phải Git repository; không có Git history/diff để kiểm tra. Không tự `git init`, commit hoặc push.

Trong lúc thực hiện Task 0 xuất hiện thêm [1.sql](../1.sql) (21 bytes), chứa `create DATABASE quizz`. Đã đọc để cập nhật khảo sát, không tạo/sửa/chạy file này. File chỉ là câu lệnh tạo database, chưa có bảng/entity/migration hay bằng chứng câu lệnh đã chạy thành công. Task 1 dùng tên database dự kiến `quizz` theo file hiện có, thay vì đặt thêm tên khác. SHA-256 khi đọc: `371BE4A353726B1F291B56C8B59E6D016A27D20F53CEB8A0F255FF8C5D8FD9B7`.

| Hạng mục | Kết quả khảo sát thực tế | Mức kiểm chứng |
|---|---|---|
| AGENTS.md | Không có trong workspace hoặc D:\; đã tuân theo hướng dẫn tiếng Việt người dùng gửi trong chat | Đã tìm file; không giả định có file trên đĩa |
| Task/Overview | TASKS.md có đủ Task 0–15; phụ lục bắt đầu dòng 435, tiêu đề Overview dòng 437 | Đã đọc toàn bộ task và Overview |
| Tài liệu trạng thái/quyết định cũ | Chưa có docs/project-status.md và docs/implementation-decisions.md lúc bắt đầu | Tạo mới ở Task 0 |
| Source backend/module | Không có `.java`, `src` hay implementation | Chưa có gì để chạy/reuse |
| Build/dependency | Không có pom.xml, Gradle, Wrapper, lockfile | Chưa build; không có dependency tree hiện hữu |
| Frontend | Không có HTML/CSS/JS, package.json, frontend build hoặc UI | Chưa chạy browser/end-to-end |
| DB/JPA/migration | Có 1.sql xuất hiện trong phiên, chỉ tạo database quizz; chưa có bảng/entity/repository/migration hoặc cấu hình DB project | Không thực thi SQL; chưa xác thực kết nối hoặc database tồn tại |
| Authentication | Không có auth/session/security config | Chưa kiểm chứng login/logout/permission |
| REST/WebSocket | Không có endpoint/handler/contract thực thi | Contract tối thiểu hiện chỉ là tài liệu |
| Game/scoring/timer | Không có engine/runtime/queue/timer | Chưa chứng minh bảng điểm hoạt động bằng code |
| Test/experiment | Không có test/harness/kết quả đo | Không tuyên bố unit/integration/E2E/MySQL test pass |

Tài liệu bổ sung **thiếu**: Proposal, Technical Design V2, Topics, Instruction, Submission, README; không có Overview riêng. Các dẫn chiếu đến chúng trong phụ lục không thay cho đọc file gốc. Thiếu những tài liệu này không chặn khảo sát/scaffold. Yêu cầu môn **chưa đối chiếu trực tiếp**, xem [course-requirements-checklist.md](course-requirements-checklist.md).

## 2. Môi trường thực tế

| Công cụ/thành phần | Kết quả thực tế |
|---|---|
| OS/shell | Windows 11 amd64; PowerShell |
| Java/Javac | 21.0.9, chạy được `java -version`, `javac -version` |
| Maven | 3.9.12, dùng Java 21.0.9, chạy được `mvn -version` |
| Git/rg | Có command trong PATH; thư mục hiện không có .git |
| Node/npm/Gradle/Docker | Không tìm thấy command trong PATH; không kết luận chắc chắn toàn máy chưa cài. Frontend đã chọn không cần Node/npm |
| MySQL CLI | Không có trong PATH, nhưng có `C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe`; `--version` trả 8.0.45 |
| MySQL server binary | `mysqld.exe --version` trả 8.0.45; đây là binary trên đĩa, chưa phải kết quả `SELECT VERSION()` |
| MySQL service/network | `Get-Service` thấy MySQL80 Running; `netstat` thấy PID 6952 LISTENING cổng 3306 và 33060 |
| Hạn chế kiểm tra | `Get-CimInstance Win32_Service` bị Access denied; `Get-NetTCPConnection` không cho kết quả hữu ích trong phiên này, dùng netstat thay thế. Không cần escalation để hoàn tất khảo sát |
| DB project | Có câu lệnh tạo database quizz trong 1.sql; chưa xác minh database/schema thực tế, chưa có credentials/config project; chưa SQL login/query/JPA/MySQL constraint test. Có service/listener không đồng nghĩa ứng dụng kết nối được |

Không đọc file credential bên ngoài project, không đoán password, không tạo/xóa database, không chạy migration hay dùng H2/mock để tuyên bố kiểm chứng MySQL.

## 3. Tài liệu tạo mới và quyết định

| File | Nội dung |
|---|---|
| [project-status.md](project-status.md) | Hiện trạng, kết quả kiểm tra, giới hạn và bàn giao Task 1 |
| [implementation-decisions.md](implementation-decisions.md) | Stack, module, auth REST/WS, envelope, queue/clock, atomic Start, idempotency, snapshots, transaction/failure policy và bảng truy vết |
| [gameplay-rules.md](gameplay-rules.md) | Nguyên văn Overview §1–13, bao gồm toàn bộ luật ngoài mục Cách chơi, bảng điểm/Spin/Star/Recovery và ví dụ |
| [course-requirements-checklist.md](course-requirements-checklist.md) | Checklist theo lời dẫn Overview, đánh dấu chưa đối chiếu tài liệu môn và chưa có bằng chứng sản phẩm |

Chọn Java 21 + Maven + Spring Boot 3.5.x + JPA/MySQL; frontend HTML/CSS/JavaScript ES modules cùng origin; session cookie cho REST/WS; raw WebSocket JSON; Flyway quản lý schema từ task phù hợp. Chọn phiên bản patch/dependency thực tế và ghi lock/pom ở Task 1, chưa resolve/cài trong Task 0. Lý do và tài liệu chính thức đã đọc được dẫn tại implementation-decisions §2.

Hai điểm nguồn để mở đã hỏi và người dùng trả lời trong phiên 04/10/2026:

1. Sau chấm toàn câu, ONE_SURVIVOR/ALL_ELIMINATED ưu tiên trước COMPLETED, kể cả câu cuối.
2. Khi ALL_ELIMINATED, người đồng hạng 1 đều là Winner. Luật ranking score/time/1,1,3 giữ nguyên.

Ghi rõ tại decisions G-01/G-02; không sửa Overview gốc. Không còn câu hỏi nghiệp vụ chặn Task 0/1. Các chi tiết kỹ thuật thuộc task sau đã có hướng xử lý và task sở hữu; chưa triển khai chúng.

## 4. Kiểm tra đã chạy và kết quả

Các kiểm tra ở Task 0 là kiểm tra tài liệu/môi trường; không phải application test.

| Lệnh/kiểm tra | Kết quả |
|---|---|
| `rg --files --hidden --no-ignore` và `Get-ChildItem -Force` | Ban đầu chỉ TASKS.md; agent tạo đúng 4 file docs. Phát hiện thêm 1.sql xuất hiện trong phiên và đọc lại; tổng cuối 6 file, chưa có source/build/config |
| `rg -n '^#{1,4} ' TASKS.md` + đọc UTF-8 từng đoạn | Đọc đủ Task 0–15 và Overview; không cần file Overview ngoài |
| `git status --short --branch` | Không phải Git repository; đây là hiện trạng, không phải build failure |
| `java -version`, `javac -version`, `mvn -version` | Thành công, version như bảng môi trường |
| `Get-Command java,javac,mvn,gradle,node,npm,mysql,docker,git,rg -ErrorAction SilentlyContinue` | Có Java/Javac/Maven/Git/rg; công cụ còn lại không trong PATH |
| `Get-Service` lọc MySQL, `netstat -ano -p tcp` | MySQL80 Running; listener 3306/33060; chưa thử SQL authentication |
| `mysql.exe --version`, `mysqld.exe --version` qua đường dẫn tuyệt đối | Binaries 8.0.45, exit code 0 |
| `Get-CimInstance Win32_Service` | Access denied; đã dùng service/netstat thay thế, không coi truy vấn này thành công |
| SHA-256 TASKS.md trước/sau | Nguồn giữ nguyên: `DDD03F01C6DFEC02A8817FE4B45CE5BBE9D9286DFB66B4EE1611F03A328D5745` |
| So sánh ordinal khối trích với substring Overview §1–13 | PASS: trùng nguyên văn, gồm dấu âm/ký tự tiếng Việt, không chỉ trùng số bảng |
| Kiểm tra các dòng bảng điểm/Recovery và trọng số | PASS: 21 dòng bảng điểm/Recovery khớp literal; 6 trọng số Spin tổng 100%; không phải chạy Score Engine |
| Kiểm tra cấu trúc docs | PASS: đủ 4 file UTF-8, local links tồn tại, code fences đóng, task headings nguồn đủ 0–15 |
| Kiểm tra số file cuối | Lần đầu FAIL do giả định chỉ 5 file, phát hiện 1.sql vừa xuất hiện; đã đọc file, sửa mô tả hiện trạng và kiểm tra lại danh sách 6 file. Không xóa file để làm kiểm tra pass |

Lệnh đọc PowerShell dùng `-Encoding UTF8` vì mặc định shell từng hiển thị sai dấu ở lần đọc đầu; đã đọc lại đúng UTF-8, không sửa encoding/nội dung TASKS.md.

### Lệnh tái lập kiểm tra nguồn và bản trích ngay bây giờ

Chạy từ `D:\BTL_LTM`:

```powershell
$ErrorActionPreference = 'Stop'
$source = [IO.File]::ReadAllText((Join-Path $PWD 'TASKS.md'), [Text.Encoding]::UTF8)
$rules = [IO.File]::ReadAllText((Join-Path $PWD 'docs/gameplay-rules.md'), [Text.Encoding]::UTF8)
$start = $source.IndexOf('## 1. Giới thiệu hệ thống')
$end = $source.IndexOf('## 14. Novelty / Contributions', $start)
$marker = '<!-- BEGIN OVERVIEW EXTRACT -->'
$extractStart = $rules.IndexOf($marker) + $marker.Length + 1
$extractEnd = $rules.IndexOf('<!-- END OVERVIEW EXTRACT -->', $extractStart)
if ($start -lt 0 -or $end -le $start -or $extractEnd -le $extractStart) { throw 'Missing markers' }
if (-not [string]::Equals($source.Substring($start, $end-$start), $rules.Substring($extractStart, $extractEnd-$extractStart), [StringComparison]::Ordinal)) { throw 'Overview extract changed' }
$expectedHash = 'DDD03F01C6DFEC02A8817FE4B45CE5BBE9D9286DFB66B4EE1611F03A328D5745'
if ((Get-FileHash TASKS.md -Algorithm SHA256).Hash -ne $expectedHash) { throw 'Source changed' }
'PASS: exact Overview extract; original TASKS.md unchanged'
```

### Lệnh chuẩn bị cho phần chưa kiểm chứng

Chỉ là hướng dẫn chạy lại, **chưa chạy ở Task 0**. Sau khi Task 1 đã tạo `pom.xml`, Maven Wrapper, cấu hình và sau khi có DB/tài khoản dành riêng cho project:

```powershell
# Đọc SQL, không thay đổi DB; nhập password tại prompt, không ghi password vào lệnh.
# quiz_app là tài khoản dự kiến; quizz lấy từ 1.sql, chưa xác minh đã tồn tại.
& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' --protocol=TCP -h 127.0.0.1 -P 3306 -u quiz_app -p -D quizz -e 'SELECT VERSION(), DATABASE(), CURRENT_USER(); SELECT 1;'

# Chỉ chạy sau scaffold Task 1; build thành công chưa chứng minh MySQL kết nối được.
.\mvnw.cmd -B test
.\mvnw.cmd -B package
.\mvnw.cmd spring-boot:run
```

Task 1 sẽ tự tạo cấu hình mẫu không credential thật và README chỉ rõ biến môi trường cần cung cấp; không yêu cầu người dùng tự viết contract/config. Test MySQL integration ở Task 2+ phải dùng schema test riêng, ghi rõ lệnh profile thực tế sau khi profile được tạo; không bịa một profile đã tồn tại.

## 5. Nghiệm thu và việc còn lại

- Đã xác định repo bắt đầu mới, tài liệu có/thiếu, môi trường và mức chưa kiểm chứng.
- Đã trích luật nguồn, giữ nguyên toàn bảng điểm/Recovery; quyết định bổ sung của người dùng tách khỏi nguồn.
- Đã chọn hướng kỹ thuật tối giản và lập bảng đầy đủ 11 điểm bắt buộc trong decisions §9, cùng test evidence cần có cho task sở hữu.
- Checklist chưa công nhận đạt môn; chưa có kết quả gameplay/MySQL/E2E/experiment.
- Chỉ thay tài liệu; không có code, dependency, schema hay dữ liệu DB bị thay đổi.

**Task 1 khi được giao tiếp:** scaffold mới một Maven/Spring Boot project, frontend static tối thiểu, chọn/pin dependency tương thích Java 21 theo tài liệu chính thức, Maven Wrapper, cấu hình MySQL/local/LAN/origin bằng môi trường và README tái lập. Đọc lại 1.sql và kiểm tra database quizz trước khi chuẩn bị DB, tránh tạo trùng hoặc đổi tên vô cớ. Kiểm tra build và startup riêng, chỉ ghi kết nối MySQL thành công khi có evidence SQL/JPA; chưa triển khai schema/gameplay các Task 2+. Không làm Task 1 trong phiên Task 0 này.

Luồng cần học để bảo vệ: Answer → auth → ingress → queue → dedup → transaction ACCEPTED_UNSCORED → commit → ACK → close/scoring toàn câu → commit → broadcast → UI. Hiện đây là luồng thiết kế trong decisions §10, chưa có đường dẫn class/code để trace.
