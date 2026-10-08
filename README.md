# Competitive Multiplayer Network Quiz System

Ứng dụng quiz thi đấu trên **một Spring Boot Server + một MySQL**, REST/raw WebSocket, trình duyệt localhost/LAN. Server quyết định phase, deadline, Spin, điểm, elimination và ranking; Host là quyền trong Room.

Đã có Account, Quiz/ảnh, Room/Waiting, Game/Final, reconnect/replacement, Cancel và History UI nối backend thật. Task15 **PARTIAL**: kỹ thuật và kết quả cùng máy được ghi ở [status](docs/project-status.md); hồ sơ môn, LAN nhiều máy, máy mới và contribution cá nhân chưa đủ. Không Cloud/Redis/Kafka/failover hoặc phục hồi game đang chạy sau crash.

Đây là **README ứng dụng**, khác mẫu README môn. Project chưa có Topics/Topic2, Instruction, Submission, mẫu README môn gốc. [Báo cáo](docs/final-report.md) dùng cấu trúc dự án thông thường; chưa xác nhận định dạng/tên file/cách nộp theo môn. Gameplay chuẩn: Overview ở [TASKS.md](TASKS.md), [bản trích luật](docs/gameplay-rules.md), G-01/G-02 trong [decisions](docs/implementation-decisions.md). TASKS là task list chính.

## Đọc tài liệu nào?

| File | Vai trò |
|---|---|
| `README.md` (file này) | README chính: cài đặt, cấu hình, build/run/test và giới thiệu toàn project. |
| [Hướng dẫn thư mục code](docs/codebase-guide.md) | Ý nghĩa từng feature Backend, các thư mục con, Frontend, resources và test; điểm bắt đầu để học code. |
| [Protocol thực nghiệm](experiments/experiment-protocol.md) | Hướng dẫn và phương pháp đo baseline/proposed, metric, workload và tái lập. |
| [Mục lục evidence](experiments/handoff/evidence-index.md) | Giải thích bằng chứng build, MySQL, browser và thực nghiệm được lưu trong bộ bàn giao. |

Hai tài liệu thực nghiệm/evidence trước đây có tên `README.md` trong thư mục con; đổi tên theo nội dung để dễ phân biệt. Cả hai vẫn cần giữ để hiểu và bảo vệ kết quả, dù không cần khi chỉ chạy ứng dụng. README trong cache Maven thuộc công cụ/dependency, không phải hướng dẫn project.

## Install và build

Java **21**, MySQL **8.0.16+** (CHECK được enforce; đã đo8.0.45), PowerShell. Python3 cho build/test/experiment client; Chrome cho smoke browser. Maven Wrapper3.9.12 đi kèm. Frontend native ES modules, không Node/npm. Spring Boot3.5.16/dependency theo [pom.xml](pom.xml).

~~~powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.9' # thay bằng JDK21 của máy
$env:MAVEN_USER_HOME = Join-Path $PWD '.cache/maven-home'
.\mvnw.cmd -B --no-transfer-progress verify -Ddebug=false
python scripts/build-client.py
~~~

Verify mặc định chạy unit tests/build JAR, **không đồng nghĩa kiểm chứng MySQL**. JAR: `target/competitive-quiz-0.0.1-SNAPSHOT.jar`; client build: `target/client`. Dừng JAR của mình trước build lại trên Windows để tránh khóa artifact. Linux/macOS dùng `sh ./mvnw ...`.

## DB

Khởi động MySQL; tài khoản cài lần đầu cần quyền tạo database/migration. Lệnh hỏi password, không truyền password qua command line; nếu mysql chưa trong PATH, dùng đường dẫn binary cài trên máy.

~~~powershell
mysql --protocol=TCP -h 127.0.0.1 -P 3306 -u root -p -e 'source scripts/prepare-database.sql'
# Chỉ khi chạy integration tests/experiments:
mysql --protocol=TCP -h 127.0.0.1 -P 3306 -u root -p -e 'source scripts/prepare-test-database.sql'
mysql --protocol=TCP -h 127.0.0.1 -P 3306 -u root -p -e 'source scripts/prepare-experiment-database.sql'
~~~

CREATE DATABASE IF NOT EXISTS, không DROP. Runtime mặc định `quizz`; integration riêng `quizz_task2_test`; harness riêng `quizz_task15_experiment`. Mỗi Server chỉ dùng một DB. Không trỏ integration vào dữ liệu đang chơi.

Flyway V1/V2 tạo11 bảng domain; V3 cập nhật Decision của Room; JPA ddl-auto=validate, không update/create. Giữ Flyway bật khi cài mới/demo. Schema integration đã chuẩn bị dùng `-Dspring.flyway.enabled=false` vì schema tests tự tạo DDL; không dùng cờ này để bỏ migration cài mới. [Schema/ERD](docs/database-schema.md) khớp [DDL](src/main/resources/db/migration/V1__quiz_domain.sql). Sao lưu MySQL và thư mục ảnh cùng bộ dữ liệu.

## Config, Server và Client

~~~powershell
Copy-Item config/application-local.properties.example config/application-local.properties
$env:DB_USER = 'root' # hoặc tài khoản MySQL hiện có, không phải tài khoản game
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.ps1 -PromptForPassword
~~~

Script hỏi password qua secure prompt vào biến môi trường process và khôi phục sau khi kết thúc. Hoặc điền credential local vào file được Git ignore; không đưa file ấy vào bộ nộp. Mẫu không chứa credential thật. Sau build có thể chạy `java -jar target/competitive-quiz-0.0.1-SNAPSHOT.jar --debug=false` với credential environment/local.

Mở `http://127.0.0.1:8080/`; Spring phục vụ UI cùng origin, không client server thứ hai. `/api/system/status` là liveness; `/api/system/ready` phải200/databaseUP để xác nhận MySQL/startup cleanup. Ready503/boot fail là chưa sẵn sàng. web-only chỉ kiểm tra HTTP không DB, không phải bản demo gameplay.

| Biến | Mặc định / ý nghĩa |
|---|---|
| DB_HOST / DB_PORT / DB_NAME | 127.0.0.1 / 3306 / quizz |
| DB_USER / DB_PASSWORD | quiz_app / rỗng; cấp credential local |
| SERVER_ADDRESS / SERVER_PORT | 127.0.0.1 / 8080 |
| ALLOWED_ORIGINS | http://localhost:8080,http://127.0.0.1:8080; allowlist chính xác, không wildcard |
| QUIZ_IMAGE_DIRECTORY | ./data/quiz-images; giữ directory khi restart |
| QUIZ_DEMO_PASSWORD | Chỉ seed opt-in mysql,sample-data; không password demo mặc định |

Seed tùy chọn: cấp QUIZ_DEMO_PASSWORD, chạy JAR `--spring.profiles.active=mysql,sample-data`; tạo demo_author, demo_player1..3, Quiz PUBLIC10 câu của Author. Seed không reset password/ghi đè dữ liệu đã sửa; conflict làm startup fail. Cũng có thể Register/tạo Quiz trên UI; không thao tác DB thủ công để hoàn thành trận.

### LAN

Máy Server bind SERVER_ADDRESS=0.0.0.0, ALLOWED_ORIGINS=http://<IP-LAN-Server>:8080 (thêm localhost nếu dùng). Client mở đúng origin. Chỉ TCP8080 cần phục vụ Client LAN; Client không truy cập MySQL. Không tắt Origin check hoặc bật baseline trong demo. [Protocol LAN/máy mới](experiments/experiment-protocol.md#lan-và-máy-mới) có kịch bản và trường evidence. Hiện chưa chạy nhiều máy thật.

### Chạy bằng nút Run trong IntelliJ và đổi mạng Wi-Fi

Chọn main class `vn.edu.quiz.QuizApplication`, JDK21, working directory là thư mục project (ví dụ `D:\BTL_LTM`). Profile mặc định là `mysql`; profile này đọc `config/application-local.properties`. Không chọn `ExperimentServer` để chạy ứng dụng bình thường. Biến môi trường đã đặt ở một cửa sổ PowerShell khác không tự truyền vào IntelliJ.

Có thể giữ cấu hình LAN trong file local, cùng credential DB hiện có:

```properties
SERVER_ADDRESS=0.0.0.0
ALLOWED_ORIGINS=http://192.168.1.234:8080,http://localhost:8080,http://127.0.0.1:8080
```

IP trên chỉ là ví dụ máy Server; dùng IPv4 Wi-Fi thực tế từ `ipconfig`. Khi chuyển Wi-Fi và IP thay đổi, giữ `SERVER_ADDRESS=0.0.0.0`, thay origin IP cũ trong `ALLOWED_ORIGINS` bằng IP mới rồi Stop/Run Server. Client cùng mạng mở `http://<IP-mới>:8080/`; không dùng `0.0.0.0` làm URL. Các override Environment variables/VM options/Program arguments trong IntelliJ phải không mâu thuẫn với file local. Không cần đổi DB_HOST nếu MySQL vẫn chạy trên cùng máy Server.

Kiểm tra theo thứ tự: console có `Started QuizApplication` và port8080; trên máy Server mở `http://localhost:8080/api/system/ready` để xác nhận MySQL UP; sau đó mở URL IP LAN có `:8080`. `ERR_CONNECTION_REFUSED` có thể do Server chưa chạy, sai IP/port hoặc bind localhost; origin sai thường gây lỗi HTTP/WS sau khi đã kết nối. Nếu máy Server truy cập IP LAN được nhưng thiết bị khác không vào được, kiểm tra Windows Firewall cho TCP8080 trên mạng Private và Wi-Fi có cô lập Client không. Không tắt firewall hoặc mở port MySQL cho Client.

## Feature ownership và entry points

Base `vn.edu.quiz`; [QuizApplication](src/main/java/vn/edu/quiz/QuizApplication.java) ở root scan toàn feature. Entity/Repository/enum ở feature sở hữu; không global business layer hoặc Account Entity trùng User.

| Feature | Trách nhiệm / entry point |
|---|---|
| user | UserAccount/UserRepository; không global HOST role |
| auth | AuthController→AuthService; SecurityConfiguration, AuthSessionRegistry/AuthPrincipal; BCrypt/session/CSRF, permission |
| quiz | QuizController/QuizService/QuizAccessPolicy; PUBLIC/PRIVATE/author,4 options, immutable images; DTO owner khác metadata |
| room | RoomController/RoomService; membership/config/revision; orchestration RoomOperations/RoomBoundary ở realtime/session |
| game/engine | ScoreEngine/RankingEngine, input/output thuần; không clock/random/DB/WS |
| game/service | GameLifecycle phase/retry, GameTransactions transaction, GameHistoryService/StartupCleanup; snapshot ở game/dto |
| realtime | WaitingRoomWebSocketHandler/GameCommandAdapter/Parser; connection/generation; GameRuntime/SessionQueue/RoomBoundary; timer/ServerClock; replay/fingerprint; message command/event/common |
| system/common | Readiness/config và tiện ích nhỏ; không engine/business dùng chung |
| static/client | app.js/router; api/core/dom/transport; account/quizzes/rooms/game-state/game/history; textContent, revision/receipt guards |

Canonical [REST](docs/rest-api-contract.md) và [WS](docs/websocket-contract.md) quy định field/type/nullability/permission/error thật. REST quản lý Account/Quiz/Room/config/Start/snapshot/Cancel/History; /ws Waiting+gameplay dùng cookie HttpOnly/mandatory Origin, cùng envelope. Không JWT/query token/localStorage identity. Answer ACK không correctness. Non-owner PUBLIC chỉ metadata; Owner chọn PRIVATE, người chơi tham gia Room qua code theo policy đã chốt.

### Trace để học và bảo vệ

`transport.js` giữ UUID/frame khi retry → /ws Principal/current generation → GameCommandParser → GameCommandAdapter → GameRuntime.command → **ingress timestamp/sequence/enqueue nguyên tử** → SessionQueue tuần tự/Game → replay User+Game trước phase validation → GameLifecycle → GameTransactions (ScoreEngine thuần khi scoring) → **proxy DB commit** → copied runtime/receipt → GameLifecycleEvent/AuthenticatedSocketRegistry → recipient message → game-state revision guard → game.js/History.

Start dùng RoomBoundary trước Game ID; revalidate HTTP session sau chờ lock; transaction snapshot roster/config/UAG rồi handoff actor sau commit. UAG gồm Spectator, Start chéo Room không cùng thắng. Answer hợp lệ receivedAt<deadline, timestamp Client không dùng. Timer cùng ingress, sai question/phase/token/generation no-op. Offline vẫn được tính NO_ANSWER. Chấm cả câu một transaction, rollback không partial runtime/cache ACCEPT. Retry tối đa3 lần100/300ms; terminal error khác durable success, restore DB/restart hoàn tất pending cleanup.

Replay giữ10 phút sau FINISHED, không bảo đảm qua restart. Socket mới thay cũ/4002, queued command cũ bị fence theo generation, không reset PlayerSession. Host eliminated/Spectator vẫn Cancel; CANCELLED/SERVER_INTERRUPTED không official Winner. End thường ưu tiên ALL_ELIMINATED/ONE_SURVIVOR trước COMPLETED; tất cả rank1 là co-Winner theo xác nhận người dùng. [Báo cáo](docs/final-report.md) có sơ đồ/transaction/luật.

## Test và smoke thật

~~~powershell
# Full suite với MySQL/schema test đã chuẩn bị; không H2
.\mvnw.cmd -B --no-transfer-progress verify -Pmysql-smoke -Ddebug=false
# Server mysql/JAR đang ready; Chrome+Python
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-client.ps1 -Gameplay -ReportName task15-client-tests.json
~~~

JUnit XML ở target/surefire-reports, target/failsafe-reports. Browser dùng4 Chrome contexts cookie riêng, HTTP/rawWS/MySQL thật. Smoke Register/Login/Logout→Quiz/ảnh→Waiting→Host Participate3Player hoàn thành10 câu (Spin/Star/Answer/NO_ANSWER offline/lost ACK retry/reconnect)→Final/History; các trận riêng Host Spectator3Player Cancel, Host eliminated Cancel, replacement/auth expiry. Không mockAPI hoặc coi Waiting thành công là đã chơi hết trận.

Evidence lịch sử: Task13 full **224 unit+92 integration**; Task14 diff cuối **47 unit+77 integration**,0 fail/error/skip. Task15 kết quả hiện tại ở [status](docs/project-status.md), [handoff evidence](experiments/handoff/evidence-index.md). Không cộng bộ có test trùng thành số mới. Race tests dùng clock/latch/barrier, load không thay thế chứng minh race.

## Experiment, novelty, limitations

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
