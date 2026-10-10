# MultiGame — báo cáo bàn giao

Đây là tài liệu tổng hợp đang sử dụng để đọc và chạy project. Các tài liệu phát triển, contract và checklist trước đây đã được hợp nhất vào đây ngày 10/10/2026. Không thay đổi code, protocol, gameplay, schema hoặc dữ liệu trong lần dọn này. Markdown nằm trong các thư mục thực nghiệm là bằng chứng lịch sử có checksum, không phải hướng dẫn phiên bản mới.

## 1. Hệ thống và phạm vi đã có

MultiGame là game thi đấu cá nhân, Web Client REST/raw WebSocket, một Spring Boot Server và một MySQL Database trên localhost/LAN. Server giữ shared state, thời gian, quyền, chấm điểm và xếp hạng. Client render, nhận event/snapshot và gửi lựa chọn; không tự chấm hay chuyển câu. Không có Cloud, Redis, Kafka, multi-server, failover hoặc phục hồi trận đang chạy sau server crash.

Bảy chế độ có authoring, dữ liệu, xử lý Answer, snapshot/reconnect và UI thật:

| Chế độ | Nội dung và cách trả lời |
|---|---|
| QUIZ | Bốn lựa chọn A/B/C/D, chọn một; Spin/Star chỉ dùng tại đây |
| SONG | Video MP4 hình và tiếng do tác giả cắt/upload; gõ tên bài |
| VIETNAMESE_PUZZLE | Sắp các mảnh chữ theo thứ tự để tạo từ/cụm từ |
| RIDDLE | Câu đố bằng chữ, gõ đáp án |
| CLUES | Các gợi ý được Server mở theo mốc chung; gõ đáp án |
| IMAGE_WORD | Ảnh gợi từ/cụm từ, gõ đáp án |
| ORDERING | Sắp các mục rồi gửi toàn bộ danh sách ID |

Một Room v2 chọn 1–7 chế độ khác nhau theo thứ tự, mỗi màn một bộ đúng mode, số câu và thời gian; tổng 1–50 câu. Ít nhất 3 Player để Start. Các màn dùng chung điểm/ranking. Tác giả của bất kỳ bộ được chọn không được chơi trận đó, nhưng có thể Host Spectator. Bộ PUBLIC của mình nằm cả “Của tôi” và “Chung”; PRIVATE chỉ chủ sở hữu thấy/sử dụng.

## 2. Cấu trúc và đường đọc code

```text
pom.xml, mvnw, mvnw.cmd, .mvn/   Một Maven module; build/wrapper
src/main/java/vn/edu/multigame/
  MultigameApplication.java       Entry point, Spring scan
  auth/                          Login/register/session/CSRF/security
  user/                          User entity và repository
  questionbank/                  Bộ câu hỏi bảy mode, CRUD, ảnh/video
  quiz/                          Option của chế độ Quiz
  room/                          Room config/roster, boundary trước Start
  game/                          Engine thuần, lifecycle, snapshot, History
  realtime/                      WS/messages, queue/timer, replay, connection
  common/                        Tiện ích thật sự dùng chung
  system/                        Config và status/readiness
src/main/resources/
  application*.yml               Cấu hình và profile
  db/migration/                  Flyway V1–V7
  static/client/                 Frontend JavaScript/CSS/HTML native
src/test/                        Unit, MySQL/REST/WS integration và fixtures
config/                          Mẫu cấu hình; file local không đưa vào gói
scripts/                         Run/build/test, fixture và thực nghiệm
data/quiz-images/, quiz-videos/  Media thật của người dùng/demo
experiments/                     CSV/log/manifest/seed/config và evidence
docs/assets/                    Screenshot thật của phiên bản cũ
PROJECT_REPORT.md               Tài liệu bàn giao hiện tại
```

Tên toàn hệ thống là MultiGame, base package vn.edu.multigame, JAR multigame-0.0.1-SNAPSHOT.jar. Entity Quiz/Question, bảng quiz, DB quizz, /api/quizzes và các khóa quiz.* vẫn giữ để tương thích. questionbank sở hữu bộ câu hỏi nhiều mode; không đồng nghĩa mọi lớp có tên Quiz chỉ còn xử lý mode Quiz. Không có Maven multi-module.

Entry points: MultigameApplication; auth/controller và auth/security; questionbank/controller/QuestionBankController; room/controller; game/service; realtime/websocket; game/engine. Frontend app.js, core.js, api.js, transport.js, game-state.js và question-banks.js, game.js, history.js cùng các module mode. Đọc luồng: auth cookie → WS adapter → ingress timestamp/sequence → session queue → GameService → pure engine → DB commit → runtime/replay/event → frontend. Start trước khi có GameSession dùng Room boundary; sau Start handoff sang session processor. Engine không đọc JPA, clock/network hoặc random Spin.

## 3. Môi trường, build và chạy

Java 21; Maven Wrapper 3.9.12; Spring Boot 3.5.16; MySQL 8.0 có CHECK constraint thực thi (đã kiểm chứng 8.0.45). Python 3 cho script build/test/experiment; frontend không cần npm. Browser Chromium/Chrome được dùng cho smoke; bình thường người chơi có thể dùng trình duyệt khác có JS/WebSocket/video. Không nâng dependency để chạy.

PowerShell từ thư mục project:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.9"  # đổi đúng nơi cài
$env:MAVEN_USER_HOME = Join-Path $PWD ".cache/maven-home"
mysql --protocol=TCP -h 127.0.0.1 -P 3306 -u root -p -e "source scripts/prepare-database.sql"
if (!(Test-Path config/application-local.properties)) {
  Copy-Item config/application-local.properties.example config/application-local.properties
}
# Điền DB_USER và credential riêng trong file local hoặc biến môi trường; không nộp credential
python scripts/build-client.py
.\mvnw.cmd -B --no-transfer-progress verify -Ddebug=false
$env:DB_USER = "root"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.ps1 -PromptForPassword
```

Tên mẫu cấu hình ở trên phải khớp file hiện có trong config; xem cây thực tế nếu bản đóng gói đổi tên mẫu. start-local nhập password ẩn, không lưu ra đĩa. Cũng có thể chạy `.\mvnw.cmd spring-boot:run` sau khi đã cấp config/biến môi trường hoặc `java -jar target/multigame-0.0.1-SNAPSHOT.jar --debug=false`. Chạy từ root để đường dẫn media/config tương đối đúng. Mở http://127.0.0.1:8080. Server phục vụ luôn Web Client, không cần server frontend thứ hai. web-only không kiểm chứng gameplay/MySQL.

Các biến: DB_HOST mặc định 127.0.0.1, DB_PORT 3306, DB_NAME quizz, DB_USER quiz_app, DB_PASSWORD cần tự cấp; SERVER_ADDRESS 127.0.0.1, SERVER_PORT 8080; ALLOWED_ORIGINS là danh sách origin chính xác. QUIZ_IMAGE_DIRECTORY và QUIZ_VIDEO_DIRECTORY mặc định data/quiz-images và data/quiz-videos. Không đặt credential thật vào báo cáo/Git. IntelliJ chọn vn.edu.multigame.MultigameApplication, JDK21, working directory root; biến PowerShell ở cửa sổ khác không tự truyền cho IntelliJ. ExperimentServer là harness test, không phải main ứng dụng.

### 3–4 máy cùng Wi-Fi

Trên máy Server lấy IPv4 mới bằng ipconfig. Ví dụ:

```powershell
$env:SERVER_ADDRESS = "0.0.0.0"
$env:ALLOWED_ORIGINS = "http://192.168.1.234:8080,http://localhost:8080,http://127.0.0.1:8080"
.\mvnw.cmd spring-boot:run
```

Các máy Client vào http://192.168.1.234:8080, đăng nhập tài khoản riêng. Wi-Fi khác chỉ đổi IP trong URL/origin rồi restart Server; không đổi DB_HOST nếu MySQL vẫn trên máy Server. 0.0.0.0 là địa chỉ bind, không phải URL để mở. Cho phép inbound TCP8080 ở mạng Private trong Windows Firewall; kiểm tra router không bật client isolation. Không mở MySQL3306 cho Player, không tắt toàn bộ firewall. Localhost của máy Client chỉ chính máy đó. Các browser/profile/incognito riêng trên một máy có thể là nhiều Client, nhưng không chứng minh LAN nhiều máy thật. Một User chỉ một active socket nên hai tab cùng account sẽ replacement.

### Test và fixture

```powershell
mysql -h 127.0.0.1 -u root -p -e "source scripts/prepare-test-database.sql"
.\mvnw.cmd -B --no-transfer-progress verify -Pmysql-smoke -Ddebug=false
python scripts/build-client.py
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-client.ps1
# Script browser/CDP và fixture từng mode ở scripts/test-*-client.py
```

Profile MySQL integration dùng DB test riêng, không dùng H2 để kết luận MySQL đạt; đọc config test trước khi chạy để không trỏ nhầm DB người dùng. Fixture demo opt-in bằng profile sample-data và QUIZ_DEMO_PASSWORD: demo_author + demo_player1..3, một bộ PUBLIC10 câu. Seed không âm thầm reset dữ liệu/password đã sửa; conflict sẽ báo lỗi. Các bộ mode/media khác có thể tạo bằng UI hoặc script fixture đã giữ.

## 4. Database, media và backup

JPA validate, Flyway migration/validate khi startup; không sửa checksum migration đã áp dụng, không bật Flyway clean. V1 nền tảng11bảng; V2 snapshot effect; V3 Decision7s; V4 dữ liệu đa mode/GameStage/schemaVersion; V5 RoomStage; V6 alias Vua Tiếng Việt; V7 dấu vết gợi ý đã mở. Có 13 entity/repository: USER, QUIZ, QUESTION, ROOM, ROOM_MEMBER, GAME_SESSION, GAME_MEMBER, PLAYER_SESSION, GAME_QUESTION, ANSWER, USER_ACTIVE_GAME và hai bảng stage. Tên SQL chính xác nằm trong migration, không đổi tên chỉ để đồng bộ thương hiệu.

FK/NOT NULL/CHECK/UNIQUE bảo vệ dữ liệu: Answer chỉ liên kết PlayerSession và GameQuestion cùng GameSession; PlayerSession phải là GameMember PLAYER; một Answer/player/question; GAME_QUESTION UNIQUE(game_session_id,order_index); RoomMember UNIQUE(room,user); USER_ACTIVE_GAME unique User gồm Host Spectator. Leave giữ membership LEFT, Join lại tái sử dụng hàng; elimination lịch sử không bị xóa. Quiz sửa/xóa mềm không cascade xóa snapshot/History. Roster, source author, nội dung/đáp án/config, thứ tự trình bày và reference media được snapshot tại Start.

Ảnh PNG/JPEG ≤2MiB được chuẩn hóa PNG bất biến theo SHA256. Video MP4 local ≤20MiB/120s/1080p, H264 + AAC phù hợp bộ kiểm tra; Server hỗ trợ Range. mediaRef là sha256:64hex; không dùng URL ngoài. Giữ cả buckets và drafts, không tự suy ra file orphan để xóa. History/trận cũ có thể tham chiếu đường dẫn media ngoài data qua config test; lần dọn này không xóa các thư mục media trong target.

Backup cùng nhau: MySQL dump (ví dụ mysqldump --single-transaction --routines --triggers quizz), data/quiz-images, data/quiz-videos và mọi thư mục media đã cấu hình riêng; giữ V1–V7/config mẫu. Không copy password thật vào gói công khai. Chưa có evidence restore dump trên máy mới. DB quizz người dùng còn V3 trong lần kiểm tra trước; V7 mới đã chạy trên DB bàn giao riêng, không tuyên bố quizz đã được nâng. Backup rồi chạy migration theo đúng DB dự định.

## 5. REST thực tế và quyền dữ liệu

API giữ nguyên /api/quizzes để tương thích dù authoring đã đa mode. Response trả DTO, không serialize Entity/password_hash. Lỗi có code/message/serverTimeMs đã lọc chi tiết nội bộ; một số lỗi container/CORS không theo JSON ứng dụng.

| Endpoint | Hành vi/quyền |
|---|---|
| GET /api/system/status; GET /api/system/ready | Status và readiness; ready200 khi Server/DB sẵn sàng, 503 nếu chưa sẵn sàng |
| GET /api/auth/csrf | Lấy token/headerName trước POST/PUT/DELETE |
| POST /api/auth/register | username/password/displayName; 201, không tự login |
| POST /api/auth/login; GET /api/auth/me | Login200 tạo/rotate session; me chỉ User đã auth |
| POST /api/auth/logout | 204, invalidate session và socket cùng session |
| POST /api/quizzes | Tạo bộ: title, visibility, mode, questions; 201 |
| GET /api/quizzes?scope=MINE/SHARED/VISIBLE&mode=...&page=0&size=20 | MINE gồm bộ riêng PUBLIC/PRIVATE; SHARED mọi PUBLIC kể cả mình; VISIBLE own+PUBLIC; size1–100 |
| GET /api/quizzes/{id} | Owner có nội dung/đáp án; non-owner PUBLIC chỉ metadata; PRIVATE người khác404 |
| PUT /api/quizzes/{id} | Owner, full replacement + revision; mode bất biến, không xóa History |
| DELETE /api/quizzes/{id}?revision=... | Owner, soft delete204 |
| POST /api/quizzes/images/drafts; POST /api/quizzes/{id}/images | Upload ảnh draft theo User hoặc Owner bộ; 201 |
| GET /api/quizzes/images/drafts/{hash}; GET /api/quizzes/{id}/images/{hash}?gameSessionId=... | Draft chỉ chủ; ảnh bộ chủ hoặc GameMember của câu đã mở |
| POST /api/quizzes/videos/drafts; POST /api/quizzes/{id}/videos; GET đường dẫn tương ứng | Video SONG với quyền tương tự, download hỗ trợ Range200/206 |
| POST /api/rooms | requestId + config; tạo DRAFT và Host member |
| GET /api/rooms; GET /api/rooms/{id} | Phòng mình đang JOINED; DRAFT chỉ Host |
| GET /api/rooms/by-code/{code} | Preview WAITING, không trả roster/nội dung bí mật |
| PUT /api/rooms/{id}; POST /api/rooms/{id}/open; POST .../close | Host; requestId/revision; sửa DRAFT/WAITING, open DRAFT→WAITING, close DRAFT/WAITING→CLOSED |
| POST /api/rooms/{id}/start | Host WAITING; requestId/revision/questionCount; 200 gồm gameSessionId, room, serverTimeMs |
| GET /api/games/{id}/snapshot | Chỉ GameMember; snapshot riêng theo User |
| POST /api/games/{id}/cancel | requestId; Host GameMember kể cả Spectator/eliminated; finalSnapshot |
| GET /api/games/history?page=0&size=20 | FINISHED mà User là GameMember, kể cả đã Leave/Spectator |
| GET /api/games/history/{id} | GameMember; trận chưa FINISHED409 HISTORY_NOT_READY |

Auth dùng cookie JSESSIONID HttpOnly/SameSite=Lax, session HTTP idle30phút. WS frame không gia hạn HTTP session. Browser không lưu JWT/password; server lấy identity từ principal, không tin userId client. POST/PUT/DELETE gửi X-CSRF-TOKEN; lấy token mới sau login. Username ASCII chữ/số/underscore3–32, lowercase Locale.ROOT; password8–72 ký tự và tối đa72byte UTF8, không trim; BCrypt cost10; displayName tối đa100.

Bộ câu hỏi: title không trắng≤200; questions1–50; content không trắng≤5000. QUIZ đúng4option≤2000 và một correctAnswer A/B/C/D. Text answer modes có acceptedAnswers1–20, từng alias không trắng≤300; chuẩn hóa NFC/lowercase ROOT/trim/co cụm Unicode whitespace, giữ dấu/chấm câu, không fuzzy. IMAGE_WORD bắt buộc ảnh, SONG bắt buộc video. VIETNAMESE_PUZZLE pieces và ORDERING items có2–100 mục, id duy nhất [A-Za-z0-9_-]{1,64}, text≤300; correctOrder chứa mỗiID đúng một lần. Vua Tiếng Việt có alias khớp cách ghép mảnh; ORDERING dùng permutation chính xác. CLUES1–20hint với offsetMs int64≥0 tăng nghiêm ngặt, text≤2000; mọi hint phải trước deadline cấu hình. Không nhận shape lẫn mode hoặc field bí mật thuộc mode khác.

Metadata gồm id/ownerUserId/title/visibility/questionCount/createdAtMs/revision/mode; owner thêm questions. Non-owner PUBLIC không được lấy đáp án hay nội dung qua GET bộ; nội dung chỉ được phát theo câu trong Game đã tham gia. Media snapshot không cho xem câu tương lai.

Room config v2: name≤200, maxPlayers3–100, hostParticipation PLAYER/SPECTATOR, stages[{mode,quizId,questionCount,questionDurationMs}]; mode không trùng, tổng≤50. V2 không gửi legacy quizId/questionDurationMs ở cấp config. Roster tối đa100member; Room ACTIVE không đổi config/participation/roster trận. Response gồm id/hostUserId/name/status/roomCode/maxPlayers/revision/members/configVersion/stages và legacy quizId/quizTitle/questionDurationMs nullable; member có membershipId/userId/displayName/host/participation/joinedAtMs. Stage có orderIndex/mode/quizId/ownerUserId/title/deleted/questionCount/questionDurationMs.

Start atomically giữ Room lock trước GameSession, kiểm tra Host/state/roster/config/sourceauthor/activeuser; Start cùng Room chỉ một trận, chéo Room chung User chỉ một transaction thành công nhờ USER_ACTIVE_GAME. RequestId UUID lowercase; questionCount phải bằng tổng plan; trận v1 còn ràng buộc10–50. Không broadcast thành công trước DB commit.

Nhóm lỗi:400validation/media/mode;401chưa auth hoặc login sai;403CSRF/quyền/author;404đối tượng không thấy;409state/revision/requestId mismatch/resources/start/active-user;413media quá lớn;503DB/storage/server busy hoặc capacity. UI hiển thị code, không coi rejection dự kiến là system error.

## 6. Raw WebSocket, ordering, retry và reconnect

Endpoint /ws, dùng cookie browser và Origin chính xác trong ALLOWED_ORIGINS. Không auth bằng query/userId. Handshake chưa auth401, origin thiếu/sai403, query400, DB auth lỗi503; browser thường chỉ thấy close1006 khi handshake thất bại. Auth ready là EVENT AUTH_READY có serverTimeMs và payload userId/username/connectionGeneration.

Envelope command hiện có:

```json
{"v":1,"kind":"COMMAND","requestId":"UUID-lowercase","type":"ANSWER","target":{"kind":"GAME","id":1},"questionIndex":1,"payload":{"option":"A"}}
```

Tất cả field trên bắt buộc; không thêm field ngoài contract. target.id int64 dương; questionIndex1..N hoặc null đúng command; payload luôn object. Room command questionIndex=null. Game index là global index toàn trận, không reset mỗi màn.

| Type / target | Payload và semantic |
|---|---|
| JOIN_ROOM / ROOM | roomCode, participation; WAITING |
| LEAVE_ROOM / ROOM | {}; non-Host, JOINED WAITING |
| REMOVE_MEMBER / ROOM | userId; Host, không tự remove |
| SUBSCRIBE_ROOM / ROOM | {}; đọc update phòng mình; DRAFT chỉ Host |
| START_GAME / ROOM | revision, questionCount; cùng boundary/replay với REST |
| CONTINUE / GAME | {}; index hiện tại, INTRO Player ready trước deadline |
| USE_SPIN / USE_STAR / GAME | {}; index hiện tại, DECISION QUIZ đủ tài nguyên/quyền |
| ANSWER / GAME | đúng một shape: {option:A/B/C/D}, {text:...} hoặc {itemIds:[...]}; OPEN trước deadline |
| RECONNECT / GAME | {}; index=null; kiểm tra GameMember và nhận snapshot ACTIVE/FINISHED |
| CANCEL_GAME / GAME | {}; index=null; Host kể cả Spectator/eliminated |

ACK: v1/kind ACK/requestId/type/target/status ACCEPTED/revision/serverTimeMs/payload; gameplay có questionIndex nullable theo command. Answer/Spin/Star payload có selectedOption nullable, submittedAnswer nullable, alreadyAnswered, spinEffect nullable, starSelected, remainingSpins, starAvailable, remainingSpinPool. Answer ACK không có correctness. Continue trả stageIndex/readyPlayers; Start trả StartResponse; Cancel trả finalSnapshot. RECONNECT trả GameSnapshot + connectionGeneration. Room command trả room; Leave có left=true.

ERROR có v1/kind ERROR/requestId/target (nullable khi parse fail), code/message/retryable/serverTimeMs, gameplay questionIndex nullable. Không cache business rejection, không trả ACCEPT khi DB fail. Frame giới hạn8192byte. Close4001 LOGGED_OUT/SESSION_EXPIRED,4002 SESSION_REPLACED;1011 auth/send unavailable;1008unsupported message; quá lớn có thể1009. Session expiry sweep1s.

Game events gồm GAME_STARTED, INTRO_STARTED, INTRO_UPDATED, DECISION_STARTED, QUESTION_START, CLUES_RELEASED, QUESTION_CLOSED, SCORING_STARTED, QUESTION_RESULT, PLAYER_ELIMINATED (v1), LEADERBOARD_UPDATED, GAME_END, GAME_UNAVAILABLE. Envelope v1/kind EVENT/type/target/eventId/revision/serverTimeMs/payload là snapshot theo người nhận. Room có ROOM_UPDATED và ROOM_ACCESS_REVOKED(reason NOT_A_MEMBER). Auth ready/replacement dùng envelope auth không có revision/target; không giả chúng là gameplay state.

Idempotency scope User+Room+requestId trước Start, User+GameSession+requestId sau Start. Fingerprint gồm type,target,index,canonical payload SHA256 (sort key object, giữ thứ tự array). Auth/identity/generation kiểm tra trước replay; replay trước validate phase/index hiện tại. Cùng ID khác nội dung từ chối, cùng nội dung trả response gốc kể cả sau Close/FINISHED. Spin random một lần trong action/TX rồi persist/cache, không draw lại do retry. Cache put/runtime update sau commit; rollback không lưu ACCEPT. Một valid Answer/player/question dù dùng ID khác. Client retry phải giữ ID/payload gốc; JOIN mới sau Leave phải dùng ID mới.

Retention: receipt Room10phút; actor/replay Game10phút sau terminal, không xóa ngay End. Room capacity512scope/1024receipt, admission65; socket tối đa32room subscription. Capacity đầy báo lỗi hữu hạn thay vì bỏ receipt gây side effect lặp. RECONNECT không phải action receipt; hết retention có thể đọc final immutable DB snapshot. Không cam kết replay cache sau server crash.

Một active socket/User: socket mới tăng generation, gửi SESSION_REPLACED rồi đóng socket cũ; socket cũ dừng auto-reconnect. Command cũ đã enqueue nhưng chưa bắt đầu bị fence; operation đã bắt đầu hoàn tất một lần, old socket không gửi kết quả vào socket mới. Disconnect cũ không đánh offline socket mới. Không reset score/streak/resources/answer/UAG.

Ingress timestamp/sequence/enqueue nguyên tử dưới cùng boundary cho command và timer. Nội bộ dùng monotonic time, Client nhận epoch deadline/serverTimeMs/remainingMs. receivedAt < deadline mới hợp lệ; >=deadline từ chối dù timer chưa xử lý. Processor chậm không cho timer vượt command đã ingress trước. Session xử lý tuần tự qua worker pool chung; Session khác còn worker vẫn tiến triển. Timer có game/question/phase/token/generation, cũ hoặc đóng lặp no-op.

RECONNECT snapshot/subscription/send ở cùng queue trước event tiếp theo. Client dùng revision + serverTimeMs/eventId trong scope đúng Room/Game và generation để bỏ dữ liệu cũ; ACK replay giải quyết request, không rollback state. Cùng revision nhưng khác eventId vẫn xử lý event liên quan. Deadline tiếp tục khi offline. CLUES chỉ snapshot phần đã mở; timer hint sau close no-op. SONG reconnect seek theo openedAtMs/server time, không thêm thời gian nghe; cần bật âm thanh/nút Play dự phòng nếu autoplay bị chặn. Media lỗi phía Server không tự cancel: UI báo lỗi tải, Player vẫn có thể đoán tới đóng câu.

## 7. Snapshot, Result và History

GameSnapshot gồm gameSessionId/roomId, legacy quizId/quizAuthorUserId/quizTitleSnapshot nullable ở v2, status/phase/questionIndex/questionCount/revision/serverTimeMs, deadlineEpochMs/remainingMs nullable khi không có deadline, runtimeState/cleanupPending, endReason nullable, winners, hasOfficialWinner, schemaVersion, config(v1)/v2Config(v2), members, question, results, player, stages/stageIndex/stage/readyPlayers.

Stage giữ sourceQuizId/authorUserId/title/orderIndex/mode/firstQuestionIndex/questionCount/duration. Member có role/participation/state/score/time/rank; Spectator không có playerState/score/rank. `player` là trạng thái riêng của người nhận: Host Spectator player=null; Player có score, win/lose streak, Momentum/Recovery, remainingSpins/starAvailable/remainingSpinPool/currentSpin/starSelected/alreadyAnswered, selectedOption hoặc submittedAnswer, dấu vết elimination(v1), totalAnswerTimeMs và totalCorrectAnswerTimeMs(v2).

- INITIALIZING/INTRO/DECISION: không lộ câu/đáp án tương lai; INTRO có readyPlayers và deadline chung.
- QUESTION_OPEN: chỉ câu hiện tại, options nếu Quiz/pieces/items/media/hints đã mở nếu mode khác; correctAnswer/acceptedAnswers/correctOrder không có. SONG có mediaRef/openedAtMs. CLUES chỉ prefix đã release, không gửi hint tương lai.
- CLOSED/SCORING: chưa coi là đã chấm, không lộ đáp án; deadline có thể null.
- RESULT: câu vừa chấm, đáp án/matchingPolicy tương ứng; results có outcome/baseDelta/ruleDelta(v2)/scoreDelta/scoreAfter, answerTimeMs/totalTime, streak trước/sau và các flag consume/grant effect, state/elimination; deadline1500ms dùng để Client hiển thị. Không lộ câu tiếp theo.
- FINISHED: final standings/winners/endReason/officialWinner, không deadline; Answer đã ACCEPT nhưng chưa chấm khi Cancel vẫn riêng, không giả có correctness.

History list chỉ trận FINISHED của chính GameMember. Detail có startedAtMs/finishedAtMs/finalSnapshot và các câu đã mở, không câu tương lai. Mỗi câu giữ content/options/imageRef/mode/payload, openedAtMs/deadlineAtMs/scoredAtMs nullable và answer timeline. Answer có answerStatus/selectedOption hoặc submittedAnswer/receivedAtMs/answerTimeMs/spinEffect/starSelected/result và score fields. ACCEPTED_UNSCORED giữ đáp án đã nhận + receivedAtMs, nhưng scoredAtMs/baseDelta/scoreDelta/scoreAfter/result=null; NO_ANSWER receivedAtMs=null. Không ép đáp án chưa chấm thành sai hoặc NO_ANSWER. Elimination v1 và score đóng băng vẫn trong History. Owner bộ không tự có quyền xem History nếu không GameMember.

## 8. Luồng trận và điểm hiện tại

Start tạo roster GameMember bất biến, PlayerSession, GameQuestion/config/stage/media snapshot và USER_ACTIVE_GAME trong một transaction. V2 Player bắt đầu0; không loại; floor điểm sau tính là0. Spin floor(tổng số câu QUIZ/10), Star1 khi cóQuiz; khôngQuiz thì cả hai0.

Mỗi màn INTRO tối đa10s; đủ mọi Player roster Continue thì bắt đầu sớm, Spectator không giữ điều kiện ready. Quiz có DECISION7s rồi QUESTION_OPEN theo duration màn; mode khác mở câu sau INTRO. Server đi CLOSED→SCORING→RESULT1500ms→câu/màn tiếp hoặc FINISHED. Deadline mới tính từ lúc Server thật sự mở phase. Client không tự chuyển/chấm khi countdown về0.

Tập cần chờ ở v2 là Player online lúc mở câu. Mất mạng sau mở vẫn phải chờ người đó tới answer/deadline; nếu vẫn offline lúc câu kế mở thì không giữ điều kiện đóng sớm câu mới, nhưng vẫn thuộc trận và có thể reconnect trả lời trước deadline. Tập chờ rỗng không tự đóng tức thì. Mỗi Player một Answer hợp lệ; đủ tập chờ đã trả lời đóng sớm hoặc timer đóng tại deadline. Server chấm cả Player roster, chưa trả lời là NO_ANSWER. Timer/Close/Scoring chỉ thực hiện một lần.

Sáu mode ngoài Quiz: đúng+10, riêng câu cuối MỖI màn đúng+20; sai/NO_ANSWER0. Không nhân đôi câu cuối Quiz. Quiz giữ bảng delta sau:

| Mode Quiz | CORRECT | WRONG | NO_ANSWER |
|---|---:|---:|---:|
| Normal | 10 | -4 | -1 |
| BONUS | 15 | -4 | -1 |
| SAFE | 8 | -2 | 0 |
| BREAKTHROUGH | 18 | -7 | -3 |
| SPEED | 22 | -10 | -4 |
| DECISIVE | 28 | -15 | -7 |
| HARDSHIP | 8 | -6 | -2 |
| Star riêng | 25 | -14 | -7 |
| BONUS + Star | 30 | -4 | -1 |
| SAFE + Star | 20 | -2 | 0 |
| BREAKTHROUGH + Star | 30 | -12 | -6 |
| SPEED + Star | 35 | -16 | -8 |
| DECISIVE + Star | 40 | -22 | -10 |
| HARDSHIP + Star | Không hợp lệ | Không hợp lệ | Không hợp lệ |

Spin pool trọng số BONUS25/SAFE20/BREAKTHROUGH25/SPEED15/DECISIVE10/HARDSHIP5, rút không hoàn lại và chuẩn hóa trọng số còn lại. Mỗi câu tối đa1Spin; Spin→Star được, Star trước khóa Spin; HARDSHIP không Star.

5CORRECT liên tiếp tạo Momentum; 5WRONG liên tiếp tạo Recovery; NO_ANSWER reset cả streak. Momentum+3 ở CORRECT kế tiếp, Recovery chỉ khi basePenalty âm: min(basePenalty+3,0), consume kể cả final0; Correct hoặc base0 giữ Recovery. Effect mới không áp dụng ngay câu tạo streak; hai effect có thể cùng tồn tại, không stack cùng loại. Spin/Star không thay cách đếm outcome. Sang mode khác giữ streak/effect nhưng không dùng/cập nhật/cấp tại mode đó. V2 floor sau tính: ruleDelta khác scoreDelta thực khi bị chặn ở0.

Ranking v2: score giảm dần, tổng thời gian CHỈ câu đúng tăng dần; bằng cả hai đồng hạng (1,1,3), mọi người hạng1 đều Winner khi kết thúc bình thường.

Tương thích v1: trận cũ ban đầu20, score<0 bị loại (0 còn sống), cấu hình mộtQuiz10–50, ranking theo totalAnswerTimeMs cũ; elimination trước cập nhật/cấp effect. ONE_SURVIVOR/ALL_ELIMINATED ưu tiên trước COMPLETED khi trùng; hạng1 đồng hạng đều Winner kể cả ALL_ELIMINATED. Dữ liệu v1 không được reinterpret thành luật v2. CANCELLED/SERVER_INTERRUPTED có standings nhưng hasOfficialWinner=false và winners=[], không tuyên bố thắng chính thức.

### Commit, Cancel và DB lỗi

Scoring dùng state tạm, persist toàn câu trong transaction; chỉ thay runtime/cache/broadcast sau commit. Rollback không partial scoring hay event thành công. Cancel và Close/Scoring vào cùng queue: scoring bắt đầu trước thì chấm toàn câu rồi Cancel; Cancel trước thì giữ ACCEPTED_UNSCORED, không chấm câu đó. Host v1 eliminated vẫn Cancel, không Answer/Spin/Star. Kết thúc giải phóng UAG/Room về WAITING trong transaction nhất quán, không xóa replay cache ngay.

DB failure policy có giới hạn: transient rollback chắc chắn retry3attempt với backoff100/300ms; hết retry hoặc kết quả commit không chắc chắn → runtime UNAVAILABLE, dừng timer, notification lỗi terminal khác persisted success. Durable SERVER_INTERRUPTED cleanup retry hữu hạn; nếu DB còn mất thì cleanupPending=true, không giả đã lưu History/giải phóng UAG. Startup xử lý ACTIVE bỏ lại thành FINISHED/SERVER_INTERRUPTED, Room WAITING, dọn active-user trước readiness. Không phục hồi trận từ giữa sau crash.

## 9. Kiểm tra thật và phạm vi bằng chứng

Source snapshot gần nhất: namespace MultiGame sau refactor, Java21.0.9/Boot3.5.16/Maven3.9.12/MySQL8.0.45 trên Windows. Không xem task DONE hay tài liệu là chứng minh code chạy.

| Kiểm tra đã chạy trước lần dọn | Kết quả thật | Evidence giữ lại |
|---|---|---|
| verify -Pmysql-smoke -Ddebug=false (full regression một lượt) | 302 unit/21suite +149 MySQL/REST/rawWS integration/22suite;0fail/error/skip | experiments/handoff/refactor-multigame-2026-10-10/refactor-full-verify.log, refactor-test-counts.json, XML surefire/failsafe |
| python scripts/build-client.py | 14module/16asset build đạt | Log build trong archive bảy mode và JAR inspection |
| JAR mới + MySQL quizz_task25_handoff V7, port8087 | HTTP ready200, ServerUP/DBUP; main vn.edu.multigame.MultigameApplication | refactor-readiness.json, refactor-jar-server.log, refactor-jar-inspection.txt |
| Chrome154,4context/account riêng, test-seven-modes-client.py --smoke | 89component check +7realflow group PASS | refactor-multigame-client-tests.json, refactor-browser-smoke.log, screenshot |
| DB sau browser | Game5/6/7 FINISHED/COMPLETED,8 CANCELLED;0ACTIVE/0UAG/0invalidunscored,4Room WAITING | refactor-browser-db.tsv |
| Quizz người dùng trước/sau | V3/count/checksum không đổi; không migrate DB người dùng trong refactor | refactor-db-before.tsv/refactor-db-after.tsv |
| So sánh mechanical refactor + archive | 180body production không đổi logic ngoài tên/comment/label; migration và measured evidence không đổi | refactor-mechanical-audit.json/refactor-archive-check.json/refactor-result.json |

Browser flow thật gồm CRUD7mode/media/private quyền, INTRO/replacement, Quiz10câuDecision7s/Spin/Star, lostAnswerACK/replay sau FINISHED, trận bảy mode/Final/History, RIDDLE-only không cấpQuizresources, Host Spectator/Cancel. Không đồng nghĩa đã đo Wi-Fi3–4máy; đây là same-machine profiles. Evidence Task21–25 trước đổi namespace được giữ ở experiments/handoff/2026-10-10-seven-modes với manifest/hash, có phiên bản tương ứng. Screenshot docs/assets/gameplay-2026-10-07 là UI Quiz v1 lịch sử, không chứng minh đa mode.

Lần dọn hiện tại chỉ hợp nhất/xóa tài liệu và bản sao generated; kiểm tra sau diff ghi ở mục12. Không chạy lại benchmark hoặc toàn451test nếu source không đổi và không có concern mới.

## 10. Thực nghiệm và đóng góp có evidence

Dữ liệu gốc [đa mode 10/10/2026](experiments/results/2026-10-10-multimode-loopback/) và [Quiz v1 06/10/2026](experiments/results/2026-10-06-loopback/) được giữ nguyên CSV/log/config/seed/environment/plots/sha256/measured-source. Markdown results trong mỗi bộ thuộc dữ liệu lịch sử bất biến; các đường dẫn source cũ ở đó là provenance, không là lệnh chạy namespace hiện tại. Baseline chỉ profile/harness test, ExperimentServer không nằm trong productionJAR.

Đo10/10: Windows10.0.26200/i5-13500HX/20logicalCPU/RAM16886128640byte, JDK21.0.9/Python3.12.10/MySQL8.0.45, loopback127.0.0.1, mộtRoom tại mộtthời điểm, workers4/pool5. Baseline và proposed mỗi16Game;10reliabilityGame×3Player=30trial mỗi scenario mỗi nhánh;3round/load3và4Player. 32Game checks:0duplicate sideeffect/0invariant violation. Load spans18/24 mỗi nhánh.

| Scenario | Baseline success | Proposed success |
|---|---:|---:|
| Lost Spin ACK: khôi phục response gốc | 0/30 | 30/30 |
| Lost Answer ACK | 0/30 | 30/30 |
| Retry sau FINISHED | 0/30 | 30/30 |
| Reconnect trong DECISION | 0/30, mismatch30 | 30/30, mismatch0 |
| Reconnect sau Answer | 0/30, mismatch30 | 30/30, mismatch0 |

Baseline vẫn giữ1Spin/q và1Answer/q nhưng erase receipt sau commit; không phải tối ưu no-cache thực sự. Baseline reconnect mở socket/auth/subscribeRoom nhưng không fullsnapshot; proposed replayresponse/fullprivateSnapshot. Baseline30SPIN_AFTER_STAR+60QUESTION_CLOSED là expected rejection, không systemerror. ACK loss do harness bỏ response thực ở tầng ứng dụng, không mock API/không chứng minh mất gói Wi-Fi vật lý.

Server processing p95(ms), nearest-rank: load3 baseline59.833/proposed63.381; load4 77.502/80.265. Không tuyên bố proposed luôn nhanh hơn. Client resync MODEL apply p95:DECISION43.058ms,afterAnswer38.188ms; không phải browser paint.

Server duration nanoTime từ GameRuntime entry tới futurecomplete gồm queue/TX/runtime/cache, không auth/network; Client RTT perf_counter từ send tới ACK; original-response recovery đòi response gốc bằng nhau. Snapshot response là RECONNECTsend→ACK; client resync từ socketopen/authready→ACK→deepcopy apply model, RESToracle chỉ đối chiếu sau đó. Mẫu số success30/scenario, không giấu retry/baseline rejection.

Fixture benchmark Quiz10→Riddle1 nhưng đo câuQuiz rồiCancel, không benchmark video/hint/7modefullGame. INTRO10s/DECISION7s/Question10s/RESULT1.5s, không tăng tốc timer. Seed15062026 điều khiển workload, không thay SecureRandomSpin; raw ghi draw thực. 208clientCSVrow/nhánh, server289baseline/349proposed. measured-source hash gắn phiên bản trước đổi namespace. Pilot lỗi CONTINUE khi INITIALIZING được giữ riêng và loại khỏi số liệu, không đổi thành success. Bộ06/10 v1 Decision5s/load3/5/10/20/44Game checks là evidence cũ, không dùng chứng minh phiên bản đa mode.

Tái lập bằng output mới, không ghi đè evidence:

```powershell
mysql -h127.0.0.1 -u root -p -e "source scripts/prepare-experiment-database.sql"
& ./scripts/run-experiment.ps1 -Multimode -Mode both -Loads @(3,4) -Rounds 3 -ReliabilityGames 10 -OutputDirectory experiments/results/my-new-run
python scripts/summarize-experiment.py --help
python scripts/plot-experiment.py --help
```

Đóng góp kỹ thuật có evidence: ordering ingress/timer, atomicStart/scoring/commit boundary, original-response replay, generation replacement, private snapshot/resync, dữ liệu v1/v2 và authoring7mode. Không gán contribution cá nhân khi chưa có tên/MSSV/commitPR/evidence phân công thật. Same-machine dùng chungCPU/RAM/networkstack; không suy ra Internet latency/LAN nhiều máy. Không rerun experiment trong lần dọn.

## 11. Checklist môn và gói bàn giao

Không thấy tài liệu môn gốc Topics/Instruction/Submission/mẫuREADME/Proposal/TechnicalDesignV2 trong project. Vì vậy chưa đối chiếu trực tiếp rubric, deadline, tênfile/format hoặc cách nộp USB/Drive; không khẳng định môn yêu cầu README riêng. PROJECT_REPORT.md đóng vai trò hướng dẫn ứng dụng/báo cáo kỹ thuật hiện có, chưa thay thế mẫu bắt buộc chưa được cung cấp.

| Yêu cầu dự kiến cần chứng minh | Hiện có | Còn cần |
|---|---|---|
| Client/Server, protocol, shared state | REST/rawWS/queue/Server scoring, contract tại mục5–8, integrationXML | Đối chiếu Topics/Instruction gốc |
| Concurrency/errors/timeout/duplicate/disconnect/DB | Unit/MySQL/rawWS race/retry/rollback/startup, browser smoke | Demo thực tế theo rubric |
| Novelty và experiment thật | Replay/reconnect baseline+proposed, rawCSV/hash ở mục10 | Kết luận theo phạm vi đo, không coi7mode tự đủ novelty |
| Cài/chạy/demo LAN | Config mẫu/wrapper/script + JAR/MySQL/browser local thật | 3–4máy Wi-Fi thật, máy mới build/run/restore backup |
| Nhóm và đóng góp | Chưa có danh sách/thực tế phân công được xác nhận | Tên/MSSV/lớp/nhóm, công việc từngngười/evidence |
| Báo cáo/README đúng mẫu, Compilatio/AI policy | Báo cáo tổng hợp này, chưa thấy mẫu/kết quả | Mẫu gốc, submissionformat, kiểm tra Compilatio thật và chính sách AI |

Gói cần giữ: PROJECT_REPORT.md; src(main/test/resources), pom.xml, Wrapper/.mvn, .gitattributes/.gitignore; config/application-local.properties.example; scripts chạy/build/test/SQL/experiment và fixture; media demo thật; DBdump riêng đã backup nếu bàn giao dữ liệu; experiments raw+hash+measured-source và docs/assets screenshots. Không nộp password/session/cookies.

Không đưa target/.cache/.idea/.vscode/.git, config/application-local.properties hoặc .env thật vào gói. Các thư mục này có thể vẫn tồn tại local; target chứa media test thì backup media riêng trước khi bỏ khỏi gói. .gitignore bỏ qua data media để tránh Git phình ra, KHÔNG có nghĩa media không cần cho demo: đóng gói/backup data riêng. Cache giúp buildoffline, không source bắt buộc.

Tài liệu phát triển cũ được thay thế sau kiểm tra tham chiếu; bản sao local trước dọn ở .cache/handoff-cleanup-before (không thuộc gói bàn giao). Các tài liệu results.md và historical-task15-evidence-index.md còn giữ vì checksum/provenance; không sửa chúng để làm đẹp. Artifact-audit.csv là inventory lịch sử, không số liệu benchmark. LogCSV thật được giữ kể cả baseline/pilot không thành công.

## 12. Phạm vi dọn bàn giao ngày 10/10/2026

Đã đọc nội dung và kiểm tra references trước khi gộp: 10tài liệu docs (guide, yêu cầu môn, schema, report, gameplay, decisions, status, REST, team, WS), README, TASKS/Overview, kế hoạch/khảo sát mở rộng và experiment guide/index. Source/pom/config/script/test không cần các tài liệu này khi chạy; collector Task15 là tham chiếu script duy nhất và đã bỏ vì chỉ còn lịch sử, có guard từ chối chạy. Không tìm thấy quy định môn bắt buộc giữ file riêng.

| Đã bỏ | Lý do / ảnh hưởng |
|---|---|
| README.md, TASKS.md, TASKS_MO_RONG_7_CHE_DO.md, KHAO_SAT_MO_RONG_7_CHE_DO.md | Hướng dẫn/nguồn luật/kế hoạch cũ được gộp; không còn nhiều điểm vào tài liệu. Không ảnh hưởng runtime |
| docs/*.md (10file) | Chuyển phần còn dùng vào báo cáo này; bỏ nhật ký dài, quyết định cũ đã thay thế và checklist lặp |
| experiments/experiment-protocol.md; handoff/artifact-audit.md; handoff/evidence-index.md | Hướng dẫn/index hiện tại được gộp; rawCSV, auditCSV, manifest và historical index vẫn giữ |
| 1.sql | Chỉ CREATE DATABASE quizz; scripts/prepare-database.sql là bản dùng thật với IF NOT EXISTS/charset |
| 1.txt | Chỉ số task16..23 + dòng trắng, không dữ liệu/không runtime reference; có backup local trước bỏ |
| scripts/collect-handoff-evidence.py | Collector riêng Task15 đã khóa không chạy để bảo vệ archive; evidence có sẵn/giữ nguyên |
| target/classes,test-classes,client,generated-*,maven-status,maven-archiver,surefire/failsafe-reports và JAR cũ/mới | Generated, tái tạo bằng build/verify; XML full regression đã lưu archive. Build cuối sẽ tạo lại output cần thiết, vẫn ignored |
| 47bản log/JSON/TSV target đã archive (15refactor +32Task15–25) | Hash giống bản trong experiments/handoff hoặc experiments/results; bỏ bản sao, không mất evidence |

Không xóa media/data/migration/source/test/Wrapper; không đổi gameplay/API/config hay dependency. Không xóa bừa thư mục target: media test, profileChrome và các log lịch sử chưa đủ căn cứ được để local, ignored và không đưa vào gói. .cache có offline dependency và backup công việc đang dở; .idea/.vscode là thiết lập IDE riêng, không nộp. Nếu muốn bỏ vật lý các thư mục đó sau này, phải backup media và xác minh không process dùng chúng.

Các reference cũ trong rawlog/hash/historical index giữ nguyên để chứng minh provenance; dùng archive-map/manifest trong experiments/handoff để tìm bản gốc. Đường dẫn runtime/script không phụ thuộc doc đã bỏ.

### Kết quả kiểm tra sau dọn

- `python scripts/build-client.py`: exit0,14module/16asset.
- Maven3.9.12 `verify -Pmysql-smoke -Dtest=CodebaseStructureTest,SystemWebTest -Dit.test=MySqlSmokeIT -Ddebug=false`: BUILD SUCCESS;7unit +2MySQL integration,0fail/error/skip. Compile lại180production/47test source, package JAR mới. Thực tế dùng Java Classworlds launcher từ Wrapper cache với `-B --no-transfer-progress -o -Dmaven.repo.local=D:/BTL_LTM/.cache/maven` (tương đương goal/options Wrapper, tránh tải lại); không giả đã gọi Wrapper qua mạng.
- `java -jar target/multigame-0.0.1-SNAPSHOT.jar --debug=false`, DBquizz_task25_handoff/MySQL8.0.45: readiness8087 HTTP200, applicationmultigame/ServerUP/DBUP; `/`, `/client/question-banks.js`, `/client/game.js`, `/client/history.js` đềuHTTP200. Server kiểm tra đã dừng.
- SQLread-only DBquizz: checksumFlywayV1–V3 và count các bảng giống byte-for-byte bằng chứng trước; không thay dữ liệu người dùng.
- Checksum496file cần giữ không đổi so với trước dọn (source/test/pom/Wrapper/configmẫu/media/scripts/evidence). Bốn file có mặt trong inventory bảo vệ sơ bộ nhưng thuộc danh sách xóa chủ đích được phân biệt riêng; không báo chúng như mất source.
- `git diff --check`: exit0. Không sửa source/config/migration/dependency/runtime behavior; công việc dirty có từ các task trước được bảo toàn. Backup tài liệu dirty local trước xóa không đưa vào gói.

Evidence lần dọn: [cleanup-2026-10-10](experiments/handoff/cleanup-2026-10-10/) gồm build/test log, XML, readiness/HTTP, SQLread-only, danh sách xóa và audit checksum. Full451test/browser flow/experiment đã đạt trên cùng source được tái sử dụng, không chạy lại trong lần dọn. Có warning deprecation/Mockito dynamic-agent của build hiện có, không khiến test fail; không đổi dependency trong task này. Chưa kiểm chứng LAN nhiều máy, restore trên máy mới, Compilatio hay đối chiếu tài liệu môn gốc.

## 13. Bộ lọc mới và bộ mẫu 6 chế độ (10/10/2026)

Bộ lọc ở danh sách bộ câu hỏi dùng dropdown mở khi rê chuột, đóng khi rời; vẫn hỗ trợ chạm, Tab/ArrowUp/ArrowDown/Home/End/Escape. Hai scope Của tôi/Chung giữ bộ lọc đang chọn. Chỉ sửa question-banks.js/styles.css; không đổi endpoint, quyền, gameplay hay migration.

Đã nhập qua REST thật vào DB quizz: 6bộ PUBLIC dưới tác giả demo riêng (User52), mỗi bộ5câu, tổng30câu. IDs17Vua Tiếng Việt,18Đố mẹo,19Truy tìm dấu vết,20Sắp xếp trình tự,21Đuổi hình bắt chữ,22Đoán tên bài hát. Không thêm bộQuiz/không sửa bộ cũ. Mọi User thấy ở Bộ câu hỏi→Chung; tác giả không chơi bộ tự tạo theo luật hiện có.

Fixtures ở data/demo-banks/questions.json +5PNG +5MP4 +song-provenance.json/checksum. Bộ hình là minh họa gợi thành ngữ, không chỉ nhận diện đồ vật. CLUES dùng mốc0/2000/4000ms, chọn thời gian câu trên4giây (nên20–30giây). SONG dùng video H264/AAC10.4–13.1giây có hình/âm thanh giai điệu nhạc cụ tổng hợp: Happy Birthday to You, Twinkle Twinkle Little Star, Frère Jacques (alias Kìa con bướm vàng), Jingle Bells, Ode to Joy. Không phải bản thu thương mại/có giọng ca; không hiện tên/đáp án trong clip. Aliases được chuẩn hóa theo Server.

Dữ liệu đã có trong MySQL, không cần chạy lại khi restart. Muốn nhập trên database mới, bật Server rồi chạy:

```powershell
python scripts/import-demo-banks.py --origin http://127.0.0.1:8080
```

Importer tạo tác giả riêng, upload media qua API và không ghi đè bộ đã có; chạy lại đã xác minh6bộ EXISTING_VERIFIED, không tạo trùng. Credential tác giả tự sinh nằm trong .env.demo-banks (ignored, không nộp); người chơi dùng account của mình. import-result.json là receipt theo database/máy, không fixture bất biến. Backup thêm data/demo-banks cùng hai kho media thực tế và MySQL. scripts/generate-demo-melodies.py chỉ để tái tạo MP4 khi cần, cần PyAV17.1/NumPy/Pillow, không là dependency chạy Server.

Kiểm tra: build-client.py14module/16asset; Maven package -DskipTests BUILD SUCCESS; không coi skipTests là đã testBackend. 89component frontend check đã đạt; test-catalogue-filter.py trên Chrome154/Server/MySQL thật đạt8nhóm: hover/leave/keyboard/chọnscope-mode,6bộ PUBLIC không lộ content cho nonowner,touch390px không overflow,playback cả5video và tải cả5ảnh choOwner. Không chạy lại Java fullregression vì Java/config/persistence không đổi. Readiness bản cuối HTTP200/DBUP; DB quizz hiệnV7 sau lần bạn chạy migration. Chưa test một trận30câu hoặc LAN nhiều máy cho các bộ mới, không suy ra từ smoke UI. Evidence ở experiments/handoff/catalogue-demo-2026-10-10/. Source/evidence trước mục này thuộc phiên bản/lần kiểm tra tương ứng, không thay số liệu benchmark cũ.
