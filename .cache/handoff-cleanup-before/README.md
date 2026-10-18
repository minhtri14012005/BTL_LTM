# MultiGame — Multiplayer LAN Game System

Ứng dụng thi đấu 7 chế độ trên **một Spring Boot Server + một MySQL**, REST/raw WebSocket, trình duyệt localhost/LAN. Server quyết định phase, deadline, Spin, điểm và ranking; v2 không loại Player, lịch sử v1 giữ elimination; Host là quyền trong Room.

Đã có Account, Quiz/ảnh, Room/Waiting, Game/Final, reconnect/replacement, Cancel và History UI nối backend thật. Task25 **PARTIAL**: kỹ thuật và kết quả cùng máy được ghi ở [status](docs/project-status.md); hồ sơ môn, LAN nhiều máy, máy mới và contribution cá nhân chưa đủ. Không Cloud/Redis/Kafka/failover hoặc phục hồi game đang chạy sau crash.

Đây là **README ứng dụng**, khác mẫu README môn. Project chưa có Topics/Topic2, Instruction, Submission, mẫu README môn gốc. [Báo cáo](docs/final-report.md) dùng cấu trúc dự án thông thường; chưa xác nhận định dạng/tên file/cách nộp theo môn. Luật v2: [task mở rộng](TASKS_MO_RONG_7_CHE_DO.md) và decisions28–35. Nguồn bảng Quiz/v1: Overview ở [TASKS.md](TASKS.md), [bản trích luật](docs/gameplay-rules.md), G-01/G-02 trong [decisions](docs/implementation-decisions.md). TASKS là task list chính.

## Đọc tài liệu nào?

| File | Vai trò |
|---|---|
| `README.md` (file này) | README chính: cài đặt, cấu hình, build/run/test và giới thiệu toàn project. |
| [Hướng dẫn thư mục code](docs/codebase-guide.md) | Ý nghĩa từng feature Backend, các thư mục con, Frontend, resources và test; điểm bắt đầu để học code. |
| [Protocol thực nghiệm](experiments/experiment-protocol.md) | Hướng dẫn và phương pháp đo baseline/proposed, metric, workload và tái lập. |
| [Mục lục evidence](experiments/handoff/evidence-index.md) | Giải thích bằng chứng build, MySQL, browser và thực nghiệm được lưu trong bộ bàn giao. |

Hai tài liệu thực nghiệm/evidence trước đây có tên `README.md` trong thư mục con; đổi tên theo nội dung để dễ phân biệt. Cả hai vẫn cần giữ để hiểu và bảo vệ kết quả, dù không cần khi chỉ chạy ứng dụng. README trong cache Maven thuộc công cụ/dependency, không phải hướng dẫn project.

## Install và build

Java **21**, MySQL **8.0.17+** (CHECK/JSON schema được enforce cho nhiều màn; đã đo8.0.45), PowerShell. Python3 cho build/test/experiment client; Chrome cho smoke browser. Maven Wrapper3.9.12 đi kèm. Frontend native ES modules, không Node/npm. Spring Boot3.5.16/dependency theo [pom.xml](pom.xml).

~~~powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.9' # thay bằng JDK21 của máy
$env:MAVEN_USER_HOME = Join-Path $PWD '.cache/maven-home'
.\mvnw.cmd -B --no-transfer-progress verify -Ddebug=false
python scripts/build-client.py
~~~

Verify mặc định chạy unit tests/build JAR, **không đồng nghĩa kiểm chứng MySQL**. JAR: `target/multigame-0.0.1-SNAPSHOT.jar`; client build: `target/client`. Dừng JAR của mình trước build lại trên Windows để tránh khóa artifact. Linux/macOS dùng `sh ./mvnw ...`.

## DB

Khởi động MySQL; tài khoản cài lần đầu cần quyền tạo database/migration. Lệnh hỏi password, không truyền password qua command line; nếu mysql chưa trong PATH, dùng đường dẫn binary cài trên máy.

~~~powershell
mysql --protocol=TCP -h 127.0.0.1 -P 3306 -u root -p -e 'source scripts/prepare-database.sql'
# Chỉ khi chạy integration tests/experiments:
mysql --protocol=TCP -h 127.0.0.1 -P 3306 -u root -p -e 'source scripts/prepare-test-database.sql'
mysql --protocol=TCP -h 127.0.0.1 -P 3306 -u root -p -e 'source scripts/prepare-experiment-database.sql'
~~~

CREATE DATABASE IF NOT EXISTS, không DROP. Runtime mặc định `quizz`; integration riêng `quizz_task2_test`; harness riêng `quizz_task15_experiment`. Mỗi Server chỉ dùng một DB. Không trỏ integration vào dữ liệu đang chơi.

Flyway V1/V2 tạo11 bảng domain, V3 cập nhật Decision của Room; V4/V5 thêm dữ liệu và kế hoạch nhiều màn, V6 aliases Vua Tiếng Việt, V7 cursor gợi ý CLUES. JPA ddl-auto=validate, không update/create; giữ Flyway bật khi cài mới/demo, không sửa checksum migration đã áp dụng. [Schema/ERD v1](docs/database-schema.md) và [DDL v1](src/main/resources/db/migration/V1__quiz_domain.sql) là nền lịch sử; cấu trúc mở rộng nằm trong V4–V7 ở src/main/resources/db/migration. Sao lưu MySQL cùng cả kho ảnh/video, gồm drafts. Integration dùng schema riêng; không tắt Flyway khi cài mới để bỏ qua V4–V7.

## Config, Server và Client

~~~powershell
Copy-Item config/application-local.properties.example config/application-local.properties
$env:DB_USER = 'root' # hoặc tài khoản MySQL hiện có, không phải tài khoản game
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.ps1 -PromptForPassword
~~~

Script hỏi password qua secure prompt vào biến môi trường process và khôi phục sau khi kết thúc. Hoặc điền credential local vào file được Git ignore; không đưa file ấy vào bộ nộp. Mẫu không chứa credential thật. Sau build có thể chạy `java -jar target/multigame-0.0.1-SNAPSHOT.jar --debug=false` với credential environment/local.

Mở `http://127.0.0.1:8080/`; Spring phục vụ UI cùng origin, không client server thứ hai. `/api/system/status` là liveness; `/api/system/ready` phải200/databaseUP để xác nhận MySQL/startup cleanup. Ready503/boot fail là chưa sẵn sàng. web-only chỉ kiểm tra HTTP không DB, không phải bản demo gameplay.

| Biến | Mặc định / ý nghĩa |
|---|---|
| DB_HOST / DB_PORT / DB_NAME | 127.0.0.1 / 3306 / quizz |
| DB_USER / DB_PASSWORD | quiz_app / rỗng; cấp credential local |
| SERVER_ADDRESS / SERVER_PORT | 127.0.0.1 / 8080 |
| ALLOWED_ORIGINS | http://localhost:8080,http://127.0.0.1:8080; allowlist chính xác, không wildcard |
| QUIZ_IMAGE_DIRECTORY | ./data/quiz-images; giữ directory khi restart |
| QUIZ_VIDEO_DIRECTORY | ./data/quiz-videos; SONG MP4 immutable, lưu cùng DB và ảnh |
| QUIZ_DEMO_PASSWORD | Chỉ seed opt-in mysql,sample-data; không password demo mặc định |

Seed tùy chọn: cấp QUIZ_DEMO_PASSWORD, chạy JAR `--spring.profiles.active=mysql,sample-data`; tạo demo_author, demo_player1..3, Quiz PUBLIC10 câu của Author. Seed không reset password/ghi đè dữ liệu đã sửa; conflict làm startup fail. Cũng có thể Register/tạo Quiz trên UI; không thao tác DB thủ công để hoàn thành trận.

### LAN

Máy Server bind SERVER_ADDRESS=0.0.0.0, ALLOWED_ORIGINS=http://<IP-LAN-Server>:8080 (thêm localhost nếu dùng). Client mở đúng origin. Chỉ TCP8080 cần phục vụ Client LAN; Client không truy cập MySQL. Không tắt Origin check hoặc bật baseline trong demo. [Protocol LAN/máy mới](experiments/experiment-protocol.md#lan-và-máy-mới) có kịch bản và trường evidence. Hiện chưa chạy nhiều máy thật.

### Chạy bằng nút Run trong IntelliJ và đổi mạng Wi-Fi

Chọn main class `vn.edu.multigame.MultigameApplication`, JDK21, working directory là thư mục project (ví dụ `D:\BTL_LTM`). Profile mặc định là `mysql`; profile này đọc `config/application-local.properties`. Không chọn `ExperimentServer` để chạy ứng dụng bình thường. Biến môi trường đã đặt ở một cửa sổ PowerShell khác không tự truyền vào IntelliJ.

Có thể giữ cấu hình LAN trong file local, cùng credential DB hiện có:

```properties
SERVER_ADDRESS=0.0.0.0
ALLOWED_ORIGINS=http://192.168.1.234:8080,http://localhost:8080,http://127.0.0.1:8080
```

IP trên chỉ là ví dụ máy Server; dùng IPv4 Wi-Fi thực tế từ `ipconfig`. Khi chuyển Wi-Fi và IP thay đổi, giữ `SERVER_ADDRESS=0.0.0.0`, thay origin IP cũ trong `ALLOWED_ORIGINS` bằng IP mới rồi Stop/Run Server. Client cùng mạng mở `http://<IP-mới>:8080/`; không dùng `0.0.0.0` làm URL. Các override Environment variables/VM options/Program arguments trong IntelliJ phải không mâu thuẫn với file local. Không cần đổi DB_HOST nếu MySQL vẫn chạy trên cùng máy Server.

Kiểm tra theo thứ tự: console có `Started MultigameApplication` và port8080; trên máy Server mở `http://localhost:8080/api/system/ready` để xác nhận MySQL UP; sau đó mở URL IP LAN có `:8080`. `ERR_CONNECTION_REFUSED` có thể do Server chưa chạy, sai IP/port hoặc bind localhost; origin sai thường gây lỗi HTTP/WS sau khi đã kết nối. Nếu máy Server truy cập IP LAN được nhưng thiết bị khác không vào được, kiểm tra Windows Firewall cho TCP8080 trên mạng Private và Wi-Fi có cô lập Client không. Không tắt firewall hoặc mở port MySQL cho Client.

## Feature ownership và entry points

Tên chung **MultiGame**; Maven artifact `multigame`, một Maven module. `questionbank` quản lý bộ câu hỏi cả bảy chế độ; `quiz` chỉ giữ phần riêng trắc nghiệm. Entity Quiz/Question, bảng `quiz`/`question`, API `/api/quizzes`, khóa `quiz.*` và thư mục media giữ tương thích. Sau pull/refactor, Reload Maven trong IntelliJ và chọn lại main mới; kết quả thực nghiệm đã lưu vẫn ghi tên phiên bản được đo.

Base `vn.edu.multigame`; [MultigameApplication](src/main/java/vn/edu/multigame/MultigameApplication.java) ở root scan toàn feature. Entity/Repository/enum ở feature sở hữu; không global business layer hoặc Account Entity trùng User.

| Feature | Trách nhiệm / entry point |
|---|---|
| user | UserAccount/UserRepository; không global HOST role |
| auth | AuthController→AuthService; SecurityConfiguration, AuthSessionRegistry/AuthPrincipal; BCrypt/session/CSRF, permission |
| questionbank | QuestionBankController/QuestionBankService/QuestionBankAccessPolicy; PUBLIC/PRIVATE/author, bộ câu7 mode, aliases/arrangements/timeline, ảnh/video local immutable; DTO owner khác metadata |
| quiz | Option A/B/C/D riêng chế độ Quiz; điểm/effect vẫn ở game/engine |
| room | RoomController/RoomService; membership/config/revision; orchestration RoomOperations/RoomBoundary ở realtime/session |
| game/engine | ScoreEngine luật v1, MultimodeScoreEngine v2 dùng lại bảng Quiz; TextAnswerEvaluator/ArrangementEvaluator, ranking thuần; không clock/random/DB/WS |
| game/service | GameLifecycle phase/retry, GameTransactions transaction, GameHistoryService/StartupCleanup; snapshot ở game/dto |
| realtime | WaitingRoomWebSocketHandler/GameCommandAdapter/Parser; connection/generation; GameRuntime/SessionQueue/RoomBoundary; timer/ServerClock; replay/fingerprint; message command/event/common |
| system/common | Readiness/config và tiện ích nhỏ; không engine/business dùng chung |
| static/client | app.js/router; api/core/dom/transport; account/quizzes/rooms/game-state/game/history; textContent, revision/receipt guards |

Canonical [REST](docs/rest-api-contract.md) và [WS](docs/websocket-contract.md) quy định field/type/nullability/permission/error thật. REST quản lý Account/Quiz/Room/config/Start/snapshot/Cancel/History; /ws Waiting+gameplay dùng cookie HttpOnly/mandatory Origin, cùng envelope. Không JWT/query token/localStorage identity. Answer ACK không correctness. Non-owner PUBLIC chỉ metadata; Owner chọn PRIVATE, người chơi tham gia Room qua code theo policy đã chốt.

### Trace để học và bảo vệ

`transport.js` giữ UUID/frame khi retry → /ws Principal/current generation → GameCommandParser → GameCommandAdapter → GameRuntime.command → **ingress timestamp/sequence/enqueue nguyên tử** → SessionQueue tuần tự/Game → replay User+Game trước phase validation → GameLifecycle → GameTransactions (ScoreEngine v1 hoặc MultimodeScoreEngine v2 thuần khi scoring) → **proxy DB commit** → copied runtime/receipt → GameLifecycleEvent/AuthenticatedSocketRegistry → recipient message → game-state revision guard → game.js/History.

Start dùng RoomBoundary trước Game ID; revalidate HTTP session sau chờ lock; transaction snapshot roster/config/UAG rồi handoff actor sau commit. UAG gồm Spectator, Start chéo Room không cùng thắng. Answer hợp lệ receivedAt<deadline, timestamp Client không dùng. Timer cùng ingress, sai question/phase/token/generation no-op. Offline vẫn được tính NO_ANSWER. Chấm cả câu một transaction, rollback không partial runtime/cache ACCEPT. Retry tối đa3 lần100/300ms; terminal error khác durable success, restore DB/restart hoàn tất pending cleanup.

Replay giữ10 phút sau FINISHED, không bảo đảm qua restart. Socket mới thay cũ/4002, queued command cũ bị fence theo generation, không reset PlayerSession. Host eliminated/Spectator vẫn Cancel; CANCELLED/SERVER_INTERRUPTED không official Winner. V1 End thường ưu tiên ALL_ELIMINATED/ONE_SURVIVOR trước COMPLETED. V2 không loại người chơi, End thường COMPLETED sau câu cuối; tất cả rank1 là co-Winner theo xác nhận người dùng. [Báo cáo](docs/final-report.md) có sơ đồ/transaction/luật.

## Test và smoke thật

~~~powershell
# Full suite với MySQL/schema test đã chuẩn bị; không H2
.\mvnw.cmd -B --no-transfer-progress verify -Pmysql-smoke -Ddebug=false
# Server mysql/JAR đang ready; Chrome+Python
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-client.ps1 -Gameplay -ReportName task15-client-tests.json
~~~

JUnit XML ở target/surefire-reports, target/failsafe-reports. Browser dùng4 Chrome contexts cookie riêng, HTTP/rawWS/MySQL thật. Smoke Register/Login/Logout→Quiz/ảnh→Waiting→Host Participate3Player hoàn thành10 câu (Spin/Star/Answer/NO_ANSWER offline/lost ACK retry/reconnect)→Final/History; các trận riêng Host Spectator3Player Cancel, Host eliminated Cancel, replacement/auth expiry. Không mockAPI hoặc coi Waiting thành công là đã chơi hết trận.

Evidence lịch sử: Task13 full **224 unit+92 integration**; Task14 diff cuối **47 unit+77 integration**,0 fail/error/skip. Task15 là evidence lịch sử 06/10; kết quả bản 7 chế độ ở [status](docs/project-status.md), [handoff evidence](experiments/handoff/evidence-index.md). Không cộng bộ có test trùng thành số mới. Race tests dùng clock/latch/barrier, load không thay thế chứng minh race.

## Experiment, novelty, limitations (phân biệt run cũ và mới)

~~~powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run-experiment.ps1 -OutputDirectory experiments/results/my-new-run
python scripts/summarize-experiment.py experiments/results/my-new-run
~~~

[Protocol](experiments/experiment-protocol.md) định nghĩa metric/baseline/denominator. Harness test classpath, từng mode một Server/DB; baseline xóa receipt trong observer test, reconnect Room subscription không full Game snapshot. Giữ guards1 Spin/q/Answer/q, transaction/clock thật. Entry point test không nằm trong production JAR. Mỗi Client Account/cookie riêng;3/5/10/20 là kế hoạch task, không ngưỡng thầy đã xác nhận. [Results/CSV](experiments/results/2026-10-06-loopback/results.md) là loopback, không suy ra Wi-Fi/Internet latency.

Đóng góp cấp project: replay response gốc sau mất ACK, snapshot cá nhân/revision/generation, ingress thống nhất timer/command, finite failure policy và runtime/cache/broadcast sau commit. Đây là đóng góp tích hợp, không tuyên bố thuật toán nghiên cứu mới. [Phân công dự kiến/contribution](docs/team-contributions.md) tách riêng; không gán tác giả theo account/máy/Git.

Giới hạn: một Server/DB, replay RAM/TTL hữu hạn, không failover/crash recovery, ảnh cần filesystem bền; CPU/RAM/network stack chung và instrumentation/log ảnh hưởng số đo. Chưa LAN nhiều máy/máy mới/outage toàn dịch vụ giữa COMMIT bất định; chưa Compilatio/tài liệu Submission. [Checklist](docs/course-requirements-checklist.md) ghi phần còn thiếu.

## Nhóm và bộ review

Nhóm4 người, ô trống để điền sau. Phân công dự kiến không phải contribution thực tế.

| Nhãn | Họ tên | MSSV | Lớp |
|---|---|---|---|
| Thành viên 1 | | | |
| Thành viên 2 | | | |
| Thành viên 3 | | | |
| Thành viên 4 | | | |

Bộ review: README ứng dụng, final-report.md, team-contributions.md, schema/ERD, contracts/rules/decisions, CSV/log/config/hash và handoff evidence. Các tên file là lựa chọn project, chưa phải tên nộp bắt buộc môn. Không đóng gói credential local, .cache, target browser profile hoặc dữ liệu cá nhân. Còn Compilatio, thông tin nhóm, xác nhận phân công/contribution, máy mới/LAN và Instruction/Submission.

## References

- [Nguồn Overview](TASKS.md), [gameplay](docs/gameplay-rules.md), [decisions](docs/implementation-decisions.md), [REST](docs/rest-api-contract.md), [WS](docs/websocket-contract.md).
- [Spring Boot3.5 reference](https://docs.spring.io/spring-boot/3.5/reference/index.html), [Spring Security servlet authentication](https://docs.spring.io/spring-security/reference/servlet/authentication/index.html), [Spring WebSocket](https://docs.spring.io/spring-framework/reference/web/websocket.html).
- [MySQL8 CHECK](https://dev.mysql.com/doc/refman/8.0/en/create-table-check-constraints.html), [Java21 nanoTime](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/System.html#nanoTime()), [WHATWG WebSockets](https://websockets.spec.whatwg.org/).
- Tài liệu nền tảng không thay chứng cứ implementation/tests hoặc yêu cầu môn chưa có.

## Cập nhật gameplay 07/10/2026

Trận mới: chọn Spin/Hope Star/Chơi thường trong7giây → Answer theo cấu hình Room → RESULT chung1,5giây → tự sang câu sau/Final. ACK không đúng/sai; khi chấm, đúng xanh/sai đã chọn đỏ, toast delta riêng. Leaderboard bên phải có bật/tắt, thời gian hiển thị giây; backend ms và công thức điểm/ranking giữ nguyên. Waiting ẩn Revision nhưng dữ liệu đồng bộ vẫn giữ.

Khởi động với Flyway bật để V3 đổi default/Room DRAFT/WAITING cũ thành7000ms; không reset DB hoặc sửa snapshot Game ACTIVE/FINISHED. Room quay về WAITING/Start mới được chuẩn hóa7000ms. Không sửa V1/V2 đã áp dụng. Evidence/benchmark06/10/2026 giữ nguyên cho cấu hình5000ms; kiểm thử/ảnh bản mới xem docs/project-status.md.

## Trận nhiều màn — bảy chế độ (Task24)

Ứng dụng dùng cùng một Server/DB và UI, không cài frontend thứ hai. Register ít nhất3 tài khoản Player và một Author khác; Author tạo các bộ bằng editor: Quiz4 lựa chọn, SONG clip có hình/tiếng, Vua Tiếng Việt mảnh chữ, RIDDLE đáp án chữ, CLUES timeline gợi ý, IMAGE_WORD hình gợi từ/cụm từ, ORDERING toàn bộ thứ tự. Mine có own PUBLIC/PRIVATE; Shared có mọi PUBLIC. Người khác chỉ xem metadata của bộ PUBLIC, không lấy nội dung/đáp án nguồn. Nếu Host là tác giả bất kỳ bộ đã chọn thì chọn Spectate; cần3 Player khác. Host có thể chọn PRIVATE của mình, người được mời bằng Room code chơi được nhưng không đọc bộ gốc.

Create Room chọn1–7 mode khác nhau, sắp thứ tự, một bộ đúng mode/count/duration cho mỗi màn; tổng1–50 câu. Không cần10 câu nếu Room dùng stages; Room legacy v1 vẫn Quiz10–50 và luật cũ. Chọn N câu đầu của mỗi bộ theo thứ tự biên soạn. Offset CLUES phải tăng và nhỏ hơn duration của màn; sửa nguồn sau Start không đổi snapshot. Xem đủ request/validation ở [REST](docs/rest-api-contract.md), message/revision ở [WS](docs/websocket-contract.md).

Open→các Player Join→Start→Intro chung10s (đủ Player Tiếp tục thì mở sớm; Spectator không giữ ready)→Quiz Decision7s hoặc non-Quiz mở câu ngay→Answer→Result chung1,5s→Server tự chuyển câu/màn→Final→History. Client chỉ hiển thị giờ Server, không tự đóng/chấm hoặc có nút Next. CLUES chỉ hiển thị gợi ý Server đã mở. SONG phát theo tiến độ câu, bật tiếng/phát bằng click nếu autoplay bị chặn. Ảnh/video lỗi vẫn cho đoán đến đủ Answer/hết giờ; không đổi deadline. [Cấu hình video/backup bên dưới](#video-đoán-bài-hát-task22).

V2 initial0/floor0/không elimination. Chỉ Quiz dùng Spin/Star/streak/Momentum/Recovery và bảng Quiz v1; Spin=floor(tổng câu Quiz/10), Star1 nếu có Quiz, khôngQuiz cả hai0. Streak/effect giữ qua non-Quiz, chỉ dùng trong Quiz. Sáu mode khác đúng+10, đúng câu cuối mỗi màn+20, sai/bỏ0; Quiz cuối không double. Ranking score giảm rồi tổng thời gian **chỉ Answer đúng** tăng; bằng cả hai đồng hạng. CANCELLED/SERVER_INTERRUPTED chỉ standings, không Official Winner. V1 History vẫn điểm âm/elimination/ranking cũ; không tự đổi version.

Disconnect không tạm dừng: trở lại nhận phase/câu/gợi ý/media/deadline hiện tại, không thêm thời gian. Online lúc mở câu rồi offline vẫn giữ gate câu đó; offline lúc câu kế mở không giữ điều kiện đóng sớm nhưng vẫn có thể reconnect và Answer trước hạn; wait-set rỗng đợi deadline. Một socket/User; tab mới thay tab cũ, tab cũ không auto-reconnect. Retry giữ nguyên UUID/frame và trả ACK đã commit; cache giữ10 phút sau End, không qua restart. Startup đánh dấu ACTIVE bỏ lại SERVER_INTERRUPTED, không resume trận.

Kiểm thử đầy đủ bằng schema test riêng và fixture Chrome/CDP như phần video (Server8087, CDP9224). Fixture ảnh/video là dữ liệu tổng hợp để kiểm pipeline, không phải bộ nội dung giải đố dùng cho demo môn:

~~~powershell
.\mvnw.cmd -B --no-transfer-progress verify -Pmysql-smoke -Ddebug=false
python -B scripts/build-client.py
python -B scripts/test-seven-modes-client.py --origin http://127.0.0.1:8087 --port 9224 --smoke --output-prefix seven-mode-new-run
~~~

Chạy Maven xong mới chạy Server/browser smoke trên cùng schema test vì startup integration có cleanup ACTIVE. Harness tạo4 Account/cookie contexts thật,7 bộ, trận16 câu đủ7 mode, trận1 mode, trận khôngQuiz và Cancel; giữ History/evidence ở target/task24-client-tests.json. MySQL controlled-clock test kiểm50 câu/150 Answer/ranking đồng hạng. Đây là same-machine; không suy ra đã demo3–4 máy Wi-Fi. Muốn kiểm LAN: cấu hình bind/origin như mục LAN, mỗi máy cùng Wi-Fi mở URL IPv4 Server:8080, dùng Account khác, ghi máy/IP/phase/reconnect và đối chiếu Final/History; không cho Client truy cập MySQL. Tài liệu môn/benchmark lịch sử chưa chứng minh bản 7 mode mới.

## Video Đoán bài hát (Task22)

Người tạo tự cắt clip có hình và tiếng, chọn bộ SONG trong editor, upload và nhập tên/aliases. Không phải cài FFmpeg/ffprobe để chạy ứng dụng: Server kiểm metadata bằng JDK, không cắt/transcode. Chỉ nhận MP4 self-contained không phân mảnh: một video H.264 avc1 (Baseline/Main/High) + một audio AAC-LC (mono/stereo); tối đa20MiB,120 giây, cạnh≤1920/tổng≤2073600pixel. Multipart request tối đa21MiB; ảnh vẫn2MiB. Đổi đuôi sang .mp4 không chuyển codec. WebM/MOV/HEVC/DRM/clip im lặng bị từ chối; metadata hợp lệ không đảm bảo compressed samples phát được trên mọi browser. Đã kiểm chứng chính xác fixture H.264 Baseline/yuv420p320×180/10fps + AAC-LC48kHz mono,25s bằng Chrome154; các profile/container/device khác chỉ là giới hạn parser, chưa là playback evidence.

QUIZ_VIDEO_DIRECTORY mặc định ./data/quiz-videos, có thể đặt đường dẫn tuyệt đối trong environment hoặc config local (như ảnh). Không đặt vào static/public. NTFS/filesystem phải hỗ trợ hard link; upload SHA-256 không ghi đè, drafts theo tài khoản, gắn vào Quiz khi lưu. Không xóa/thay file cũ khi sửa/xóa bộ: trận/History giữ ref đó. Hash không khôi phục bytes đã mất.

Backup: tạm dừng Server để không có upload/transaction đang ghi; sao lưu MySQL bằng mysqldump và **cả** QUIZ_IMAGE_DIRECTORY + QUIZ_VIDEO_DIRECTORY (gồm drafts) trong cùng bộ backup. Khôi phục DB/files tương ứng, đặt lại hai environment paths rồi chạy Server. Không nộp credential local. Startup kết thúc ACTIVE bỏ lại bằng SERVER_INTERRUPTED, không tiếp tục trận sau crash. Không có auto-clean media/orphan; filesystem publish không cùng transaction DB nên rollback có thể giữ blob chưa dùng.

Browser cùng origin tự dùng cookie để GET video có quyền; Server hỗ trợ Range/seek. Khi câu mở, Player xem/nghe và nhập tên, không dùng Spin/Star; video về giữa câu seek tới Server openedAtMs đã trôi, không thêm thời gian. Mặc định phát tắt tiếng: bấm Bật tiếng / Phát; nếu browser chặn âm thanh, nút vẫn cho phát bằng click thật. Clip ngắn hết trước deadline vẫn được đoán. 404 thiếu file hoặc503 hash/IO/decode lỗi hiện thông báo nhưng deadline/Answer tiếp tục; không tự Skip/End. History được xem lại clip của câu đã mở, source PRIVATE và video tương lai vẫn theo quyền [REST](docs/rest-api-contract.md)/[WS](docs/websocket-contract.md).

Kiểm tra trên schema test đã chuẩn bị, credential môi trường như phần Test:

~~~powershell
.\mvnw.cmd -B --no-transfer-progress test-compile failsafe:integration-test failsafe:verify -Pmysql-smoke '-Dit.test=SongLifecycleIT,QuestionBankNetworkIT'
python scripts/build-client.py
python -B scripts/test-song-client.py --origin http://127.0.0.1:8087 --port 9224 --smoke
~~~

Trước lệnh smoke, mở Terminal1 để chạy Server test thật (credential local như phần Config):

~~~powershell
$env:DB_NAME='quizz_task2_test'
$env:SERVER_PORT='8087'
$env:SERVER_ADDRESS='127.0.0.1'
$env:ALLOWED_ORIGINS='http://127.0.0.1:8087,http://localhost:8087'
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.ps1
~~~

Terminal2 mở Chrome fixture riêng; thay đường Chrome nếu máy khác:

~~~powershell
$task22ChromeProfile=Join-Path $PWD ('target/song-smoke-'+[guid]::NewGuid().ToString())
$task22ChromeArgs=@('--headless=new','--disable-gpu','--no-sandbox','--disable-software-rasterizer','--no-first-run','--no-default-browser-check','--disable-features=PreloadMediaEngagementData,MediaEngagementBypassAutoplayPolicies','--remote-debugging-address=127.0.0.1','--remote-debugging-port=9224',('--user-data-dir="'+$task22ChromeProfile+'"'),'about:blank')
$task22Chrome=Start-Process -FilePath 'C:\Program Files\Google\Chrome\Application\chrome.exe' -ArgumentList $task22ChromeArgs -WindowStyle Hidden -PassThru
python -B scripts/test-song-client.py --origin http://127.0.0.1:8087 --port 9224 --smoke
Stop-Process -Id $task22Chrome.Id -ErrorAction SilentlyContinue
~~~

Chrome profile và --no-sandbox chỉ dành cho fixture localhost trong môi trường test này, không dùng profile Chrome cá nhân; không chạy smoke trên DB người dùng. Dừng Server test khi xong và trả DB_NAME/port/origin về cấu hình demo. Mỗi lần smoke tạo4 Account riêng và giữ Game/History làm evidence. Ba MP4 test trong src/test/resources/quiz là video màu/tone tự tạo, không nhạc người dùng. Chỉ khi muốn tạo lại fixture cần PyAV17.1.0 + NumPy và chạy python scripts/generate-song-fixture.py; application/build/integration sử dụng fixture có sẵn, không cần hai package Python đó. Baseline/các số liệu thực nghiệm cũ giữ gắn đúng phiên bản đã đo, không chứng minh hiệu năng video/LAN mới.

## Bàn giao Task25 — bản 7 chế độ

[Báo cáo đầy đủ](docs/final-report.md), [checklist môn](docs/course-requirements-checklist.md), [phân công/actual contribution trống](docs/team-contributions.md), [evidence index](experiments/handoff/evidence-index.md) và [artifact audit](experiments/handoff/artifact-audit.md). Không thấy tài liệu Topics/Instruction/Submission/README mẫu môn gốc; chưa xác nhận format/tên/kênh/deadline. Chưa Compilatio/LAN nhiều máy/build máy mới. Root README là hướng dẫn ứng dụng.

Thực nghiệm mới: [raw/results v2 10/10](experiments/results/2026-10-10-multimode-loopback/results.md), replay/full snapshot vs baseline test-only. Tải thực tế3/4Player,3round/mức,30trial/scenario/mode, cùng máy/một Roomconcurrent; benchmark chỉ câuQuiz đầu của planQuiz→Riddle rồiCancel. Không tự gọi đây là benchmark đủ 7 mode/WiFi. [06/10 Quiz5s](experiments/results/2026-10-06-loopback/results.md) là phiên bản cũ, nguyên provenance, không gộp số liệu.

~~~powershell
# Từ PowerShell ở root, credential DB qua environment/file local ignored.
powershell -NoProfile -ExecutionPolicy Bypass -Command "& './scripts/run-experiment.ps1' -Multimode -Loads @(3,4) -Rounds 3 -ReliabilityGames 10 -Port 8086 -OutputDirectory experiments/results/new-v2-run"
# Hoặc gọi script trực tiếp với -Loads @(3,4).
python -B scripts/summarize-experiment.py experiments/results/new-v2-run
# Smoke từ JAR đãready, Chrome kiểmthửCDP9224/profile riêng; output mới tránh overwrite Task24.
python -B scripts/test-seven-modes-client.py --smoke --task 25 --output-prefix my-handoff --origin http://127.0.0.1:8087 --port 9224
~~~

Server8087 là portsmoke, thayorigin/CDP khi chạy máy khác. Không mở cổngCDP choLAN. Demo mặc định8080 dùng MultigameApplication/JAR, không ExperimentServer. Python/Chrome dùng test, không dependency runtime của người chơi. Backup MySQL cùng QUIZ_IMAGE_DIRECTORY/QUIZ_VIDEO_DIRECTORY, gồm drafts; không xóa media theo tuổi/tên/hash chỉ vì file không thuộcGit. Trước mvn clean giữ raw evidence trong experiments/handoff/results, không coi toàn bộtarget làrác.
