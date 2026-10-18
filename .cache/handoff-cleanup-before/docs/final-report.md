# MultiGame — Game thi đấu mạng 7 chế độ — báo cáo kỹ thuật

Ngày 10/10/2026, Asia/Bangkok. Tên code hiện hành MultiGame (`vn.edu.multigame`); evidence thực nghiệm giữ nguyên tên/namespace của phiên bản đã đo, không đo lại chỉ vì refactor. Hồ sơ Task25 **PARTIAL**: kết quả kỹ thuật được ghi theo evidence thật; chưa có Topics/Topic2, Instruction, Submission hoặc mẫu README môn nguyên bản. Chưa đối chiếu tên file/format/kênh/deadline nộp. Không suy ra yêu cầu USB/Drive. Proposal/Technical Design V2 cũng không thấy; prompt task không thay tài liệu môn.

| Nhóm 4 người | Họ tên | MSSV | Lớp |
|---|---|---|---|
| Thành viên 1 | | | |
| Thành viên 2 | | | |
| Thành viên 3 | | | |
| Thành viên 4 | | | |

Phân công dự kiến và bảng contribution thực tế để trống tại [team](team-contributions.md). Evidence cấp project không xác minh tác giả cá nhân.

## Abstract

Ứng dụng Web nhiều người chơi chạy trên một Spring Boot Server và một MySQL, dùng REST/raw WebSocket trên localhost/LAN. Một trận có tối đa bảy màn thuộc bảy chế độ khác nhau, cùng roster/điểm/bảng xếp hạng. Mở rộng nội dung tái sử dụng queue, timer, transaction, replay và reconnect của Quiz cũ thay vì tạo bảy game engine độc lập. Đóng góp mạng cần kiểm chứng là thứ tự ingress, side effect sau commit, trả lại response gốc khi retry và snapshot cá nhân khi nối lại. Full regression Task24 đạt 302 unit và 149 MySQL/REST/WS integration; browser thật đạt 89 component và 7 nhóm flow. Task25 đo lại replay/reconnect bằng baseline test-only trên phiên bản v2 và chạy smoke từ cấu hình mẫu. Các số liệu mới nằm ở mục Results; dữ liệu ngày06/10 thuộc Quiz v1 Decision5s, không đại diện cho v2/Wi-Fi. Chưa có evidence nhiều máy LAN, Compilatio hoặc máy mới.

## 1. Bài toán, phạm vi và nguồn

Nhiều Client gửi thao tác đồng thời; response có thể mất, socket bị thay hoặc người chơi offline khi phase đổi. Server phải giữ một deadline chung, nhận một Answer hợp lệ/người/câu, chấm cả câu atomic và giữ lịch sử đã commit. Browser chỉ gửi input/hiển thị trạng thái, không tự chấm hoặc gia hạn bằng timestamp của nó.

Nguồn luật mới: yêu cầu người dùng và [task mở rộng16–25](../TASKS_MO_RONG_7_CHE_DO.md), [decisions28–35](implementation-decisions.md). [Overview v1](../TASKS.md)/[bản trích](gameplay-rules.md) vẫn là nguồn bảng Quiz/effect và lịch sử cũ. [REST](rest-api-contract.md)/[WS](websocket-contract.md) là contract canonical của code thật. Xác nhận người dùng: giữ streak/Momentum/Recovery qua non-Quiz và chỉ áp dụng trong Quiz; v1 ALL_ELIMINATED/ONE_SURVIVOR ưu tiên COMPLETED, đồng hạng1 đều Winner. V2 không elimination nên kết thúc bình thường COMPLETED.

Giữ HTML/CSS/native ES modules, Java21/Spring Boot3.5.16/JPA/Flyway/MySQL8.0.45, package-by-feature, một Server/DB. Không Cloud/Redis/Kafka/multi-server/failover hoặc crash resume. Các Client LAN không kết nối trực tiếp MySQL.

## 2. Kiến trúc và feature ownership

[README ứng dụng](../README.md) và [hướng dẫn đọc code](codebase-guide.md) có entry point/lệnh chạy. MultigameApplication scan 13 Entity/Repository; JPA validate, Flyway quản lý schema. Auth/user sở hữu danh tính; quiz sở hữu bộ câu/media; room sở hữu kế hoạch/membership; game sở hữu luật/state/transaction/snapshot/History; realtime sở hữu ingress/queue/timer/cache/socket/wire mapping. Engine thuần không import JPA, clock, random hoặc WebSocket; game service không phụ thuộc concrete transport.

~~~mermaid
flowchart LR
    C[Web Clients riêng Account] <-->|REST và raw WS| A[Auth và adapter]
    A --> R[Room boundary trước Game ID]
    R --> Q[Ingress và queue từng Game]
    A --> Q
    T[Timer monotonic] --> Q
    Q --> G[Lifecycle và transaction service]
    G --> E[ScoreEngine v1 / MultimodeScoreEngine v2]
    G --> D[(Một MySQL)]
    G --> M[Local immutable PNG / MP4]
    G -->|Sau commit| V[Runtime / replay / event]
    V --> C
~~~

Trace Answer: cookie session/Origin → parser/generation → ingress timestamp/sequence/enqueue atomic → session processor → fingerprint/replay lookup → Lifecycle kiểm phase/input/deadline → Transactions persist → DB commit → runtime/receipt → ACK không correctness → close/score transaction → RESULT/event → game-state/revision → UI. Retry đã có receipt trả ACK gốc trước phase validation, vẫn kiểm danh tính/membership/mismatch.

~~~mermaid
sequenceDiagram
    participant C as Browser
    participant W as Auth / WS adapter
    participant Q as Game queue / replay
    participant G as Lifecycle / transaction
    participant D as MySQL
    C->>W: ANSWER + UUID + questionIndex + typed input
    W->>Q: Principal + generation + ingress
    alt Committed receipt exists
        Q-->>C: Original ACK
    else New valid input
        Q->>G: Validate / persist Answer
        G->>D: COMMIT accepted input
        D-->>G: Success
        G-->>Q: Committed projection
        Q-->>C: ACK without correctness
    end
    Q->>G: All wait-set answered or current deadline
    G->>G: Pure engine computes whole question
    G->>D: All score/result/state + COMMIT
    G-->>Q: Runtime / cached result after commit
    Q-->>C: QUESTION_RESULT / leaderboard / End
~~~

## 3. Dữ liệu, snapshot và tương thích v1

[Schema/ERD](database-schema.md) và [DDL V1–V7](../src/main/resources/db/migration) mô tả implementation. Có13 bảng: app_user, quiz, question, room, room_member, room_stage, game_session, game_stage, game_member, player_session, game_question, answer, user_active_game. Composite FK chặn Answer player/question khác Game và PlayerSession không GAME_MEMBER PLAYER; UNIQUE game/order và room/user; UAG gồm Host Spectator. Room config generation và frozen GameStage/GameQuestion giữ nguồn/author/content/answers/config/media độc lập source edit/delete.

V1–V3 giữ checksum. V4 thêm versioned typed data/stage/config/correct-only metric; V5 RoomStage; V6 Vua aliases; V7 released-hint cursor. Không chuyển trận cũ sang luật v2. V1 initial20/negative/elimination/totalAnswerTime/endReason giữ nghĩa; v2 initial/floor0/no elimination dùng totalCorrectAnswerTimeMs. Migration IT thật từV3→V7 giữ v1 score−4/eliminated/time1312/ALL_ELIMINATED/rank1; Cancel v1 score20 còn nguyên.

ACCEPTED_UNSCORED giữ input/receivedAt nếu đã nhận, scoredAt/baseDelta/scoreDelta/scoreAfter/result null; NO_ANSWER receivedAt null. Cancel trước close không ép Answer chưa chấm thành sai/NO_ANSWER. Quiz soft-delete không cascade lịch sử. Media SHA immutable local, filesystem không atomic với DB; orphan được giữ. Backup/restore cần DB và kho ảnh/video/drafts cùng bộ.

## 4. Bảy chế độ và luật v2

| Mode | Input và kiểm tra Server | Media / reconnect |
|---|---|---|
| QUIZ | Một option A/B/C/D; đúng một secret correctAnswer | Ảnh optional; resource/Spin/Star/answer đã commit trong snapshot |
| SONG | Text exact normalized alias | MP4 tác giả tự cắt, H264+AAC; reconnect seek theo openedAtMs, không thêm giờ |
| VIETNAMESE_PUZZLE | Permutation stable IDs; nối nguyên văn các mảnh rồi so aliases | Presentation shuffle một lần khi Start; không lộ order/alias trước chấm |
| RIDDLE | Text exact normalized alias | Chỉ nội dung câu; input đã nhận được giữ |
| CLUES | Text exact normalized alias | Server release prefix theo offset chung; reconnect chỉ phần đã commit |
| IMAGE_WORD | Text exact normalized alias | Một hình gợi từ/cụm từ, required immutable PNG |
| ORDERING | Toàn bộ stable IDs đúng exact order | Shuffle snapshot cố định, giữ submitted IDs |

Text NFC, case-insensitive Locale.ROOT, trim/gộp Unicode whitespace; giữ dấu/punctuation, không fuzzy. Bộ Mine gồm own PUBLIC+PRIVATE; Shared gồm mọi PUBLIC kể cả own. Non-owner PUBLIC chỉ metadata, không content/secret. Host có thể chọn own PRIVATE; invited Players chơi snapshot nhưng không đọc bộ riêng. Author bất kỳ bộ trong trận không là Player, có thể Host Spectator.

Room chọn1–7 mode khác nhau, mỗi màn một bộ đúng loại/count/duration, tổng1–50 câu. Roster/plan snapshot cố định. Initial0, score tối thiểu0 sau tính, không eliminated. Chỉ Quiz dùng bảng cộng/trừ/Spin/Star/effects. Spin=floor(tổng số câu Quiz/10), Star1 nếu có Quiz, khôngQuiz cả hai0. Sáu mode khác đúng+10, đúng câu cuối từng màn+20, sai/bỏ0; Quiz cuối không double. Rank scoreDESC rồi correct-only elapsed ServerASC; bằng cả hai đồng hạng competition1,1,3/rank1 co-Winner. CANCELLED/SERVER_INTERRUPTED có standings, không official winner.

### Bảng Quiz/effect được giữ từ Overview

| Action | Correct | Wrong | NO_ANSWER |
|---|---:|---:|---:|
| Normal |10|−4|−1|
| BONUS |15|−4|−1|
| SAFE |8|−2|0|
| BREAKTHROUGH |18|−7|−3|
| SPEED |22|−10|−4|
| DECISIVE |28|−15|−7|
| HARDSHIP |8|−6|−2|
| Star riêng |25|−14|−7|
| BONUS+Star |30|−4|−1|
| SAFE+Star |20|−2|0|
| BREAKTHROUGH+Star |30|−12|−6|
| SPEED+Star |35|−16|−8|
| DECISIVE+Star |40|−22|−10|
| HARDSHIP+Star |Cấm|Cấm|Cấm|

Spin pool ban đầu BONUS25%,SAFE20%,BREAKTHROUGH25%,SPEED15%,DECISIVE10%,HARDSHIP5%, draw không replacement/chuẩn hóa pool còn lại, không random lại retry. Spin→optional Star hợp lệ; Star riêng khóa Spin; một Spin/câu, Star tối đa1/trận. Mỗi5 Correct/Wrong liên tiếp reset streak và cấp Momentum/Recovery cho câu sau; NO_ANSWER reset cả streak. Momentum+3 Correct kế tiếp. Recovery dùng basePenalty<0: min(base+3,0), consume kể cả final0; base0/Correct giữ. Effect coexist/không stack/không apply câu tạo streak. V2 floor có thể làm actual scoreDelta khác ruleDelta; nonQuiz không update/consume/grant Quiz streak/effects.

## 5. Phase, thời gian, concurrency và reconnect

~~~mermaid
stateDiagram-v2
    [*] --> INTRO: Start / đổi màn
    INTRO --> DECISION: Quiz, đủ Player Continue hoặc 10s
    INTRO --> QUESTION_OPEN: nonQuiz, đủ Continue hoặc 10s
    DECISION --> QUESTION_OPEN: 7s
    QUESTION_OPEN --> QUESTION_CLOSED: wait-set đã trả lời hoặc deadline
    QUESTION_CLOSED --> SCORING
    SCORING --> RESULT: whole-question commit
    RESULT --> DECISION: Quiz cùng màn sau 1.5s
    RESULT --> QUESTION_OPEN: nonQuiz cùng màn sau 1.5s
    RESULT --> INTRO: màn tiếp sau 1.5s
    RESULT --> FINISHED: câu cuối / shared final display
    FINISHED --> [*]
~~~

Deadline mở theo thời điểm Server thật mở phase. Monotonic clock cho duration/deadline nội bộ; epoch serverTime/deadline cho UI. Client countdown chỉ hiển thị. receivedAt<deadline hợp lệ, exact/+1 bị reject kể cả timer chưa xử lý. Timer/command cùng atomic ingress; command đã ingress trước close không bị timer vượt qua khi worker chậm. Timer mang session/generation/question/phase/token, stale/lặp no-op. Shared worker pool4, từng Game tuần tự, không một worker toàn hệ thống hoặc thread sống mãi/Game.

V2 wait-set chụp Players online lúc mở câu. Disconnect sau đó vẫn giữ chờ tới deadline; offline khi câu kế mở không gate early close, vẫn có thể reconnect Answer trước hạn. Wait-set rỗng chờ deadline; offline NO_ANSWER vẫn được persist cho roster. Intro chờ mọi roster Player, Spectator không gate; offline chưa Continue thì đủ10s. RESULT chung1.5s, Server tự chuyển, không next button.

Một gameplay socket/User, generation replacement: queued operation chưa bắt đầu của socket cũ bị reject; operation đã bắt đầu hoàn tất một lần. Disconnect cũ không làm mới offline. RECONNECT cùng GameMember trả snapshot ACTIVE/FINISHED, Host Spectator player=null; phase/current input/resources/effect/result/deadline/leaderboard đủ; revision ordering chặn rollback. Replay terminal giữ10 phút, không xóa ngayEnd. Các field thật theo canonical contract.

## 6. Media, bảo mật và DB failure

Ảnh normalized PNG SHA256, video MP4 local20MiB/120s/1080p H264+AAC; Owner staging trướcCreate, access Owner hoặc member/câu đã mở. URL không public static; không lộ secret/future content. SONG muted default/click enable audio/nativeplay fallback; reconnect seek elapsed và không thêm thời gian. Server media404/503 vẫn cho đoán và giữ deadline. CLUES cursor prefix cùng revision/transaction, late hint không reveal sau close hoặc vượt Answer đã ingress.

BCrypt/session HttpOnly/REST CSRF, WS cookie và mandatory exact Origin allowlist; Server không tin userId trong frame. Host là quyền Room, v1 Host eliminated vẫnCancel. PRIVATE outsiders404, History chỉ GameMember. DTO không Entity/passwordHash. HTTP/LAN không claim deployment Internet đủ bảo mật.

Transient rollback chắc chắn retry tối đa3 attempt/backoff100/300ms, không framework mới. Runtime/ACKcache/broadcast chỉ sau commit. Hết retry/commit bất định: runtime UNAVAILABLE, timers dừng, notification phân biệt persisted success; durable interruption cleanup chỉ hoàn tất khi DB ghi được, còn mất giữ cleanupPending/UAG. Startup ACTIVE bỏ lại→FINISHED/SERVER_INTERRUPTED/RoomWAITING/UAGrelease trước readiness; không resume game crash.

## 7. Kiểm chứng chức năng và provenance

Task24 fullverify một lượt: **302 unit+149 real MySQL/REST/rawWS IT**,0fail/error/skip. Bao phủ bảng/effects, typed scoring, deadline−1/exact/+1, ingress chậm, timer stale, Start same/crossRoom, UAG Spectator, queue isolation, duplicate/FINISHED replay, replacement/reconnect, Cancel/scoring, rollback/terminal/startup và media error. Clock/latch điều khiển dùng cho race, readiness polling không làm bằng chứng race.

Task24 browser Chrome154: **89 component+7 nhóm smoke**,4 cookie contexts thật. Game2070 đủ7màn/16câu có48Answers/38CORRECT/10WRONG, scores243/120/209; correct-time5114/4706/5136ms/ranks1/3/2 khớp SQL. Các trận2071 mộtRIDDLE,2072 khôngQuiz,2073Cancel giữAccepted-Unscored. Game50câu/150Answers trongIT: scores30/30/30,time200/400/200ms,ranks1/3/1,co-Winner; source edit sauStart không đổi game. Ảnh/video synthetic, không nội dung người chơi thực tế.

Evidence được sao lưu tại [archive Task21–24](../experiments/handoff/2026-10-10-seven-modes/archive-map.json). Manifest giữ original target paths/sourcehash, archive-map chỉ vị trí bản sao. Đây là lượt chạy trướcTask25, không cộng thành số test mới. Bảng Task25 smoke/build mới và Results được bổ sung dưới đây từ log thật.

## 8. Phương pháp thực nghiệm Task25

[Protocol](../experiments/experiment-protocol.md) và [environment](../experiments/results/2026-10-10-multimode-loopback/environment.json) ghi cấu hình, seed, hardware, phiên bản và source hash. Máy Windows11/10.0.26200, i5-13500HX,20 logical processors, RAM16886128640 bytes, JDK21.0.9/Python3.12.10/MySQL8.0.45. Base Git commit5fd560e3951238c3de9e52de8c7d91e3cc76983f không bao gồm working tree Tasks16–25; manifest SHA-256 mới xác định source thực đã đo. Server/MySQL/harness cùng máy qua127.0.0.1,4 game workers/JDBC pool5; không lấy thông tin môi trường06/10 thay số đọc hiện tại.

Baseline và proposed dùng cùng code/guards/MySQL/clock thật. ExperimentServer chỉ nằm test classpath/profile experiment, không trong JAR demo. Baseline xóa committed receipts sau commit/trước giao wrapped ACK; vẫn giữ1Spin/câu,1Answer/câu và mọi constraint. Reconnect baseline mở authenticated socket rồi subscribeRoom, không full Game snapshot. Proposed giữ replay response gốc và RECONNECT/apply private snapshot. Đây là ablation retention, vẫn có allocate/put rồi xóa; không phải no-cache implementation được tối ưu hiệu năng.

Mỗi mode10 reliability Game ×3Player Accounts =30trial/scenario, tải3/4Players ×3round, một Room đang hoạt động. Mỗi Client có Account/cookie riêng, thêm Author HTTP; tối đa5Accounts/mode. Fixture v2 Quiz10→Riddle1, initial0/floor0/no elimination, Intro10s/Decision7s/Question10s/Result1.5s. Benchmark dùng câuQuiz đầu rồi Cancel; không đo cả7màn hoặc media throughput. Browser smoke riêng kiểm chứng7mode. Không tăng tốc timer. ACK loss mô phỏng ứng dụng chưa apply ACK thật, không giả API/DB, không packet-loss/Wi-Fi outage emulator. Seed15062026 chỉ fixture/workload; SecureRandom Spin/UUID không seeded, raw response giữ draw thực.

| Metric | Bắt đầu → kết thúc / clock | Mẫu số |
|---|---|---|
| Server processing | Entry GameRuntime→future completion, System.nanoTime | Success spans của load; gồm queue/transaction/runtime/cache, không auth/parse/network send |
| Client RTT | Send command→nhận ACK/ERROR, perf_counter_ns | Từng command đo; không network-only latency |
| Original recovery | So toàn ACK retry bằng response gốc đã commit |30/scenario/mode cho lostSpin, lostAnswer, retryFinished |
| Snapshot response | Send RECONNECT→snapshot ACK, perf_counter_ns |30/phase/proposed; baseline N/A |
| Client resync | Socket-open→AUTH_READY→ACK→deep-copy snapshot vào harnessmodel |30/phase/proposed; không browser paint hoặc Server sent |
| State mismatch | Compare status/phase/index/revision/player/members với RESToracle sau apply |30/phase/mode; oracle không feed baseline state |
| Durable safety | History/Room/resource/uniqueAnswer và read-only SQL |16Game/mode,32tổng |

Dùng monotonic clock riêng trong mỗi process cho duration, không trừ timestamps giữa Client/Server. Raw reply serverTimeMs dùng epoch để ghi provenance: baseline1791574913423→1791575053686; proposed1791575250428→1791575391237, không thay latency measurement. Lỗi hệ thống làm harness fail và giữ partial data; expected rejection được phân loại bằng client/wire codes, server CSV REJECTED_OR_ERROR không đủ phân biệt một mình.

Hai mode chạy tuần tự baseline→proposed. CPU/RAM/TCP stack chung, không counterbalance/warmup hoặc utilization sampling; browser smoke chạy song song một phần trên DB riêng, cũng là background load. Không suy Wi-Fi/Internet/media latency hoặc reliability100% ngoài tập thử. Pilot Continue trước INTRO_STARTED bị INTRO_CLOSED được lưu riêng, không tính Results.

## 9. Results — chỉ dữ liệu đã chạy

[Tables tự sinh](../experiments/results/2026-10-10-multimode-loopback/results.md), [summary](../experiments/results/2026-10-10-multimode-loopback/summary.json), [raw/config](../experiments/results/2026-10-10-multimode-loopback/environment.json). Mỗi mode16Game/Room,208client metric rows; baseline289/proposed349server spans do số RECONNECT khác nhau. Tổng32trận đo, một Room concurrent; không cộng pilot hoặc4trận browser. Reliability dùng3Players; load dùng3/4, mỗi mức3round.

| Scenario | Baseline success/attempts | Proposed success/attempts | Baseline/proposed mismatch |
|---|---:|---:|---:|
| Lost Spin ACK: response gốc |0/30|30/30|Không phải metric state|
| Lost Answer ACK: response gốc |0/30|30/30|Không phải metric state|
| Retry Answer sau FINISHED |0/30|30/30|Không phải metric state|
| Reconnect DECISION |0/30|30/30|30/0|
| Reconnect sau Answer/phase advance |0/30|30/30|30/0|

Baseline có30SPIN_AFTER_STAR và60QUESTION_CLOSED là expected business rejection khi không giữ receipt, không phải90system errors. Mismatch là Client thiếu state, không corruption DB. Cả hai duplicate side effects0 và durable invariant violations0 trên16Gamechecks/mode. SQL toàn schema:0ACTIVE/UAG/duplicateAnswer/crossGameAnswer/Spin pool inconsistency/invalidunscored. Schema84trận =51lịch sử +1pilot +32đo mới; SERVER_INTERRUPTED1 là pilot bị dừng, không lỗi32trận chính thức.

| Players | Rounds/mode | Samples/mode | Baseline server p50/p95 ms | Proposed server p50/p95 ms | Baseline/proposed Client p95 ms |
|---|---:|---:|---:|---:|---:|
|3|3|18|26.509/59.833|30.141/63.381|62.901/71.558|
|4|3|24|41.610/77.502|40.677/80.265|81.629/83.513|

| Proposed metric (n30/phase) | DECISION p50/p95 ms | Sau Answer p50/p95 ms |
|---|---:|---:|
| Snapshot response send→ACK |4.447/12.586|4.701/11.428|
| Client resync open→model apply |23.461/43.058|30.513/38.188|

![Reliability thực đo](../experiments/results/2026-10-10-multimode-loopback/reliability.png)

![Server duration thực đo](../experiments/results/2026-10-10-multimode-loopback/server-latency.png)

SVG cùng thư mục dùng cho export. p95 nearest-rank;18/24mẫu nhỏ, không suy capacity. Proposed load p95 cao hơn baseline ở cả3/4 trong lượt này; đóng góp được chứng minh ở recovery, không luôn nhanh hơn.

### Build và smoke từ cấu hình mẫu

Task25 cached Maven package đạt302unit,0fail/error/skip/BUILD SUCCESS; frontend14modules/16assets, tất cả static assets trong JAR khớp source, không có ExperimentServer. JAR dùng bản sao byte-identical của config mẫu, credential DB qua environment. Fresh quizz_task25_handoff/MySQL8.0.45/FlywayV1–V7/readyUP. Không migration vào quizz người dùng.

Chrome154/4cookie contexts:89component+7smokegroups PASS, không mockAPI. Game1 đủ 7 mode/16câu/48Answer/38CORRECT10WRONG, SpinHARDSHIP nên P1 khôngStar; P2Star riêng đã test. Final221/120/209, correct-only5143/4693/5216ms, rank1/3/2 khớp SQL. Game2 RIDDLE20/20/20, Game3 Vua→Ordering40/40/40/noQuiz resources, Game4Cancel giữ1ACCEPTED_UNSCORED/noWinner. Các ID thuộc DBsmoke mới, khác v1Game1 ở schema khác.

Video reconnect playhead3.121013s, còn8828ms, deadline1791575257434 không đổi. RESULT/hint/INTRO/replacement và mấtAnswerACK/retry cùng UUID sauFINISHED đều PASS. Room1–4WAITING/UAG0. [History mobile390px](../experiments/handoff/2026-10-10-seven-modes/handoff25-seven-history-mobile.png) đã kiểm tra trực quan. Manifest/index giữ command/log/hash/SQL;149IT Task24 được tái sử dụng vì Task25 không đổi Java/schema/production/UI, không gọi đó là lượt chạy mới.

### Phiên bản cũ — không gộp

[Run06/10](../experiments/results/2026-10-06-loopback/results.md) thuộc Quizv1/Decision5s/RESULTcũ:30trial/scenario, proposed30/30recovery/modelmatching, baseline0/30,0duplicate/0invariant trong44Gamechecks;3round/mức3/5/10/20. CSV/log/env/hash giữ nguyên. [Handoff lịch sử](../experiments/handoff/historical-task15-evidence-index.md) giữ provenance. Không dùng các con số này để khẳng định benchmark v2/Wi-Fi.

## 10. Discussion và contribution

7mode là mở rộng sản phẩm, không tự nó là novelty mạng. Contribution cấp project: authoritative shared state; atomic ingress command/timer; RoomStart/UAG/scoring transaction; commit→runtime/cache/event; bounded original replay, private phase snapshot và generation replacement; nullable History/failure cleanup. Guard/constraint giữ safety ngay ở baseline; replay giải quyết ambiguity response, full snapshot khôi phục state bỏ lỡ thay vì chỉ nhận event tương lai.

Same-machine/JIT/run order/background browser/DB growth và instrumentation làm hạn chế so sánh latency. First-question synthetic benchmark không đo media throughput,50câu,7màn chơi hết hoặc nhiềuRoom cùng tải; các functional tests không chuyển thành số liệu benchmark. Không crash resume/failover. Local media và MySQL phải backup cùng bộ. Không gán contribution cá nhân từ evidence cấp project.

## 11. Reproducibility và bàn giao

[README](../README.md) hướng dẫn install/DB/config/server/client/test/LAN; [protocol](../experiments/experiment-protocol.md) có workload/metric/commands; [evidence index](../experiments/handoff/evidence-index.md) tách current/historical; [artifact audit](../experiments/handoff/artifact-audit.md) liệt kê Git/references/cleanup. Bộ review gồm source/tests/fixtures, pom/wrapper/migrations/scripts/sample, README/report/schema/guide/rules/contracts/decisions/checklist/team và raw measurement/evidence có hashes. Không gửi config credential/.cache/profile/browser state/userdata chưa được phép. Chưa tạo bundle theo format bắt buộc khi thiếu Submission.

Demo: Author đăng ký/đăng nhập→tạo7bộ/uploadPNG+MP4→HostSpectate và3Players khác→Room kế hoạch/Start→Continue/QuizSpinStar/typedAnswers→Result1.5s/autotransition→video/hint reconnect→Final/History/RoomWAITING; trận riêngCancel trướcscore→AcceptedUnscored/noOfficialWinner. Browser profiles cùng máy là Client riêng, không chứng minh4máyLAN. LAN bind0.0.0.0/exactOriginIP:port/firewallPrivateTCP; Client mởIPServer, khôngMySQL.

| Mục hồ sơ cần xác minh | Kết quả / evidence |
|---|---|
| Topics/Instruction/README mẫu/Submission nguyên bản | |
| Tên/MSSV/actual contribution4thành viên | |
| Compilatio và điều kiện môn | |
| Build/run trên máy mới | |
| LAN3–4thiết bị Wi-Fi thật | |
| Backup/restore DB+media trên máy mới | |
| Filename/format/channel/deadline nộp | |

Ô trống nghĩa chưa có evidence; planned assignment không thay công việc thực tế. [Checklist](course-requirements-checklist.md) ánh xạ mục tiêu→module→test và giới hạn. Task25 PARTIAL về hồ sơ/thiết bị, không tuyên bố đạt môn.

## 12. References

Nguồn implementation/luật: Overview/TASKS, task mở rộng16–25, decisions, canonicalREST/WS, DDL/source/tests và raw evidence của project. Tài liệu official các task trước đã đối chiếu: [SpringBoot3.5](https://docs.spring.io/spring-boot/3.5/reference/index.html), [MySQLCHECK](https://dev.mysql.com/doc/refman/8.0/en/create-table-check-constraints.html), [JSONschema](https://dev.mysql.com/doc/refman/8.0/en/json-validation-functions.html), [Java21nanoTime](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/System.html#nanoTime()), [WebSocketstandard](https://websockets.spec.whatwg.org/), [Chromeautoplay](https://developer.chrome.com/blog/autoplay/). Task25 không chọn API/version mới hoặc claim đã browse lại các nguồn đó. Không có course original để viện dẫn đúng section/threshold; báo cáo dùng cấu trúc dự án thông thường cho review.
