# KHẢO SÁT MỞ RỘNG QUIZ MULTIPLAYER THÀNH GAME 7 CHẾ ĐỘ

Ngày khảo sát: 09/10/2026. Project: Competitive Multiplayer Network Quiz System, D:/BTL_LTM.

## 1. Mục đích, phạm vi và mức bằng chứng

Đây là một báo cáo tự chứa nội dung để gửi Cowork làm đầu vào viết task triển khai. Đường dẫn được ghi để truy vết, không yêu cầu người đọc mở từng tài liệu khác mới hiểu kết luận. Chưa viết task mới hoặc thiết kế lại toàn bộ codebase.

Trong lần khảo sát không sửa source, migration, test, contract, cấu hình hoặc database; không chạy build, full suite, benchmark hoặc Server. Theo yêu cầu bổ sung của người dùng, chỉ tạo file báo cáo này. Không commit/push.

Đã kiểm tra source thật theo các đường: Quiz/Question CRUD → Room/Start → snapshot → phase/queue/timer → Answer → scoring/ranking → Result → reconnect → History/Cancel → frontend, cùng auth và media. Đã đọc phần liên quan của TASKS/Overview, trạng thái gần nhất, architecture freeze, canonical REST/WS, README và tài liệu môn/thực nghiệm hiện có. Không coi chữ DONE trong tài liệu là bằng chứng code đã chạy.

Không tìm thấy AGENTS.md ở project, D:/ hoặc cây project được kiểm tra. Tuân theo AGENTS instructions trong chat: trả lời tiếng Việt.

Không tìm thấy Topics/Topic2, Instruction, Submission, README mẫu môn nguyên bản, Proposal hoặc Technical Design V2. README hiện tại là README ứng dụng; final-report là báo cáo fallback theo cấu trúc dự án thông thường. Những yêu cầu mang tên Topic2/Instruction trong project là dẫn lại từ Overview, không chứng minh đã đối chiếu nguyên bản.

Nhãn sử dụng:

- IMPLEMENTED: tìm thấy implementation và test/evidence tương ứng cho bản Quiz.
- PARTIAL: có một phần tái sử dụng, chưa đủ yêu cầu mới.
- DOC-ONLY: chỉ có mô tả/dẫn lại, chưa có implementation hoặc nguồn gốc tương ứng.
- ABSENT: không tìm thấy implementation trong phạm vi khảo sát.
- UNVERIFIED: chưa có bằng chứng chạy đủ cho môi trường/khả năng được nói đến.

IMPLEMENTED không có nghĩa đã chạy lại ngày khảo sát. Evidence trong báo cáo là artefact lịch sử được đọc, có ngày và giới hạn.

## 2. Đặc tả mở rộng người dùng đã chốt

Các luật mới ở đây ưu tiên hơn Overview cũ ghi initial20/elimination. Không cần xác nhận lại những luật đã rõ.

### 2.1. Nền tảng

Một Spring Boot Server + một MySQL Database, JPA/Flyway, Web Client REST/raw WebSocket, localhost/LAN. Giữ stack/dependency và package-by-feature. Server quyết định gameplay; Host là quyền theo Room. Không Cloud/Redis/Kafka/multi-server/failover. Nhóm dự kiến 3–4 người.

### 2.2. Bảy chế độ

| Chế độ | Nội dung | Trả lời |
|---|---|---|
| Quiz | Câu với đúng 4 lựa chọn | Một option A/B/C/D |
| Đoán tên bài hát | Người tạo tự cắt video có hình và âm thanh, upload Server lưu local | Gõ tên bài hát |
| Vua Tiếng Việt | Chữ/mảnh chữ tạo từ hoặc cụm từ | Sắp xếp chữ/mảnh chữ |
| Đố mẹo | Câu đố bằng chữ | Gõ đáp án |
| Truy tìm dấu vết | Server mở gợi ý theo timeline chung | Gõ đáp án |
| Đuổi hình bắt chữ | Hình gợi ý từ/cụm từ; không chỉ gọi tên đồ vật | Gõ đáp án |
| Sắp xếp trình tự | Các mục cần sắp đúng thứ tự | Gửi toàn bộ kết quả sắp |

Mỗi người chơi/chấm riêng, không phải cùng nhóm nộp một Answer.

Room chọn 1–7 chế độ/màn, thứ tự màn, một bộ đúng chế độ cho mỗi màn, số câu và thời gian trả lời từng màn; tổng tối đa50 câu. Các màn dùng chung score/ranking.

Intro mỗi màn tối đa10s; đủ Player bấm Tiếp tục được mở sớm. Host Spectator không giữ điều kiện ready.

Danh mục:
- Của tôi: tất cả bộ của tôi, PUBLIC và PRIVATE.
- Chung: mọi PUBLIC, gồm PUBLIC của tôi.
- Không lộ PRIVATE người khác.
- Snapshot nội dung/đáp án/config/media ổn định trong trận và History.
- Chưa tự thay author/private policy cũ.

### 2.3. Điểm/resources

- Initial0; sau tính kết quả score≥0; không elimination.
- Chỉ Quiz dùng bảng cộng/trừ, Spin/Star/Momentum/Recovery hiện có.
- Decision7s trước mỗi câu Quiz; sáu mode khác không dùng Spin/Star.
- Spin/người = floor(tổng số câu Quiz toàn trận/10), dùng chỉ Quiz.
- KhôngQuiz → không cấp Spin/Star; cóQuiz → Hope Star tối đa1/người/trận.
- NonQuiz: correct+10, wrong/noanswer0; câu cuối của MỖI màn nonQuiz correct+20, wrong/no0.
- Câu cuối Quiz không double.
- Rank: scoreDESC, bằng điểm thì tổng thời gian CHỈ câu đúng ASC, bằng cả hai đồng hạng.

### 2.4. Deadline/offline/reconnect/media

Deadline chung, Server không pause khi Client mất mạng. Ví dụ câu20s, rớt ở5s về15s còn khoảng5s; đã chuyển câu thì nhận câu hiện tại, không làm lại cũ.

Một validAnswer/player/question. Đóng khi tất cả trong tập cần chờ đã nộp hoặc deadline:
- Online lúc mở rồi mất mạng chưa nộp: vẫn chờ đến deadline.
- Offline lúc câu mới mở: không nằm trong tập chờ đóng sớm của câu mới.
- Vẫn thuộc trận, có thể reconnect và trả lời câu hiện tại còn hợp lệ.
- Answer đã Server nhận không mất; timer cũ không chấm/mở lại.

Intro/hints có timeline chung; reconnect chỉ thấy phần đã mở/thời gian còn lại. Video reconnect phát theo tiến độ câu, không thêm thời gian nghe; có bước bật âm thanh và fallback Play nếu autoplay bị chặn.

Media local Server lỗi thì Client báo tải lỗi nhưng vẫn có thể đoán; trận tiếp tục tới đủ Answer hoặc deadline. Không Cloud/livestream/failover.

RESULT theo nhịp Server chung, hiện source thật đã có1,5s rồi tự chuyển câu.

## 3. Hiện trạng chạy được

### 3.1. Stack thực tế

Java21, Spring Boot3.5.16, Maven Wrapper3.9.12; JPA/MySQL/Flyway. README yêu cầu MySQL8.0.16+ để enforce CHECK; evidence MySQL8.0.45.

Frontend HTML/CSS/JavaScript ES modules trong src/main/resources/static/client, do Boot phục vụ cùng origin; không React/Vue/frontend server thứ hai, không Node/npm. Scripts Python/PowerShell phục vụ build/test/browser/experiment. Main vn.edu.quiz.QuizApplication.

Auth HttpSession/cookie + BCrypt + REST CSRF + WS Origin allowlist; rawWS /ws, không STOMP.

Trong bảng dưới, Java đường dẫn tương đối từ src/main/java/vn/edu/quiz; test ở src/test/java/vn/edu/quiz; frontend ở src/main/resources/static/client.

| Thành phần | File/class chính | Hành vi đã có | Test/evidence | Mức |
|---|---|---|---|---|
| Auth | AuthService, AuthConfiguration, AuthSessionRegistry, controller/security auth | Register/Login/Logout, hash, cookie/CSRF/Origin, identity Principal, session kiểm lại lúc xử lý/gửi | AuthNetworkIT; AuthorizationRepositoryIT; browser logout/expiry | IMPLEMENTED |
| Quiz CRUD | QuizService, QuizAccessPolicy, Quiz/Question entity/repository | Owner CRUD, revision, soft-delete/history; PUBLIC non-owner metadata, PRIVATE ẩn | QuizNetworkIT5; browser Quiz/Room | IMPLEMENTED |
| Mine/Shared | QuizService.list, QuizRepository.findVisible/findByOwner, quizzes.js | Một list gộp PUBLIC hoặc own; chưa hai scope | Source/contract list gộp | PARTIAL |
| Ảnh local | QuizImageStore, QuizService.upload/image | Immutable PNG hash; authorized released access | QuizNetworkIT; browser ảnh | IMPLEMENTED ảnh Quiz |
| Video | Không thấy upload/storage/video player | Chưa Song/video/seek | Không thấy test/evidence | ABSENT |
| Room/Waiting | RoomService, RoomBoundary/Operations, rooms.js | Host/roster/state/config/revision/WS; mộtQuiz/thời lượng chung | RoomNetworkIT; Chrome4cookiecontexts | IMPLEMENTED cũ, PARTIAL mới |
| Start/snapshot | GameOperations, GameTransactions.start, GameplayRulesSnapshot | Host/WAITING/≥3Player/10–50câu, author/UAG, immutable roster/content/config | GameLifecycleIT; MySqlSchemaIT; Start auth race | IMPLEMENTED mộtQuiz |
| Queue/clock/timer | SessionQueue, PhaseWindow, realtime/timer | FIFO từngGame/sharedpool, ingress nguyên tử, strictdeadline, monotonic, staleguard | SessionQueueTest21; lifecycle races | IMPLEMENTED |
| Phase/Result | GameLifecycle.open/timer/closed | Decision7s→OPEN→CLOSED→SCORING→RESULT1,5s→câu kế/End | GamePresentationIT6; Chrome | IMPLEMENTED |
| Intro/ready/stage | Không có runtime/phase/command tương ứng | Chưa intro10s/Continue/multi-stage | Không thấy test | ABSENT |
| Answer/replay | GameCommandParser, GameRuntime.command, GameReplayCache, transactions.accept | A/B/C/D, uniqueAnswer, ACK không correctness, replay trướcphase, commit rồicache | GameCommandNetworkIT11; lostACKsmoke | IMPLEMENTED Quiz; typed/order ABSENT |
| Spin/Star | SpinSelector, lifecycle.action, transactions.useSpin/useStar | Sample once/retry; resources; Star-only locksSpin; HARDSHIPnoStar | Engine/command/browser | IMPLEMENTED, chưa modeguard |
| Scoring/ranking | ScoreEngine, RankingEngine, transactions.score | Initial20, âm loại, time cả đúng/sai/noanswer; effects/bảngQuiz | ScoreEngine113; Ranking6; realMySQLscoring | IMPLEMENTED v1; PARTIAL v2 |
| Wait-set online | lifecycle.open, QuestionCloseGate | Chờ mọi PLAYING, không presence filter | Source/tests offline luật cũ | ABSENT luật mới |
| Reconnect/replacement | AuthenticatedSocketRegistry, runtime.reconnect, lifecycle.snapshot | One socket/User, generation, snapshot/revision, resources/deadline giữ | GameReconnectNetworkIT12; Chrome | IMPLEMENTED Quiz; PARTIAL nhiềumode |
| History/Cancel | HistoryService, transactions.cancel/finish/cleanup, history.js | Final/timeline/scope, unscorednull, HosteliminatedCancel, cleanupcommit | GameHistoryCancelIT12; ChromeHistory | IMPLEMENTED v1 |
| DBfailure/startup | lifecycle.attempt/unavailable; transaction interrupt/cleanup | Boundedretry, runtimeerror khác persistedEnd, stalestartupcleanup | SQLrollback/KILLconnection/startuptests | IMPLEMENTED theo test; wholeDBoutage giữaCOMMIT UNVERIFIED |
| UI | app/auth/quizzes/rooms/game-state/game/history/transport | Account→Quiz→Room→Game→Final/History thật, Client không chấm | Chrome10groups; DOM39tests | IMPLEMENTED Quiz; sáumode ABSENT |
| LAN nhiều máy | README/config/script | Có bind/origin hướng dẫn | Evidence chỉsame-machine | UNVERIFIED |
| Môn gốc | Checklist/report/README fallback | Có yêu cầu dẫn lại, thiếu bản gốc | Inventory tài liệu | DOC-ONLY/UNVERIFIED đốichiếu môn |

### 3.2. Evidence mới nhất đã đọc — 07/10/2026

- target/update-full-regression.log: **226 unit +104 MySQL/network integration**,0failure/error/skip, BUILD SUCCESS.
- Sau fullrun sửa MySqlSmokeIT isolation sang quizz_task2_test. update-isolated-smoke.log: **3unit+2IT PASS**; không cộng tests trùng vào tổng full.
- update-server-startup.log: Hikari kết nối MySQL; Flyway validate3migrations và migrate quizz V2→V3 thành công; Tomcat8080; Started QuizApplication.
- update-mysql-evidence.txt: MySQL8.0.45; V1/V2/V3success; **0ACTIVEGame/0activeUser/0duplicateAnswer** sau smoke. Snapshot5.000ms cũ vẫn có bên cạnh7.000ms mới.
- update-client-tests.json/update-browser.log: **10 smoke groups PASS**, Chrome154, **4 cookie contexts độc lập**;3Player hoàn thành10câu, HostSpectator+3Player, Spin/Star, lostACKreplay, offlineNOANSWER, replacement, eliminatedHostCancel, History/auth.
- update-client-unit-tests.json: **39 frontendunit PASS** cuối cùng; đây không phải39E2Eflows.
- target/client-build.json: **11modules/13assets**.

Các bằng chứng là bản Quiz, không chứng minh sevenmodes đã chạy. Không xác nhận Server/MySQL hôm nay đang UP. Ảnh/số test/CSV có ngày khác không được cộng trùng hoặc đổi nghĩa.

## 4. Bản đồ điểm tái sử dụng

Tên file và method trong phần này để truy vết. Nội dung hiện trạng và khoảng cách được giải thích ngay tại đây.

### 4.1. Quiz/Question CRUD, danh mục và quyền

QuizService.list/get/create/edit/delete đã xử lý owner, transaction, revision, lock và soft-delete. Khi sửa bộ câu, code đánh dấu các câu cũ không còn dùng và tạo thế hệ câu mới; lịch sử không bị xóa. QuizAccessPolicy tách quyền owner/public/private/author. Owner nhận nội dung và đáp án để biên soạn; non-owner của PUBLIC chỉ nhận metadata, không nhận câu/options/correctAnswer qua GET bộ câu. Không trả Entity trực tiếp.

Có thể dùng lại phần quyền, CRUD, error, revision và lưu lịch sử. Phần cố định cho Quiz là QuestionRequest, Entity và DDL đều yêu cầu đúng bốn options và một correctAnswer A/B/C/D. Chưa có mode, accepted text answers, pieces, order items, timed clues hoặc video.

Danh mục hiện là một list gộp điều kiện owner OR PUBLIC. Repository đã có findByOwner, nhưng chưa có hai scope Mine/Shared trong API và UI. Theo luật mới, bộ PUBLIC của chính mình phải xuất hiện ở cả hai tab, không chia thành hai danh sách loại trừ nhau.

Quyền PRIVATE thực tế có một điểm cần làm rõ: Host chỉ được chọn PUBLIC hoặc PRIVATE của mình. Khi người khác Join, RoomService.join kiểm tra usableQuiz bằng hostUserId, rồi kiểm tra author participation; không bắt người Join phải là Owner. Như vậy non-owner có thể chơi bộ PRIVATE của Host qua Room hợp lệ, dù không được browse/get source của bộ đó. Đây là khác biệt diễn giải cần chốt, chưa tự gọi là permission bypass.

Author hiện không được PLAYER của Quiz mình tạo; vẫn được Host/Spectator. Với trận nhiều bộ từ nhiều tác giả, chưa có policy kết hợp.

Truy vết: QuizService.java dòng34–43,60–99,109–137; QuizAccessPolicy10–19; QuizRepository13–20; RoomService100–116.

### 4.2. Room config và Start

Room/RoomMember, quyền Host, trạng thái DRAFT/WAITING/ACTIVE/CLOSED, roster, revision và pre-game boundary có thể dùng lại.

Room hiện chỉ có một quizId và một questionDurationMs. RoomConfigRequest chưa có stage list, mode, số câu/thời lượng từng màn. StartGameRequest chỉ có tổng N10..50. Thứ tự câu hiện được shuffle một lần khi Start và lưu cố định.

Start thực tế làm theo flow:

1. Serialize và replay theo Room trước khi GameSession tồn tại.
2. Lock Room; kiểm Host/WAITING/revision/không có ACTIVE Game.
3. Lock một Quiz; kiểm quyền Host chọn bộ.
4. Chụp roster; kiểm ít nhất3 Player, số câu khả dụng và author restriction.
5. Lock Users theo thứ tự; kiểm USER_ACTIVE_GAME, kể cả Spectator.
6. Tạo GameSession/GameMember/PlayerSession/GameQuestion/config/UAG trong một transaction.
7. Sau commit mới install runtime/handoff và phát event.

Giữ atomicity, lock order, UAG và roster bất biến. Cần mở validation/config/snapshot thành nhiều bộ/màn trong cùng Start. Không chỉ thêm list quizId ở UI hoặc ghi một phần stage rồi gọi Start cũ.

Truy vết: realtime/session/GameOperations; game/service/GameTransactions.start dòng57–101; RoomService.apply/snapshot154–173.

### 4.3. Game persistence và snapshot

Giữ GameSession làm identity toàn trận; GameMember là roster bất biến; PlayerSession lưu score/resources; GameQuestion lưu câu đã chọn theo global order; Answer gắn đúng PlayerSession và GameQuestion cùng GameSession bằng composite FK.

Unique Answer/player/question, snapshot không bị sửa theo source Quiz và Accepted-Unscored trước scoring đều dùng lại được. Cancel không ép một Answer đã nhận thành WRONG hoặc NO_ANSWER.

Cố định cho Quiz hiện nay:

- GameSession có một quizId, một author và title.
- GameQuestion có content,4options,correctOption,imageRef; chưa stage/mode/local index/last-in-stage.
- Answer chỉ selectedOption.
- Config snapshot v1 không có nhiều stage duration.
- GameSnapshot/HistoryDTO dùng Option; chưa typed input, permutation, hints, video.

Cần stage/source/mode/version, immutable content/canonical answer/matching policy/media và result theo loại. Public snapshot phải bỏ secret answer và future hints. Media reference phải đủ identity nguồn; một global quizId không còn đủ.

### 4.4. ScoreEngine và ranking

ScoreEngine thuần, không JPA/WS/DB/clock/random. GameTransactions.score tính kết quả tạm cho toàn câu rồi persist trong transaction. Giữ ranh giới này.

Engine hiện không phải bộ luật cấu hình tự do:

- GameplayRulesSnapshot chỉ chấp nhận schemaVersion1 và N10..50.
- ScoreEngine constructor so với factory v1 và reject rules khác.
- Factory initial20, Spin=count/10, Star1.
- Calculate cộng score+delta, score<0→ELIMINATED; chưa floor0.
- NO_ANSWER dùng full global questionDuration; total time cộng cả đúng/sai/no-answer.
- Elimination xét trước cập nhật/cấp streak effect.
- RankingEngine đã scoreDESC/timeASC và competition ties, nhưng metric time chưa đúng bản mới.

Có thể giữ evaluator bảng Quiz/effects và thuật toán tie. Cần rules version mới và correct-only time riêng cho trận mới. Sáu mode khác dùng chung outcome→10/20/0, không cần sáu scoring engine độc lập. Không đổi nghĩa time/score lịch sử v1.

Truy vết: ScoreEngine14–16,27–81; GameplayRulesSnapshot17–44; GameTransactions.score171–214.

### 4.5. Phase, ingress, queue, timer và wait-set

Flow thật hiện tại: DECISION → QUESTION_OPEN → QUESTION_CLOSED → SCORING → RESULT → câu kế hoặc FINISHED. Decision7.000ms; RESULT1.500ms chung, tính sau scoring commit. Terminal cleanup/rank/UAG/Room đã commit ngay; Client chỉ trình bày câu cuối theo remaining time, không mở câu tiếp.

SessionQueue dùng chung pool4 worker, FIFO từng Game. Ingress là enqueue/admission thành công, không phải lúc frame vừa tới network hoặc processor bắt đầu. Server gán timestamp monotonic, sequence và enqueue nguyên tử. receivedAt<deadline hợp lệ; đúng deadline hoặc sau bị reject. Command đã ingress trước Close không bị timer vượt qua dù processor chậm.

PhaseWindow dùng monotonic cho duration và epoch/remaining cho hiển thị. Phase mới lấy clock mới lúc Server thực sự mở. Timer có session/generation/question/phase/token; stale hoặc Close lặp no-op. Deferred retry nhường worker nhưng giữ thứ tự. Terminal route/cache giữ10 phút.

Khoảng cách:

- GameLifecycle.open tạo QuestionCloseGate từ mọi PLAYING, không lọc online.
- Gate yêu cầu tập không rỗng, chưa có empty wait-set policy.
- Registry hiện quản lý socket ownership/generation, chưa có điểm chụp online roster cho nghiệp vụ.
- Cần tách quyền trả lời khỏi tập cần chờ: offline-at-open vẫn thuộc trận và có thể trả lời khi reconnect, nhưng không thêm ngược vào wait-set của câu đã mở.
- Chưa có INTRO/Continue/stage transition/hint timeline.
- SessionQueue.armTimer chỉ giữ một timer phase/Session; arm mới hủy timer cũ và loại queued timer cũ. Không gọi armTimer riêng cho mỗi hint làm mất timer đóng câu.
- Reveal phải đi qua cùng queue, có stage/question/token guard và không đánh đổi deadline đóng câu.

Truy vết: GameLifecycle42–67,97–127; game/runtime/QuestionCloseGate và PhaseWindow; SessionQueue93–131,150–203.

### 4.6. Message, replay và giới hạn

Giữ envelope v1 COMMAND/ACK/ERROR/EVENT, target ROOM/GAME, actor từ auth:

- Pre-game/START: User+Room+requestId.
- Gameplay: User+Game+requestId.
- Fingerprint: type/target/questionIndex/canonical payload; object keys sort, arrays giữ thứ tự.
- Auth/member → lookup/mismatch → replay → phase/resources → business → commit → runtime/cache ACK → send.
- Rollback không cache ACCEPT; Spin capture một lựa chọn qua transaction retry.
- FINISHED route/cache10 phút; RECONNECT đọc fresh snapshot, không mutation receipt.

Parser hiện chỉ nhận ANSWER option A/B/C/D; runtime gọi Option.valueOf. Cần typed answer/permutation/Continue và mode-aware ACK/result/snapshot. Có thể giữ global questionIndex1..N; Continue phải định danh đúng stage/intro để retry cũ không làm ready màn mới.

Không nhận client userId/score/timestamp. Inbound8KiB, bounded queue và cache128receipt/User/Game đang đủ cho tối đa50Answer+5Spin+1Star. Payload và Continue mới cần đánh giá capacity, không tự nới vô hạn.

### 4.7. Reconnect và socket replacement

Đã có một socket active/User. Socket mới thay owner nguyên tử, gửi SESSION_REPLACED/close4002; Client cũ dừng auto-reconnect. Old disconnect không xóa owner mới.

Command cũ chưa bắt đầu processor bị reject sau replacement. Operation đã bắt đầu được hoàn tất một lần, kể cả transaction retry; socket mới dùng snapshot/replay để biết kết quả. Không undo accepted operation vì thay socket.

RECONNECT cùng queue chụp committed snapshot→subscribe→send trước operation kế. Không reset Answer/resources/deadline. Host Spectator player=null; OPEN không lộ đáp án/future question; RESULT có thời gian còn lại chung. FINISHED sau TTL đọc DB, không tạo lại runtime. Client bỏ revision thấp hơn nhưng vẫn xử lý các event cùng revision khác nhau.

Bản mới cần stage/mode/intro readiness, released clues, timeline, typed receipt, video offset và result vừa chấm lúc chuyển màn. Presence cho wait-set không được reset roster hoặc PlayerSession. Không cam kết server-crash recovery.

Truy vết: AuthenticatedSocketRegistry38–71; GameRuntime.reconnect140–167; GameLifecycle.snapshot185–194; frontend game-state/transport.

### 4.8. History, Cancel và DB failure

Giữ permission GameMember, pagination final games, standings/timeline, Accepted-Unscored nullable và commit-before-event.

Cancel/scoring cùng queue: scoring đã bắt đầu thì chấm toàn câu rồi Cancel; Cancel trước Close thì không chấm và không ép NO_ANSWER. Host Spectator/eliminated vẫn Cancel. CANCELLED/SERVER_INTERRUPTED không official Winner.

DB policy hiện có:

- Operation rollback chắc chắn retry tối đa3 lần, backoff100/300ms.
- Exhaust/uncertain commit → runtime UNAVAILABLE, stop timer, không fake ACCEPT/End persisted success.
- Durable cleanup SERVER_INTERRUPTED tối đa3 lần.
- DB còn mất → cleanupPending; không giả release UAG/Room/history. Restore DB rồi restart để startup cleanup.
- Startup không resume trận: ACTIVE bỏ lại thành FINISHED/SERVER_INTERRUPTED, release UAG/Room WAITING khi transaction ghi được.

Giữ policy này; bổ sung stage/mode/typed timeline/media/version/correct-only time. Không tạo history feature riêng hoặc retry framework mới.

Truy vết: GameTransactions.cancel/interrupt/cleanup/finish240–257; HistoryService; GameHistoryDetail.

### 4.9. Frontend và asset local

Giữ router/auth/REST/socket, pending UUID retry, revision, countdown, Waiting roster, Final/History shell.

Quiz editor hiện cố định4options/correctA-D và list gộp. Room chọn một Quiz/thời lượng, Start10..50. Game guards theo PLAYING/phase nhưng chưa mode; Game UI chỉ Decision Spin/Star và4buttons. History dùng selectedOption và URL ảnh theo global quizId. Standings dùng totalAnswerTime luật cũ.

Cần renderer theo mode trong shell submit/retry/reconnect chung. Không tạo client thứ hai hoặc bảy app riêng. Client không quyết định correctness/score.

Ảnh hiện có:

- PNG/JPEG≤2MiB; cạnh≤4096, tổng≤4 triệu pixel.
- Normalize PNG, loại metadata, SHA-256 immutable reference.
- Local path theo quizId/hash, không static public.
- Publish file hoàn chỉnh bằng hard link, không overwrite.
- Owner hoặc GameMember có câu đã mở mới đọc được.
- Read hash verify; missing404/storage503; UI báo lỗi, lifecycle không pause.
- Sửa/xóa Quiz không đổi reference lịch sử.

Video chưa có. Cần validator/container/size/duration, immutable local store, released access, seek/range, reconnect offset và audio fallback. Không dùng PNG decoder/limit cho video. Hash reference không cứu bytes đã mất; phải sao lưu DB và media cùng nhau.

## 5. Bảng Quiz và effects cần bảo toàn

Bảng thực tế v1, cũng là bảng Quiz phải giữ ở bản mới:

| Cách chơi | Correct | Wrong | NO_ANSWER |
|---|---:|---:|---:|
| Normal |+10|-4|-1|
| Star-only |+25|-14|-7|
| BONUS |+15|-4|-1|
| BONUS+Star |+30|-4|-1|
| SAFE |+8|-2|0|
| SAFE+Star |+20|-2|0|
| BREAKTHROUGH |+18|-7|-3|
| BREAKTHROUGH+Star |+30|-12|-6|
| SPEED |+22|-10|-4|
| SPEED+Star |+35|-16|-8|
| DECISIVE |+28|-15|-7|
| DECISIVE+Star |+40|-22|-10|
| HARDSHIP |+8|-6|-2|
| HARDSHIP+Star |Không hợp lệ|Không hợp lệ|Không hợp lệ|

Weights BONUS/SAFE/BREAKTHROUGH/SPEED/DECISIVE/HARDSHIP=25/20/25/15/10/5; draw không replacement trong pool riêng. Spin→Star được; Star→Spin cùng câu không được; HARDSHIP không Star.

- 5Correct liên tiếp cấp Momentum/reset win streak; 5Wrong cấp Recovery/reset lose streak.
- Correct reset lose; Wrong reset win; NO_ANSWER reset cả hai.
- Momentum Correct+3, consume khi Correct.
- Recovery chỉ dùng basePenalty âm: min(basePenalty+3,0).
- -1→0; -4→-1; -7→-4; -22→-19. Base0 hoặc Correct giữ Recovery; final0 vẫn consume.
- Không stack; Momentum/Recovery có thể cùng tồn tại.
- Effect mới không áp dụng câu tạo streak.
- V1 xét elimination trước streak; eliminated không nhận effect mới.

V2 giữ bảng/effects nhưng initial0/floor/noelimination. Cần phân biệt baseDelta, delta sau effect và biến động điểm thực tế sau floor: score0 gặp -4 vẫn0. UI không nên báo giảm4 nếu điểm thực tế không giảm. Biểu diễn field là quyết định kỹ thuật khi triển khai, chưa đổi contract.

## 6. Ma trận thay đổi cho bảy chế độ

| Mode | Dữ liệu/snapshot | Input/check Server | UI | Result/reconnect |
|---|---|---|---|---|
| Quiz | Giữ4options/correct/image; thêm stage/mode/version/duration | Option; unique Answer; mode guard tài nguyên | Dùng lại4choices/Decision7s | Effects/resources private, correct-only time; cuối màn không double |
| Song | Video immutable ref/metadata; tên/aliases secret | Text matcher; policy cần chốt | Video/audio enable/fallback+text | Seek tiến độ chung; lỗi media vẫn đoán; input/outcome/time |
| Vua TV | PieceIDs/values, target word/phrase secret | Đủ pieces/không thêm bớt; đúng từ hay đúng order cần policy | Arrange/click/drag, submit chung | Arrangement đã ACCEPT, đáp án sau chấm, deadline giữ |
| Đố mẹo | Text prompt+accepted answers secret | Text pipeline chung | Text+input | Outcome/input/time; Answer không mất |
| Dấu vết | Clues+offset+secret answer snapshot | Text; reveal cùng queue/token | Released clues/countdown | Released subset+remaining; stale hint no-op |
| Đuổi hình | Image immutable+secret word/phrase | Text; không object-label engine | Image/text/error fallback | Released access; accepted answer/result/time |
| Trình tự | Stable itemIDs+correct order secret | Full permutation, đúng tập/không duplicate | Arrange toàn bộ/mobile/keyboard | Accepted order private; canonical sau scoring; remaining |

Nhóm dùng chung để tránh bảy engine:

1. Match lifecycle/roster/stage/intro/open/deadline/close/scoring/Result/End.
2. Option evaluator cho Quiz.
3. Text evaluator cho Song/Riddle/Clues/Image-word.
4. Arrangement validation cho letters/order; correctness có thể khác.
5. Score policy theo version/mode; nonQuiz dùng chung10/20/0.
6. Renderer theo mode trong shell submit/reconnect chung.
7. Media reference/access chung, image/video validator riêng.

Nghiệp vụ vẫn thuộc game/quiz/room; queue/socket/replay thuộc realtime; pure engine không import JPA/WS. Đây là hướng đánh giá tái sử dụng, chưa freeze tên class hay thiết kế mới.

## 7. Schema và tương thích dữ liệu

### 7.1. Migration và bảng

- V1__quiz_domain.sql:11domain tables/FK/CHECK/uniqueness/immutable triggers.
- V2__require_answer_effect_snapshots.sql: scored result JSON bắt buộc effect fields.
- V3__decision_seven_seconds.sql: Room default7000, DRAFT/WAITING update+revision; không rewrite ACTIVE/FINISHED config.

Bảng: user_account, quiz, question, room, room_member, game_session, game_member, player_session, game_question, answer, user_active_game. History đọc các bảng game/question/answer, không có History tables riêng.

### 7.2. Các giả định phải mở rộng

| Giả định | Vị trí hiện tại | Yêu cầu v2 |
|---|---|---|
| Một bộ/phòng/trận | Room/GameSession quizId/author/title/media query | Stage/source/mode/order/count/time |
| N10..50 | DTO/snapshot constructor/Game CHECK | Tổng≤50; hỗ trợ các màn/trận ngắn hơn10 |
| Version1/initial20 | V1JSON CHECK+factory+engine constructor | Version mới initial0, v1 giữ nguyên nghĩa |
| Spin count/10,min1; Star1 | Config CHECK/spin factory | floor Quiz count/10 có thể0; noQuiz0resources |
| Âm/ELIMINATED | Player CHECK/elimination-freeze trigger/engine | New floor0/noelimination; legacy negative/trace giữ |
| 4options/correctA-D | Question/GameQuestion NOT NULL/CHECK | Mode payload/secret answer, không fake choices |
| selectedOption Answer | Answer CHECK/DTO/accept/UI | Typed input, đúng nullable unscored/noanswer |
| Total time mọi outcome | Engine/ranking/projection/UI | Correct-only metric v2, history không đổi |
| End theo survivors | transactions.score/endReason | V2 hết tất cả stage; abnormal End riêng |
| Quiz-only phase | Enum/DDL/lifecycle/timer/UI | Intro/stage/hints; nonQuiz không Decision |
| Immutable Quiz fields | V1 triggers | Mở rộng immutable snapshot fields mới |
| Effect JSON mọi câu | V2CHECK | Mode/version-aware result, không giả effect nonQuiz |
| Media mộtquizId | Image auth/URL/History | Multiset ref/released question/video |

Phải giữ unique global question order, unique Answer/player/question, same-Game composite FK, PlayerSession thuộc GameMember PLAYER, UAG unique User gồm Spectator, one ACTIVE Game/Room, immutable roster, không cascade History. Unscored result/delta/scoredAt nullable; NO_ANSWER receivedAt null.

### 7.3. Hướng migration (chưa tạo/chạy)

1. Migration mới sauV3; không sửa checksum V1–V3.
2. Version rules/content/result; không sửa factory v1 thành v2 rồi áp mọi History.
3. Stage config và immutable GameStage; giữ Game identity/roster/global index.
4. Mode payload/answer/media; không ép nonQuiz A-D.
5. Legacy có thể quy chiếu một Quiz stage, nhưng phải giữ config/content/score/rank/time/end nguyên nghĩa; backfill cụ thể cần test.
6. CHECK/triggers có điều kiện version/mode; Java-only không đủ.
7. Tách correct-time metric hoặc version hóa ý nghĩa rõ; không overwrite total time cũ.
8. Không clamp legacy âm, xóa elimination trace hoặc sửa Winner cũ.
9. Migration DB không bảo toàn filesystem; backup media bytes/ref đồng bộ.
10. Test DB mixed v1/v2, snapshot/rank/privacy/media/FK/CHECK sau upgrade.

ALL_ELIMINATED v1 vẫn có rank1 là Winner theo xác nhận G-01/G-02 cũ. Không xóa endReason đó khỏi reader lịch sử. CANCELLED/SERVER_INTERRUPTED đều không official Winner.

## 8. Rủi ro có bằng chứng

| Điểm | Đã xử lý ở bản hiện tại | Khoảng trống/giới hạn |
|---|---|---|
| Correct/future leak | Owner/public mapper, OPEN hidecorrect, released-image tests | Không gửi aliases/canonical order/future clues của payload mới trước chấm |
| Media URL | Hash không tự cấp quyền; nonstatic/member/released check | Query mộtquizId cần multiset; video access chưa có |
| Video/autoplay/slow load | Chưa video, chưa lỗi video thực tế được đo | Cần playback/seek/audio fallback/error; không gọi đây là bug đã xảy ra |
| Deadline | Clock/latch -1/exact/+1 và processor blocked | Giữ admission cho text/order; không tin clock Client |
| Offline early close | One-close guard, allPLAYING đúng v1 | Chưa wait-set mới/empty-set; quyền Answer khác tập chờ |
| Reconnect chuyển màn | Actor snapshot/revision/generation/resources giữ | Intro/hints/video/stage chưa có; cần race transition |
| Duplicate | Replay/fingerprint/uniqueAnswer/Spin once/finishedTTL | Continue cần stage identity; payload/capacity mới |
| Start/config race | Room lock/pre-game cache/sorted Users/UAG tests | Snapshot/permission/lock nhiều bộ phải atomic |
| Stale hint | Close timer stale guard có test | Arm hint không được đè close; hint sauCancel/nextq no-op |
| Local file mất | Immutable/hash verify/read404503; image error không pause | Ref không cứu bytes; chưa backup/restore video evidence |
| DB rollback | Temp→TX→runtime/cache/event; rollback/KILL tests | Giữ full scoring transaction; whole-DB outage giữa COMMIT chưa verified |
| Limits | Inbound8KiB, bounded queue/cache | Text/order/hints/Continue cần tính lại budget |

Các mục future là khoảng trống triển khai, không phải lỗi đã xảy ra. Wait-set/schema mismatch với luật mới không chứng minh bản cũ sai luật cũ.

## 9. Test dùng lại và cần bổ sung

### 9.1. Test source có thật

| Nhóm | Nội dung |
|---|---|
| ScoreEngine113/Ranking6 | Full Quiz table, Recovery/effects/coexist, score0alive/âm loại, ties |
| MySqlSchemaIT12 | Cross-game FK, uniqueAnswer, PLAYER/SpectatorUAG, oneACTIVE, rejoin, unscored/noanswer, immutable config |
| SessionQueueTest21 | Strict deadline/FIFO, blocked processor, A chậm B tiến, stale token/q/phase/generation, retry/cleanup |
| GameLifecycleIT18 | Quiz-edit snapshot, same/crossRoom Start/Spectator, roster race, rollback, noanswer/end priority |
| GameCommandNetworkIT11 | Same/different ID, fingerprint mismatch, resources, retry close/finished, DB fail noACCEPT |
| GameReconnectNetworkIT12 | Phases/resources/privacy/nullability, old-generation policy, ordering, TTL fallback |
| GameHistoryCancelIT12 | Cancel/scoring, Host eliminated, unscored history, scope/page, rollback/KILL/startup |
| GameIsolationIT2 | Hai Room độc lập, failureA không fenceB |
| GamePresentationIT6 | Legacy snapshot byte-identical,7s/1,5s, reconnect1ms/stale, CancelResult, terminal cleanup |
| Auth/Quiz/Room network | Cookie/CSRF/Origin/logout, owner/private/public, image/history, roster |
| CodebaseStructureTest | Feature ownership/JPA/context/circularfalse |
| Frontend/browser scripts | UUID/revision/countdown/pending, actual Quiz/History/Cancel/replacement |

Giữ regression v1, bổ sung v2; không đổi hết expected cũ thành mới hoặc disable test để đạt pass.

### 9.2. Chưa tìm thấy test/evidence cho

- Ít nhất hai màn/mode dùng chung score, intro/Continue/autotransition.
- Initial0/floor/noelimination/correct-only tie-time; wrong/no-answer không tăng tie-time.
- Quiz<10 Spin0; noQuiz0Spin/Star; tổng Quiz count qua nhiều stage.
- NonQuiz last-of-EACH-stage20; Quizlast khôngdouble.
- Cross-mode streak/effects theo policy cần chốt.
- Matching Unicode/aliases và permutation invalid; secret payload leak.
- Multiauthor/PRIVATE policy, sửa nhiều source sets sau Start.
- Online-at-open/disconnect/offline-next-open/reconnectAnswer/empty set.
- Intro ready/deadline/duplicate/reconnect/replacement/Spectator exclusion.
- Hint release queue/stale/deadline/released subset.
- Video upload/access/immutability/offset/autoplay/slow load/local error.
- Mixed v1/v2 History/media/rank/migration constraints.
- Browser nhiều account chơi bản7mode thật, LAN nhiều máy, experiment bản mới.

## 10. Đối chiếu môn và thực nghiệm

### 10.1. Requirement → evidence → bổ sung

Thiếu môn nguyên bản: bảng này đối chiếu prompt và Overview dẫn lại, không xác nhận đạt môn.

| Yêu cầu | Hiện có/evidence | Bản7mode cần |
|---|---|---|
| Client/Server | Browser RESTWS→Spring auth/game/DB; HTTPWSMySQL/Chrome | Giữ authority, giải thích input/result/timeline |
| Protocol rõ | Canonical RESTWS/envelope/parser/scope/error/revision | Stage/type/typedAnswer/Continue/hints/media/nullability |
| MultiClient/shared state | 3Player10q, HostSpectator+3, shared deadline/leaderboard | Multi-stage/ready/hints/shared score flow |
| Concurrency | Room boundary/queue/DB locks, clock/latch, same/crossStart/2Rooms | Multi-set Start, ready/timer, hint/close, reconnect stage |
| Errors/timeout/duplicate/disconnect/DB | LostACK/stale/replacement/rollback/KILL/startup | New input retry/presence/media errors, atomicity giữ |
| Novelty/contribution | Original replay/fullsnapshot vs ablation | 7mode không tự là network novelty; claim mới cần đo |
| Experiment | CSV/env/seed/config thật06/10 | Run new version, start/end/denominator rõ |
| README/report | App README/fallback report/diagrams | Thiếu template gốc; update stages/DB/media/trace/Results |
| Demo LAN | Bind/origin/firewall hướng dẫn | Chưa multi-device evidence;3–4 thiết bị Wi-Fi |
| Nhóm/Compilatio | Planned contribution, actual/hồ sơ thiếu | Họ tên/MSSV/evidence actual, policy/kết quả gốc |
| Submission | Không bản gốc | Chưa đoán format/tênfile/USB/Drive/deadline |

### 10.2. Số liệu cũ giữ đúng phiên bản

Experiment ngày06/10/2026: cùng máy Windows11, i5-13500HX20threads, RAM~15,73GiB, Java21.0.9/Python3.12.10, DBquizz_task15_experiment. TCP loopback, Decision5.000ms/answer10.000ms. Không LAN/Internet, không bản07/10 hoặc7mode.

Baseline chỉ experiment: vẫn oneSpin/q và oneAnswer/q, nhưng không replay original response; có reconnect nhưng không fullsnapshot. Không bật trong demo. Proposed có replay/fullsnapshot.

| Scenario | Baseline success | Proposed success |
|---|---:|---:|
| LostAnswerACK |0/30|30/30|
| LostSpinACK |0/30|30/30|
| ReconnectafterAnswer |0/30|30/30|
| ReconnectDecision |0/30|30/30|
| RetryFinished |0/30|30/30|

Baseline30mismatch/30 ở mỗi scenario reconnect; proposed0. Baseline0success là tiêu chí receipt recovery/model matching của ablation, không nghĩa Server crash hoặc duplicate effect.

Load3/5/10/20 account riêng,3round/mức, một Room đồng thời.22durable Game checks/mode,44official Game/Room checks tổng; không44Rooms concurrent. Duplicate side effects0/invariant violations0 ở22checks mỗi mode.

Server processing p95(ms), baseline/proposed:
- 3Client:142,149 /81,849.
- 5Client:111,948 /161,266.
- 10Client:256,693 /165,633.
- 20Client:285,865 /451,814.

Proposed không luôn nhanh hơn. Không suy Wi-Fi/Internet latency từ loopback. Đã đọc results/summary/environment/raw evidence; không chạy lại hoặc sửa CSV.

Bản mới cần ít nhất một nhóm thực nghiệm thật, version/hash/env/config/seed/CSV/log rõ. Nếu gọi client resync time, phải đo tới Client apply snapshot, không chỉ Server gửi. Expected rejection khác system error; media/network latency khác server processing.

### 10.3. Bàn giao còn thiếu

Topics/Instruction/Submission/README mẫu gốc; thông tin/actual contribution nhóm; Compilatio; LAN nhiều thiết bị; build/run máy mới; format/tênfile/kênh/deadline nộp; Results bản mới. Không tự tick pass.

## 11. Thứ tự phụ thuộc đề xuất, không phải task mới

1. Chốt policy thiếu và version dữ liệu/rules; public-secret projection, legacy compatibility.
2. Một vertical slice nhiều màn không media: bộ mode/Room stages/atomic Start/intro/Continue/global order; Quiz+Đố mẹo CRUD→Start→Answer→Result→Final/History thật.
3. Rulesv2 và eligibility: initial0/floor/noelimination, Quiz-count resources/nonQuiz10–20/correct-time, presence wait-set; MySQL rollback và legacy regression.
4. Arrangement modes: Vua TV+Trình tự, chung validation/renderer/retry/privacy.
5. Media/hints: Image-word dùng ảnh; Song localvideo/audio offset; Clues shared timeline/queue guard.
6. Browser multiplayer/isolation/LAN/experiment và đồng bộ contract/README/report theo implementation thật.

Nên có một flow nhiều màn trước video/hints. Không viết lại transport/auth/architecture đã đúng. Cowork có thể chia nhỏ hơn, nhưng giữ transaction/commit boundary và v1 evidence.

## 12. Nghiệp vụ thật sự chưa chốt

Không hỏi lại0điểm/floor/noelimination/7s/10–20/correct-time/offline deadline/media error continue.

### 12.1. Text matching và arrangement tương đương

Prompt nói “gõ tên”/“gõ đáp án”, chưa quy định hoa/thường, whitespace, Unicode, dấu tiếng Việt, aliases, typo/fuzzy. Vua TV khi pieces giống nhau tạo cùng từ: chấm theo từ/cụm hay một permutation chuẩn?

Cần chốt trước evaluator/editor/snapshot/tests. Không tự bỏ dấu hoặc fuzzy vì đổi kết quả.

### 12.2. Streak/Momentum/Recovery qua nonQuiz

Nguồn mới “Chỉ Quiz giữ ... các hiệu ứng Quiz hiện có”. NonQuiz không dùng Quiz effects đã rõ, nhưng effects/streak đang có được giữ qua nonQuiz đến Quiz sau hay reset ở boundary màn?

V1 chỉ một Quiz, chưa có case này. Cần quyết định carry/pause hoặc reset, không tự consume/cấp ở nonQuiz.

### 12.3. Empty wait-set và intro ready

Offline lúc mở không giữ early-close. Nếu all offline, tập chờ rỗng: đóng ngay hay đợi deadline cho người reconnect có cơ hội? Gate cũ reject empty.

“Đủ Player bấm Tiếp tục” chưa nói toàn roster hay online-at-intro-open. Host Spectator chắc chắn bị loại khỏi ready. Empty ready set có tự mở sớm không? Cần chốt vì đổi runtime/tests.

### 12.4. Author restriction nhiều bộ

TASKS Overview dòng1036: “Tác giả không được chơi Quiz của mình; vẫn được Host và quan sát”. Prompt mới yêu cầu chỉ báo hiện trạng, chưa tự thay luật.

Code cấm author PLAYER cả trận một Quiz. Nhiều bộ: cấm toàn trận nếu là author bất kỳ bộ, hay chỉ không chơi màn tự tạo? Hai cách đổi roster/eligibility. Chưa được tự chọn hoặc bỏ restriction.

### 12.5. PRIVATE nghĩa “sử dụng”

TASKS dòng345/Overview1010: “PRIVATE chỉ Owner quản lý/sử dụng”. Join code dùng quyền Host, người khác vẫn chơi PRIVATE của Host qua Room.

Cần rõ PRIVATE chỉ giới hạn discover/select/edit và cho invited Players chơi, hay cấm cả non-owner chơi? Chưa tự kết luận permission bypass.

### 12.6. Technical choices không cần hỏi framework

Tên field/class, stable item IDs, global index, storage layout/limits/errors, migration mechanics và callbacks nhỏ có thể tự chọn khi implement, ghi lý do. Giữ minPlayer3 nếu chưa yêu cầu đổi. Không giữ mù min10 của v1 cho stages mới; count validation phải được mô tả rõ. Không bắt người dùng chọn framework hoặc tự tạo file.

## 13. Truy vết các file chính

Nội dung khảo sát đã tự chứa phía trên; bảng này giúp người triển khai tìm đúng vị trí.

| Vai trò | Đường dẫn |
|---|---|
| Yêu cầu/architecture/status | TASKS.md; docs/gameplay-rules.md; docs/implementation-decisions.md; docs/project-status.md |
| Canonical contracts | docs/rest-api-contract.md; docs/websocket-contract.md |
| Backend base | src/main/java/vn/edu/quiz/ |
| Quiz | quiz/service/QuizService.java, QuizAccessPolicy.java, QuizImageStore.java; entity/repository/dto |
| Room | room/service/RoomService.java; room/entity/repository/dto |
| Start/lifecycle/transaction | realtime/session/GameOperations.java; game/service/GameLifecycle.java, GameTransactions.java |
| Snapshot/History | game/service/GameProjection.java, HistoryService.java; game/dto/response/GameSnapshot.java, GameHistoryDetail.java |
| Rules/engine | game/dto/GameplayRulesSnapshot.java; game/engine/ScoreEngine.java, RankingEngine.java |
| Guards | game/runtime/PhaseWindow.java, QuestionCloseGate.java |
| Infra/protocol | realtime/session/SessionQueue.java, GameRuntime.java; realtime/timer; realtime/websocket/GameCommandParser.java; realtime/idempotency |
| Socket | realtime/connection/AuthenticatedSocketRegistry.java |
| Frontend | src/main/resources/static/client/quizzes.js, rooms.js, game.js, game-state.js, history.js, transport.js |
| Migrations | src/main/resources/db/migration/V1__quiz_domain.sql, V2__require_answer_effect_snapshots.sql, V3__decision_seven_seconds.sql |
| Tests | src/test/java/vn/edu/quiz/; scripts/test-game-client.py |
| Evidence07/10 | target/update-full-regression.log, update-isolated-smoke.log, update-server-startup.log, update-mysql-evidence.txt, update-client-tests.json, update-client-unit-tests.json |
| Experiment06/10 | experiments/experiment-protocol.md; experiments/results/2026-10-06-loopback/ |
| Hồ sơ fallback | README.md; docs/final-report.md, course-requirements-checklist.md, team-contributions.md |

Khảo sát hoàn tất trong phạm vi đọc và tổng hợp. Bản7chếđộ chưa triển khai/chưa kiểm chứng. Các policy mục12 cần chốt trước task phụ thuộc. Không tạo task mới, migration hoặc contractv2.
