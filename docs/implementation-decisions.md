# Implementation decisions

Ngày cập nhật: 05/10/2026 (Asia/Bangkok). Mục1–10 là thiết kế lịch sử Task0; mục11 Task1, mục12 schema Task2, mục13 Codebase Architecture Task2.5, mục14 auth Task3, mục15 Quiz/ảnh/sample Task4, mục16 Waiting Room Task5, mục17 Score/Ranking Engine Task6, mục18 ingress/queue/timer Task7, mục19 lifecycle Task8, mục20 gameplay WS/replay Task9, mục21 reconnect/replacement Task10, mục22 Web Client Task11A, mục23 History/Cancel/cleanup Task11B, mục24 gameplay Client Task12. Task chính duy nhất: [TASKS.md](../TASKS.md). Hiện trạng: [project-status.md](project-status.md). Luật nguồn: [gameplay-rules.md](gameplay-rules.md).

## 1. Nguồn và ranh giới

- Repo ban đầu chỉ có TASKS.md; đọc phụ lục Overview ngay trong file. Không có source/dependency để tái sử dụng. Không có AGENTS.md trên đĩa trong workspace hay D:\; áp dụng chỉ dẫn tiếng Việt người dùng cung cấp trong chat.
- Cuối khảo sát xuất hiện thêm [1.sql](../1.sql), chứa `create DATABASE quizz`; đã đọc, giữ nguyên, không thực thi. Chọn tên DB dự kiến `quizz` theo tài liệu SQL hiện có; Task 1 kiểm tra DB thực tế trước chuẩn bị. Chưa có schema bảng/entity/migration để tái sử dụng.
- Overview là nguồn gameplay; các yêu cầu cụ thể trong Task 2–15 bổ sung tiêu chí triển khai. Chỉ dẫn/giải đáp trực tiếp của người dùng được ghi rõ bên dưới. Không xem lời Overview dẫn Technical Design V2 là đã đọc tài liệu V2.
- Một ứng dụng Spring Boot, một MySQL; Web Client qua REST/WebSocket trên Localhost/LAN. Server quyết định gameplay. Host là quyền theo Room, độc lập participation và trạng thái loại. Không Cloud/Redis/Kafka/cluster/failover.
- Không sửa nguồn, không tạo roadmap khác, không scaffold, cài/nâng dependency, chạy migration, khởi động ứng dụng hoặc khởi tạo Git ở Task 0.

## 2. Stack tối giản đã chọn

| Mảng | Quyết định cho triển khai | Lý do / task |
|---|---|---|
| Java/build | Java 21, Maven, một Maven module; thêm Maven Wrapper ở Task 1 | Máy có JDK 21.0.9, Maven 3.9.12; không cần toolchain khác |
| Backend | Spring Boot nhánh 3.5.x, Spring MVC, Spring Security, Spring Data JPA, Bean Validation, WebSocket | Servlet stack đồng bộ phù hợp JPA và queue tuần tự, dễ giải thích; không cần reactive |
| Version | Task 1 chốt patch release chính thức của nhánh 3.5, pin parent và Wrapper; dependency tương thích lấy từ Boot BOM | Chưa có pom/lockfile. Chưa cài hay resolve dependency; phải kiểm tra artifact/API thực tế khi scaffold, không tự trộn major Spring/Jackson/Hibernate |
| Frontend | HTML/CSS/JavaScript ES modules, fetch và WebSocket trình duyệt; static assets do Boot phục vụ cùng origin | Không cần Node/npm, SPA framework hay dev server riêng cho bài nhóm sinh viên; vẫn chia module UI/state/network |
| REST | Tiền tố `/api`; account, quiz, tạo/lưu cấu hình room, history, snapshot khi cần | Dữ liệu request/response thông thường; DTO riêng không serialize entity trực tiếp |
| Realtime | Raw WebSocket JSON tại `/ws`, không STOMP/SockJS/broker | Ít tầng, trace command/ACK/event trực tiếp; tự chịu trách nhiệm routing, authorization và send serialization |
| Auth | Spring Security HTTP session cookie `HttpOnly`, `SameSite=Lax`; BCrypt; CSRF cho REST thay đổi dữ liệu; authenticate handshake bằng session | Browser tự gửi cookie cùng origin, không cần JWT trong URL hay localStorage. Kiểm tra session còn hiệu lực khi xử lý command; logout/hết hạn đóng socket gắn session |
| Origin/LAN | Mặc định localhost; địa chỉ bind và allowed origins cấu hình bằng môi trường; danh sách origin cụ thể | Cùng origin giảm cấu hình; LAN dùng IP máy Server. Không mở wildcard có credentials |
| Database | MySQL 8.0 hiện có, InnoDB/utf8mb4; Spring Data JPA; không dùng H2 thay MySQL | Đã tìm thấy binaries 8.0.45 và service đang chạy; SQL version/credentials/schema chưa kiểm chứng |
| Migration | Flyway SQL (`flyway-core` và module `flyway-mysql`), JPA `ddl-auto=validate` từ Task 2; Task 1 chưa tạo schema gameplay | Một cơ chế quản lý schema, migration review được; không dùng `ddl-auto=update` |
| Module logic | Package theo feature: `auth`, `quiz`, `room`, `game`, `history`; trong game tách `scoring`, `runtime`, `protocol`; `config` và lỗi chung nhỏ | Một deployment; scoring thuần Java không phụ thuộc WS/JPA; tránh nhiều Maven module không cần thiết |
| Ảnh tùy chọn | File local do Server quản lý, tên/version bất biến; metadata/reference trong MySQL; endpoint đọc kiểm tra quyền | Snapshot không trỏ tới ảnh bị ghi đè; không bỏ ảnh tùy chọn và không thêm dịch vụ lưu trữ |
| Test | JUnit Jupiter/parameterized tests, Spring Boot Test/MockMvc, Java HTTP/WebSocket client, integration test trên MySQL riêng | Unit scoring/clock độc lập; test constraint/locking/rollback dùng MySQL thật. Có thể thêm Testcontainers nếu có Docker; hiện chưa thấy Docker trong PATH |

Cơ sở đối chiếu chính thức (đã đọc ngày khảo sát):

- Java 21/Maven hiện có nằm trong dải hỗ trợ nêu trên [Spring Boot 3.5 system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html). Trang lúc đọc hiển thị 3.5.16; đó chưa phải dependency đã resolve của project.
- Raw WS có API handler và yêu cầu serialize việc gửi message; handshake hỗ trợ kiểm soát origin. Xem [Spring Framework WebSocket API](https://docs.spring.io/spring-framework/reference/web/websocket/server.html). Task 1/3/9 đọc lại tài liệu API theo version thực sự pin; không lấy ví dụ major mới rồi giả định tương thích.
- Danh tính HTTP có thể đi sang WS qua Principal, nhưng kiểm tra quyền từng raw JSON command vẫn phải tự làm. Xem [Spring Security WebSocket authentication](https://docs.spring.io/spring-security/reference/servlet/integrations/websocket.html).
- Chỉ dùng một cơ chế khởi tạo schema; MySQL cần module Flyway phù hợp. Xem [Spring Boot database initialization](https://docs.spring.io/spring-boot/3.5/how-to/data-initialization.html).

## 3. Contract tối thiểu dự kiến

Đây là vocabulary/envelope để task sau thống nhất, chưa phải endpoint hoạt động. Task 3/5/9 ghi payload và error thực tế khi triển khai; không cần viết trước toàn bộ API.

```json
{
  "v": 1,
  "kind": "COMMAND",
  "requestId": "uuid",
  "type": "SUBMIT_ANSWER",
  "target": { "kind": "GAME", "id": "game-id" },
  "questionIndex": 1,
  "payload": { "option": "B" }
}
```

| Loại | Trường/chính sách |
|---|---|
| COMMAND | `v`, `requestId`, `type`, `target`, `questionIndex`, `payload`. Target ROOM cho pre-game/START_GAME; GAME cho gameplay. Index 1..N; null cho command không gắn câu. Không tin userId, điểm hoặc timestamp Client |
| ACK | `kind=ACK`, `requestId`, `type`, `target`, `status=ACCEPTED`, `revision`, `serverTimeMs`, `payload`; gửi riêng người yêu cầu sau commit. Answer chỉ ghi đã nhận, không correctness/correctAnswer/scoreDelta |
| ERROR | `kind=ERROR`, requestId nếu đọc được, target nếu hợp lệ, `code`, `message`, `retryable`; không lộ stack trace/credential. REST dùng cùng error body và HTTP status phù hợp |
| EVENT | `kind=EVENT`, `type`, `target`, `eventId`, `revision`, `serverTimeMs`, `payload`; thứ tự gửi trong target. Có thể nhiều event cùng revision, eventId phân biệt |
| SNAPSHOT | Response riêng theo identity, target, revision; phase đầy đủ theo bảng dưới. Dùng dữ liệu Server, không ghép lại từ trạng thái Client |

Lỗi dự kiến: UNAUTHENTICATED, FORBIDDEN, INVALID_MESSAGE, INVALID_REQUEST_ID, INVALID_STATE, QUESTION_CLOSED, ALREADY_ANSWERED, INSUFFICIENT_RESOURCE, USER_ACTIVE_GAME, SESSION_REPLACED, SERVER_BUSY, SERVICE_UNAVAILABLE. Không đồng nhất business rejection với lỗi hệ thống. Các command gồm JOIN_ROOM, LEAVE_ROOM, START_GAME, USE_SPIN, USE_HOPE_STAR, SUBMIT_ANSWER, CANCEL_GAME, RECONNECT; sự kiện gồm ROOM_UPDATED, DECISION_STARTED, QUESTION_START, QUESTION_RESULT, LEADERBOARD, ELIMINATION, GAME_END.

Public event chỉ mang dữ liệu được phép xem. Spin/Star/ACK và đáp án đã chọn của mình trả riêng; correctAnswer chỉ công bố từ RESULT. Non-owner PUBLIC Quiz nhận metadata, không nội dung/đáp án qua API quản lý. PRIVATE chỉ Owner sử dụng/quản lý.

## 4. Atomicity, runtime và dữ liệu

- Dùng một hàng đợi tuần tự theo Room cho Join/Leave/config/Start; mỗi GameSession có processor tuần tự trên shared bounded worker pool. Không giữ một thread vô hạn mỗi trận và không dùng một worker duy nhất cho toàn Server. Command liên quan cả Room/Game phải đi qua một thứ tự khóa được ghi/test, không chờ lồng hai queue.
- Start trong transaction: khóa hàng Room, kiểm tra WAITING/Host/roster/author/câu hỏi, tạo GAME_SESSION, GAME_MEMBER (kể cả Spectator), PLAYER_SESSION chỉ cho PLAYER, GAME_QUESTION/config snapshot, rồi chiếm USER_ACTIVE_GAME cho **mọi GameMember**. Khóa/chiếm User theo userId tăng dần; PK/UNIQUE(user_id) là chốt DB cho Start chéo Room. Lỗi ở bất cứ User nào rollback toàn Start.
- Một Room chỉ có một active game, được bảo vệ bằng khóa hàng Room và active game reference/constraint được chốt ở Task 2. Room mutation và Start dùng cùng lock. Runtime/timer và START ACK chỉ được tạo/công bố sau commit. Không dùng chỉ `exists()` rồi insert để xử lý race.
- ROOM_MEMBER có UNIQUE(room,user), status JOINED/LEFT; join lại WAITING kích hoạt lại cùng membership thay vì thêm bản ghi trùng. GameMember là roster bất biến của lần chơi, không bị leave hay sửa Room lịch sử làm mất. Không thêm Player vào trận đã Start. Hành vi Host rời/đóng phòng đang ACTIVE phải giữ đường Cancel và không chuyển Host ngầm; chi tiết permission table hoàn thiện ở Task 5.
- ANSWER unique theo player/game-question, FK phải bảo đảm cùng GameSession. GAME_QUESTION UNIQUE(game_session_id,order_index). PlayerSession phải thuộc GameMember participation PLAYER. Task 2 quyết định composite keys/constraints và service guard có test trên MySQL, không tuyên bố annotation JPA tự bảo đảm hết.
- Answer hợp lệ được lưu `ACCEPTED_UNSCORED`, selectedOption và receivedAt/answerTimeMs; chưa có outcome/scoreDelta/scoredAt. Khi scoring toàn câu mới chuyển CORRECT/WRONG. NO_ANSWER có selectedOption/receivedAt null và thời gian toàn câu. Cancel trước scoring giữ ACCEPTED_UNSCORED, không gán nhầm WRONG/NO_ANSWER hoặc cộng thời gian chưa được chấm vào kết quả đã chốt.
- Scoring tính vào bản state tạm, dùng Spin đã chọn, persist toàn câu/player/result/revision trong một transaction; commit rồi mới thay runtime và phát ACK/event. Rollback không thay score/streak/resource/runtime authoritative. Snapshot đọc state nhất quán qua cùng processor, không thấy nửa câu.
- Snapshot lưu câu/options/correctAnswer/thứ tự/ảnh, giới hạn thời gian, decision duration, toàn bộ bảng điểm/trọng số/streak/Momentum/Recovery cho trận. Không đọc lại Quiz đang sửa để chấm. Không cascade xóa history khi xóa Quiz; soft delete nội dung và giữ reference ảnh lịch sử.

## 5. Idempotency và thời gian

### Scope và replay

- Key Room: `(authenticatedUserId, ROOM, roomId, requestId)`; START_GAME chưa có GameSession vẫn dedup được, response lưu gameId vừa tạo. Key Game: `(authenticatedUserId, GAME, gameSessionId, requestId)`. Cùng requestId ở hai target khác nhau là hai scope theo định nghĩa này.
- Fingerprint nội dung chuẩn hóa gồm `type`, toàn bộ `target`, `questionIndex` (kể cả null), `payload`; chuẩn hóa thứ tự object keys, không thay đổi ý nghĩa array. Reject unknown field/invalid schema; không hash chuỗi JSON thô. Cùng key khác fingerprint trả INVALID_REQUEST_ID.
- Xác thực danh tính/quyền truy cập target trước lookup; sau đó replay response đã chốt **trước** validate phase/deadline/tài nguyên hiện tại. Vì thế retry Answer đã ACCEPT sau close vẫn nhận ACK cũ. Đồng thời cùng ID cùng payload chờ chung kết quả trong processor, không chạy hai lần. ID mới vẫn bị luật một Answer/một Spin mỗi câu chặn.
- Cache memory cho một Server: giữ toàn bộ response đã xử lý trong suốt Game ACTIVE và 10 phút sau FINISHED; Room giữ đến CLOSED cộng 10 phút, qua cả các lần chơi để retry Start cũ không tạo trận mới. Đây là thời gian kỹ thuật được chọn, không phải luật Overview. Không bảo đảm replay qua process restart; restart cleanup chấm dứt trận cũ.
- Giới hạn dự kiến 4.096 receipt/User/Game, 4.096/User/Room; chạm giới hạn thì từ chối command mới trước side effect, vẫn phục vụ replay/snapshot. Không âm thầm evict receipt đang trong thời gian cam kết. Khi hết retention ở terminal target, command gameplay mới bị từ chối; Client lấy final snapshot/history. Task 9 test và công bố giới hạn thực tế.
- Cache response thành công sau commit. Business rejection có thể cache; lỗi hạ tầng transient không biến thành ACCEPT. Không random lại effect của operation đang retry transaction. Nếu không rõ DB đã commit, không xử lý lại mù quáng: đọc/reconcile revision/kết quả đã persist hoặc chuyển unavailable theo policy.

### Ingress và deadline

- Clock duration monotonic, trừu tượng hóa để test; timestamp lưu/hiển thị là UTC epoch milliseconds. Chọn độ phân giải luật là ms, không dùng thời gian Client để chấm. Lưu answerTimeMs là chênh lệch Server nhận và lúc thật sự mở câu; NO_ANSWER bằng full questionDurationMs.
- Command/timer dùng chung ingress sequencer cho target: trong một critical section lấy monotonic timestamp chính thức, cấp sequence và enqueue nguyên tử. Timestamp trước lock không được dùng làm receivedAt authoritative. Timer callback cũng enqueue, không tự sửa state ngoài processor.
- `receivedAt < deadline` hợp lệ về thời gian; đúng deadline hoặc muộn hơn bị từ chối dù scheduler chậm. Request đã ingress trước close phải được xử lý trước close dù worker chậm. Timer mang gameId/questionIndex/phase/generation; stale hoặc đóng/scoring lặp là no-op.
- Mốc phase mới chỉ tạo khi Server thực sự mở phase; truyền `serverTimeMs`, `deadlineEpochMs`, `remainingMs` cho UI. UI countdown chỉ để hiển thị. DECISION baseline lịch sử5.000ms (đã thay bằng7000ms ở quyết định25); không cộng thêm thời gian vì Client offline/ACK đến muộn.
- Đóng câu sớm chỉ khi mọi Player PLAYING trong roster câu đã có valid Answer; DISCONNECTED vẫn nằm trong roster. Không loại người offline ra để đóng sớm.

## 6. Snapshot và nhiều kết nối

Chung: target, status, phase, revision, serverTimeMs, questionIndex/count, cấu hình công khai, Room role (Host/member), participation, roster với playerState/connectionState, leaderboard. Player của người nhận gồm score, totalAnswerTimeMs, win/loseStreak, Momentum, Recovery, remainingSpins, Star còn hay đã dùng, pool Spin còn lại, currentSpin, starSelected và alreadyAnswered/selectedOption của chính mình. Host Spectator có `player=null`, không bịa PlayerSession.

| Phase | Dữ liệu thêm / giới hạn |
|---|---|
| WAITING | Room/config/roster/participation/quyền Start; chưa có câu của trận |
| DECISION | Deadline, tài nguyên, lựa chọn Spin/Star đã ACCEPT; không nội dung/options/correctAnswer của câu sắp mở |
| QUESTION_OPEN | Câu/options/ảnh hiện tại, deadline gốc, alreadyAnswered/ACK của mình; không correctAnswer/outcome hay câu tương lai |
| QUESTION_CLOSED / SCORING | Câu đã mở và trạng thái chờ chấm, khóa input; không công bố đáp án trước RESULT |
| RESULT | Đáp án, outcome, baseDelta/finalDelta, effect đã dùng/còn lại, streak mới, elimination và leaderboard đã commit |
| FINISHED | endReason, standings/final ranking và winner theo luật; giữ ACCEPTED_UNSCORED nếu Cancel; không lộ câu tương lai chưa chơi |

Revision tăng theo thay đổi authoritative đã commit trong target; snapshot được chụp trong processor và gửi cùng luồng outbound. Event có eventId để xử lý nhiều event cùng revision: bỏ revision thấp hơn snapshot, không bỏ mù mọi event cùng revision của một operation. Task 9/10 chọn batch event hoặc thứ tự eventId cụ thể và test snapshot/event xen nhau. Nếu mất chuỗi sự kiện, resync snapshot.

Tối đa một active gameplay socket/User. Socket mới thay socket cũ, gửi SESSION_REPLACED; client cũ không reconnect tranh nhau. Gắn connection generation; command chưa bắt đầu xử lý từ generation cũ bị từ chối khi replacement đã được ghi nhận; operation đã bắt đầu tuần tự được hoàn tất một lần. Disconnect của socket cũ không đổi connectionState socket mới. Không mất membership/tài nguyên khi disconnect.

## 7. Failure policy hữu hạn (Task 8 tích hợp, Task 12 hoàn thiện)

- Transaction lỗi transient và chắc chắn rollback: tối đa 3 lần thử tổng cộng, backoff 100 ms rồi 300 ms; operation giữ nguyên input/outcome random. Không sleep chặn shared worker; giữ thứ tự processor trong lúc lên lịch thử lại. Validation/permission/constraint business conflict không retry như lỗi hệ thống.
- Sau hết retry, dừng nhận thao tác gameplay cho Game bị lỗi, dừng timer của Game, báo SERVICE_UNAVAILABLE; không kẹt SCORING không có trạng thái lỗi. Thử ghi FINISHED/SERVER_INTERRUPTED, không official winner, giải phóng Room/User trong transaction cleanup, cũng tối đa 3 lần. Chỉ công bố GAME_END đã persist sau commit.
- Nếu MySQL vẫn không truy cập được hoặc commit không xác định, không thể cam kết cleanup đã lưu. Giữ game unavailable trong runtime, đóng gameplay socket của game, ghi log lỗi; không broadcast kết quả thành công hay tự giải phóng User rồi mở trận xung đột. Cần khôi phục DB và restart Server; startup cleanup xử lý ACTIVE bỏ lại trước khi cho nhận Start. Từ chối startup readiness nếu cleanup chưa commit được.
- Startup không phục hồi trận dang dở: đánh FINISHED/SERVER_INTERRUPTED, Room về WAITING và dọn USER_ACTIVE_GAME nhất quán. Không thêm failover, không công bố kết quả thắng bình thường.
- DB commit xong nhưng send thất bại: giữ kết quả, replay ACK hoặc snapshot trên reconnect, không undo/retry scoring vì lỗi socket. Runtime publish sau commit thất bại: ngừng processor, dựng lại trạng thái đã commit từ DB hoặc unavailable; không tự chạy lại scoring. Test phải tiêm lỗi ở từng ranh giới.

## 8. Nghiệp vụ cần chốt và câu trả lời của người dùng

| ID | Điểm nguồn chưa chốt | Quyết định / trạng thái | Áp dụng |
|---|---|---|---|
| G-01 | Overview §8.9/§17 chưa ưu tiên endReason khi câu cuối cũng kết thúc theo số người sống | **ĐÃ CHỐT 04/10/2026 qua trả lời người dùng:** ưu tiên ONE_SURVIVOR/ALL_ELIMINATED trước COMPLETED. Sau chấm toàn câu: alive=0 → ALL_ELIMINATED; alive=1 → ONE_SURVIVOR; còn >=2 và hết câu → COMPLETED | Task 8/12/13 |
| G-02 | Overview §8.9 nói ALL_ELIMINATED có ranking nhưng chưa nói rõ Winner | **ĐÃ CHỐT 04/10/2026 qua trả lời người dùng:** người đồng hạng 1 đều là Winner khi ALL_ELIMINATED. Ranking vẫn score giảm/time tăng, kiểu 1,1,3 | Task 8/11/12/13 |

Cancel và scoring theo Overview §11 và Task 12, không ưu tiên theo timestamp Client: Cancel được xử lý trước scoring thì câu đó không chấm; scoring bắt đầu trước thì chấm trọn câu. Nếu scoring đã kết thúc trận bình thường, Cancel đến sau không đổi kết quả terminal. CANCELLED chỉ standings, không official winner; SERVER_INTERRUPTED cũng không công bố thắng bình thường. Việc xếp hàng này là cách triển khai thứ tự đã có trong nguồn, không cho Cancel chen giữa cập nhật Player.

Hiện không còn câu hỏi nghiệp vụ đang chặn khảo sát/Task 1. Chưa có bằng chứng mâu thuẫn bảng điểm. Các chi tiết kỹ thuật (payload endpoint, giới hạn input, thời lượng hiển thị RESULT, schema ảnh, constraint SQL) sẽ chốt ở task sở hữu; không coi thiếu V2 là lý do đòi người dùng chuẩn bị thiết kế. Nếu phát hiện luật nghiệp vụ mới còn thiếu khi triển khai, ghi đúng trường hợp và hỏi trước khi tự suy ra kết quả.

## 9. Bảng truy vết điểm bắt buộc theo TASKS.md

Đây là bảng rủi ro/nghiệm thu liên kết task hiện có, không phải roadmap mới. Tất cả implementation/test trong bảng **CHƯA THỰC HIỆN** ở Task 0.

| Điểm phải xử lý | Task sở hữu / kiểm tra lại | Bằng chứng cần có |
|---|---|---|
| Host eliminated vẫn Cancel | 3, 8, 12 / 13–14 | Host ELIMINATED cancel được; Answer/Spin/Star bị từ chối |
| Start atomic cùng Room / chéo Room | 2, 5, 8 / 13–14 | Hai Start cạnh tranh, chỉ một transaction thắng; loser không để roster/snapshot/active-user rác |
| USER_ACTIVE_GAME gồm Spectator | 2, 5, 8, 12 / 13 | Host Spectator cũng bị chặn Start Room thứ hai; end giải phóng đúng |
| Room idempotency trước GameSession | 5, 9 / 13–14 | Mất START ACK, retry trả gameId cũ, kể cả sau game kết thúc |
| Fingerprint type/target/index/payload | 9 / 13–14 | Cùng key đổi type/index/payload bị INVALID_REQUEST_ID; JSON đổi key order vẫn replay |
| Ingress ordering command/timer | 7 / 13–14 | Barrier/fake clock: deadline−1, deadline, deadline+1; worker trễ không đảo thứ tự |
| Stale timer no-op | 7, 8 / 13–14 | Timer câu/phase cũ không đóng câu mới hoặc chấm hai lần |
| Snapshot revision đủ từng phase | 9, 10, 11 / 13–14 | Resync mọi phase, player null của Spectator, event cũ không rollback UI, không lộ đáp án |
| Runtime/broadcast chỉ sau DB commit | 8, 9, 12 / 13–14 | Rollback Start/Spin/Answer/scoring không ACCEPT hoặc đổi runtime; lỗi gửi sau commit không lặp tác động |
| Failure policy hữu hạn | 8, 12 / 13–14 | Đếm đúng số retry, game unavailable có lý do, startup cleanup; không kẹt SCORING |
| Answer ACCEPT chưa chấm khi Cancel | 2, 8, 9, 12 / 13 | ACCEPTED_UNSCORED còn nguyên; không gán CORRECT/WRONG/NO_ANSWER giả; không partial score |
| Toàn bộ bảng điểm, Recovery basePenalty, effect mới | 6 / 13 | Parameterized tests mọi mode/outcome; -1,-4,-7,-22,0; 20→0 sau 5 wrong, tiếp theo→-1; effect không tác động câu tạo streak |
| Author/PUBLIC/PRIVATE và ảnh snapshot | 4, 8 / 13–14 | Tác giả không chơi; non-owner không lấy đáp án; sửa/xóa Quiz/ảnh không đổi lịch sử |
| Socket replacement và offline roster | 7, 10, 11 / 13 | Một socket active/User; stale disconnect không làm offline socket mới; offline vẫn NO_ANSWER theo deadline |
| Ranking/endReason theo G-01/G-02 | 8, 12 / 13 | Câu cuối alive=0/1, đồng hạng 1,1,3, all eliminated có co-winners; Cancel không winner |

## 10. Luồng cần học để bảo vệ (chưa có code)

`SUBMIT_ANSWER → Principal/session → schema/permission → ingress(timestamp,sequence) → Game processor → idempotency → deadline/unique answer → transaction ACCEPTED_UNSCORED → commit → ACK riêng → close toàn câu → Score Engine → transaction toàn câu/revision → commit → RESULT/LEADERBOARD → Client render`.

Giải thích được vì sao ACK chưa biết đúng/sai, retry không đổi Answer, clock Client không quyết định hạn, offline vẫn trong roster, Recovery dùng basePenalty và broadcast phải sau commit. Trace sang file/class thật chỉ có thể bổ sung khi code được triển khai.

## 11. Task 1: implementation thực tế (05/10/2026)

### Build và dependency

Một Maven module Java 21, parent Spring Boot **3.5.16**, đã xác nhận qua metadata Maven Central và build thật. Dùng Boot BOM, không override riêng Spring/Hibernate/Jackson: Spring MVC **6.2.19**, Spring Security **6.5.11**, Hibernate **6.6.53.Final**, Connector/J **9.7.0**, Flyway core/mysql **11.7.2**, JUnit Jupiter **5.12.2**. Connector/J đã được kiểm tra kết nối với Server MySQL **8.0.45**; không suy ra driver version phải bằng Server version.

Wrapper chính thức plugin **3.3.4**, type only-script, Maven distribution **3.9.12**. Đối chiếu archive SHA-512 với digest Maven Central rồi pin distribution SHA-256 `305773a68d6ddfd413df58c82b3f8050e89778e777f3a745c8e5b8cbea4018ef`; chạy lại wrapper từ cache mới thành công. Dependency cache `.cache/maven` qua .mvn/maven.config, được ignore. Không init Git/commit/push.

### Config, database và profile

- Mặc định `mysql`, URL ghép từ DB_HOST/PORT/NAME, database quizz theo 1.sql. Người dùng cung cấp credential và yêu cầu kết nối; query SQL xác nhận quizz đã tồn tại, nên không chạy script tạo DB.
- application-mysql.yml import file `config/application-local.properties` tùy chọn. File local dùng **DB_USER/DB_PASSWORD**, biến môi trường cùng tên được ưu tiên. Import tại profile MySQL tránh bị giá trị datasource profile ghi đè file nền, lỗi đã tìm thấy bằng smoke test và sửa. Sample password rỗng; credential thật chỉ ở file local ignore, không trong source/JAR/docs.
- Hikari pool tối đa 5, connection timeout 5s, validation/query/socket timeout 3s; ApplicationRunner kiểm tra SELECT 1. Không gán dialect cứng để che lỗi JDBC metadata.
- `ddl-auto=validate`, open-in-view false, SQL init never, Flyway disabled. Task 1 chưa entity/repository nên validation chưa chứng minh domain schema. Trước/sau chạy đều 0 bảng, không Flyway history. Task 2 tạo schema/migrations/constraints rồi bật Flyway.
- Script prepare-database.sql tạo database IF NOT EXISTS với utf8mb4 cho máy mới, không domain bảng/tài khoản/password hardcode. start-local.ps1 hỗ trợ chọn profile và nhập password kín, chỉ đưa vào env process rồi khôi phục.
- Profile `web-only` không tạo DataSource/JPA/Flyway hoặc đọc credential. Dùng để kiểm tra HTTP/client riêng, **không phải MySQL giả**; không H2/mock database. Chỉ bật một trong mysql/web-only.

### HTTP, security và client

- HTML/CSS/JavaScript ES modules do Boot phục vụ cùng origin. Trang chỉ hiện trạng thái và nút kiểm tra lại, ghi rõ chưa auth/room/gameplay; không tính điểm hay tạo trận giả.
- GET `/api/system/status`: HTTP 200 khi HTTP Server phục vụ, body `application`, `server`, `database` (status/message), `serverTimeMs`, `gameplay=NOT_IMPLEMENTED`.
- GET `/api/system/ready`: HTTP 200 chỉ khi SELECT 1 thành công; 503 nếu DOWN/NOT_CONFIGURED. Public response không trả credential/JDBC URL/SQL exception; no-store. Đây chưa phải command/ACK/event gameplay ở mục 3.
- SecurityFilterChain chỉ mở GET asset/status/ready và error route; request khác denyAll. Loại UserDetailsService auto-config để không sinh account/password giả. Giữ CSRF và session cookie HttpOnly/SameSite=Lax; chưa tuyên bố Register/Login đã triển khai.
- Có raw WS dependency; `/ws` hiện bị từ chối. Handler/authentication ở Task 3/5/9, không mở handler chưa auth ngoài Task 1.
- Origin cấu hình HTTP(S) có host, không wildcard/path/query/credentials; sai cấu hình fail startup. CORS credentials chỉ theo allowlist. Bind LAN và origins qua môi trường, không thêm cloud.

### Bằng chứng và giới hạn

`mvnw.cmd -B --no-transfer-progress -Pmysql-smoke verify` đạt **14 tests cấu hình/HTTP + 2 MySQL smoke**, 0 fail/error/skip. Kiểm tra JDBC product MySQL/version/database, JPA SELECT 1, HTTP readiness 200. JAR chạy 127.0.0.1:8080 với MySQL UP. Instance LAN tạm 0.0.0.0:8081 cho origin được cấu hình, từ chối origin lạ 403 và đã dừng sau test. Chưa chứng minh kết nối từ máy LAN khác.

Browser skill đã thử dùng nhưng bootstrap lỗi trusted dependency path của plugin; **chưa kiểm chứng visual/click browser**. Không coi lỗi công cụ là bằng chứng lỗi UI hay DB chưa kiểm chứng. Asset/HTTP đã test thật; chi tiết evidence trong project-status.

Luồng code đã triển khai: [app.js](../src/main/resources/static/app.js) → SecurityFilterChain/CORS → [SystemController](../src/main/java/vn/edu/quiz/system/controller/SystemController.java) → [JdbcDatabaseProbe](../src/main/java/vn/edu/quiz/system/service/JdbcDatabaseProbe.java) → SELECT 1 → JSON → DOM. Luồng Answer/scoring mục 10 vẫn là thiết kế cho task sau.

## 12. Task 2: schema và persistence thực tế (05/10/2026)

Giữ Boot3.5.16/Java21/BOM/dependency của Task1; không thêm dependency hoặc nâng version. Bật Flyway migrate/validate trước Hibernate ddl-auto=validate, clean-disabled=true; SQL init never. [V1](../src/main/resources/db/migration/V1__quiz_domain.sql) tạo 11 domain bảng, [V2](../src/main/resources/db/migration/V2__require_answer_effect_snapshots.sql) bổ sung CHECK effect snapshot Answer. V1 đã áp dụng được giữ nguyên, không sửa checksum/repair. MySQL8.0.45 thực tế; tối thiểu8.0.17 vì JSON_SCHEMA_VALID, theo [MySQL official docs](https://dev.mysql.com/doc/refman/8.0/en/json-validation-functions.html). Đây là thay đổi schema được Task2 giao; không chạy migration gameplay ở Task1.

| Lựa chọn | Lý do và ranh giới |
|---|---|
| Physical app_user, lowercase table; BIGINT IDENTITY | Tránh trùng tên hàm SQL, convention thống nhất; USER vẫn là một Account, không thêm bảng Account |
| InnoDB/utf8mb4_0900_ai_ci; enum VARCHAR ascii_bin + CHECK | Văn bản tiếng Việt; status/option phân biệt hoa/thường và reject giá trị lạ. Username UNIQUE theo collation không phân biệt hoa/thường/dấu; Task3 chuẩn hóa/validation tại API |
| Epoch UTC ms/duration BIGINT; receipt nullable | Không nhầm giây với ms; NO_ANSWER không bịa received timestamp. Deadline/clock server do Task7 thực hiện |
| Scalar FK entity; no ORM cascade | Đơn giản, tránh cascade xóa/history hoặc serialize quan hệ lộ đáp án/hash. Composite FK được SQL enforce; DTO/API ở task sau |
| @Version Quiz/Room/GameSession/PlayerSession | Kiểm soát update cạnh tranh và revision nền cho snapshot; Task5/8/9 vẫn cần lock/transaction/queue, @Version không thay thế processor |
| One ACTIVE Game/Room qua generated UNIQUE | MySQL không có partial UNIQUE thông thường; generated room_id khi ACTIVE, NULL khi FINISHED cho nhiều lần chơi lịch sử |
| USER_ACTIVE_GAME PK(user_id), FK GameMember và (game,ACTIVE) | Khóa chung mọi participation kể cả Spectator. Không được finish khi còn active-user; service phải release trong cùng transaction trước finish |
| GameMember FK(game,room), FK(room,user), snapshot participation/role | Roster gắn đúng Room và row membership; source membership đổi lựa chọn không đổi lịch sử |
| PlayerSession FK(game,user,PLAYER) + CHECK cố định PLAYER | Spectator không thể có PlayerSession dù bypass Java. Host role giữ ở GameMember/Room, độc lập loại |
| Answer hai FK (game,player)/(game,question) + unique(player,question) | Chặn Answer chéo trận cả hai hướng và duplicate ở DB; không dựa vào query exists trước insert |
| JOINED/LEFT, UNIQUE(room,user), không delete membership khi Leave | Rejoin WAITING tái dùng cùng id, cập nhật joined_at_ms lần mới, left_at_ms NULL; GameMember lịch sử bất biến. Không có nhật ký từng lần Join, không ngầm tạo Player trong trận ACTIVE |
| Soft delete User/Quiz/Question và toàn FK RESTRICT | Giữ lịch sử/source identity; GameSession title/author/config và GameQuestion nội dung snapshot dùng khi chơi/history, không đọc lại source để chấm |
| Trigger immutable content/config/roster; frozen elimination | DB giữ snapshot cả khi ghi SQL trực tiếp; ELIMINATED không thay state/score/time/index, final_rank vẫn được cập nhật. Không thêm luật loại/tính điểm |
| image_ref sha256:<64 hex lowercase>, nullable | Reference gắn nội dung thay vì URL/file có thể ghi đè. Task4 lưu/serve local file và giữ blob còn reference; Task2 chỉ schema/reference, chưa upload |
| ACCEPTED_UNSCORED với outcome NULL | ACK Answer chưa tiết lộ kết quả; Cancel trước scoring không gán đúng/sai và không cộng duration vào total đã chốt. CORRECT/WRONG/NO_ANSWER yêu cầu score/time/result đầy đủ |
| Result JSON bắt buộc effect trước/sau khi đã chấm | V2 yêu cầu bốn boolean Momentum/Recovery; PlayerSession lưu cả hai cùng lúc và elimination metadata; phục vụ giải thích history mà không bịa kết quả pending |

[GameplayRulesSnapshot](../src/main/java/vn/edu/quiz/game/dto/GameplayRulesSnapshot.java) là record dữ liệu luật schemaVersion1, chứa toàn bảng điểm/spin weight/Star/hardship restriction/streak/effect config và end rules đã được người dùng trả lời G-01/G-02. Không phải engine; không random/chấm/xếp hạng hoặc xử lý command. [GameplayRulesSnapshotTest](../src/test/java/vn/edu/quiz/game/dto/GameplayRulesSnapshotTest.java) đối chiếu các số với Overview8.2–8.7 và floor(N/10). DDL CHECK bảo vệ cấu trúc/range/flags chính của JSON; factory version1 cung cấp bảng chuẩn. Hibernate JSON typed mapping roundtrip đạt. Enum được @Enumerated(STRING) và @JdbcTypeCode(VARCHAR) rõ ràng để khớp VARCHAR thay vì native ENUM dialect, theo [Hibernate6.6 documentation](https://docs.hibernate.org/orm/6.6/userguide/html_single/).

[database-schema.md](database-schema.md) chứa ERD, toàn bộ119 cột, khóa/composite FK/nullability/default, hành vi Leave–Join, snapshot và contract persisted Answer; SQL migrations là nguồn DDL có thẩm quyền. Đối chiếu information_schema thật:11 domain bảng,18 FK,14 UNIQUE,20 CHECK,4 triggers; tên119 cột khớp. Không claim FK/CHECK chứng minh đủ aggregate ≥3 Player/đủN câu, Author permission, Start atomic hoặc scoring chính xác; các phần đó ở Task3–9/12. Không đổi Host eliminated Cancel, bảng điểm, Recovery basePenalty, effect trigger hay thêm distributed infrastructure.

Kiểm chứng riêng schema test quizz_task2_test trên cùng MySQL Server, cố định tên test trong MySqlSchemaIT, transaction rollback mỗi fixture; không thêm Database runtime thứ hai. scripts/prepare-test-database.sql tự tạo test database IF NOT EXISTS, không DROP/TRUNCATE. MySqlSmokeIT dùng DB_NAME/quizz, áp dụng Flyway rồi SELECT/JPA/readiness, không insert fixture vào quizz. Maven profile mysql-smoke hiện chạy12 schema+2 smoke sau30 tests thường; **44 tests PASS, không fail/error/skip**. Credential tái dùng file local đã được người dùng cung cấp/cho phép; không ghi vào migration/test/docs/JAR. Không H2/mock/Testcontainers hay framework mới. Chi tiết lỗi fixture đã sửa và command/evidence ở project-status.

JAR khởi động lại với version2 schema validated, readiness200/MySQL UP; instance review localhost PID70864. Chỉ dừng instance Task1 do khóa file JAR khi build Windows; không dừng serviceMySQL. Task2 DONE theo nghiệm thu schema/constraints/history independence; dừng review, không triển khai Task3. Các mục1–10 còn là thiết kế tương lai khi chưa có code tương ứng.

## 13. Codebase Architecture — TASK 2.5

Nguồn: TASKS.md bản người dùng thay trước Task2.5 (SHA256 C3AF23A5E052ECB171B55AD2456D34A57E2FCB94E478EDC7BE65352D4091555C), quy tắc package-by-feature và yêu cầu Task2.5. Giữ base package **vn.edu.quiz**, main **QuizApplication**, artifactId/dependency và behavior Task0–2. Baseline source có34 production classes +5 test classes,11 entity/11 repository; không có auth service, WS handler, queue/timer hoặc Score Engine. DomainTypes đang gom12 enums và persistence đang trộn entity/repository/snapshot; mapping dưới đây là refactor code thật, không thiết kế placeholder.

### Mapping class production

| Old package/class | New package/class |
|---|---|
| `vn.edu.quiz.game.persistence.UserActiveGameRepository` | `vn.edu.quiz.game.repository.UserActiveGameRepository` |
| `vn.edu.quiz.game.persistence.UserActiveGame` | `vn.edu.quiz.game.entity.UserActiveGame` |
| `vn.edu.quiz.game.persistence.PlayerSessionRepository` | `vn.edu.quiz.game.repository.PlayerSessionRepository` |
| `vn.edu.quiz.game.persistence.PlayerSession` | `vn.edu.quiz.game.entity.PlayerSession` |
| `vn.edu.quiz.game.persistence.GameSessionRepository` | `vn.edu.quiz.game.repository.GameSessionRepository` |
| `vn.edu.quiz.game.persistence.GameSession` | `vn.edu.quiz.game.entity.GameSession` |
| `vn.edu.quiz.game.persistence.GameQuestionRepository` | `vn.edu.quiz.game.repository.GameQuestionRepository` |
| `vn.edu.quiz.game.persistence.GameQuestion` | `vn.edu.quiz.game.entity.GameQuestion` |
| `vn.edu.quiz.game.persistence.GameplayRulesSnapshot` | `vn.edu.quiz.game.dto.GameplayRulesSnapshot` |
| `vn.edu.quiz.game.persistence.GameMemberRepository` | `vn.edu.quiz.game.repository.GameMemberRepository` |
| `vn.edu.quiz.game.persistence.GameMember` | `vn.edu.quiz.game.entity.GameMember` |
| `vn.edu.quiz.game.persistence.AnswerRepository` | `vn.edu.quiz.game.repository.AnswerRepository` |
| `vn.edu.quiz.game.persistence.Answer` | `vn.edu.quiz.game.entity.Answer` |
| `vn.edu.quiz.room.persistence.RoomRepository` | `vn.edu.quiz.room.repository.RoomRepository` |
| `vn.edu.quiz.room.persistence.RoomMemberRepository` | `vn.edu.quiz.room.repository.RoomMemberRepository` |
| `vn.edu.quiz.room.persistence.RoomMember` | `vn.edu.quiz.room.entity.RoomMember` |
| `vn.edu.quiz.room.persistence.Room` | `vn.edu.quiz.room.entity.Room` |
| `vn.edu.quiz.quiz.persistence.QuizRepository` | `vn.edu.quiz.quiz.repository.QuizRepository` |
| `vn.edu.quiz.quiz.persistence.Quiz` | `vn.edu.quiz.quiz.entity.Quiz` |
| `vn.edu.quiz.quiz.persistence.QuestionRepository` | `vn.edu.quiz.quiz.repository.QuestionRepository` |
| `vn.edu.quiz.quiz.persistence.Question` | `vn.edu.quiz.quiz.entity.Question` |
| `vn.edu.quiz.auth.persistence.UserRepository` | `vn.edu.quiz.user.repository.UserRepository` |
| `vn.edu.quiz.auth.persistence.UserAccount` | `vn.edu.quiz.user.entity.UserAccount` |
| `vn.edu.quiz.system.WebOnlyDatabaseProbe` | `vn.edu.quiz.system.service.WebOnlyDatabaseProbe` |
| `vn.edu.quiz.system.SystemController` | `vn.edu.quiz.system.controller.SystemController` |
| `vn.edu.quiz.system.JdbcDatabaseProbe` | `vn.edu.quiz.system.service.JdbcDatabaseProbe` |
| `vn.edu.quiz.system.DatabaseStatus` | `vn.edu.quiz.system.dto.response.DatabaseStatus` |
| `vn.edu.quiz.system.DatabaseStartupCheck` | `vn.edu.quiz.system.service.DatabaseStartupCheck` |
| `vn.edu.quiz.system.DatabaseProbe` | `vn.edu.quiz.system.service.DatabaseProbe` |
| `vn.edu.quiz.config.WebAccessProperties` | `vn.edu.quiz.common.config.WebAccessProperties` |
| `vn.edu.quiz.config.SecurityConfiguration` | `vn.edu.quiz.auth.security.SecurityConfiguration` |
| `vn.edu.quiz.persistence.IdentityEntity` | `vn.edu.quiz.common.util.IdentityEntity` |
| `vn.edu.quiz.persistence.DomainTypes.Visibility` | `vn.edu.quiz.quiz.enums.Visibility` |
| `vn.edu.quiz.persistence.DomainTypes.RoomStatus` | `vn.edu.quiz.room.enums.RoomStatus` |
| `vn.edu.quiz.persistence.DomainTypes.Participation` | `vn.edu.quiz.room.enums.Participation` |
| `vn.edu.quiz.persistence.DomainTypes.MembershipStatus` | `vn.edu.quiz.room.enums.MembershipStatus` |
| `vn.edu.quiz.persistence.DomainTypes.GameStatus` | `vn.edu.quiz.game.enums.GameStatus` |
| `vn.edu.quiz.persistence.DomainTypes.Phase` | `vn.edu.quiz.game.enums.Phase` |
| `vn.edu.quiz.persistence.DomainTypes.EndReason` | `vn.edu.quiz.game.enums.EndReason` |
| `vn.edu.quiz.persistence.DomainTypes.MemberRole` | `vn.edu.quiz.game.enums.MemberRole` |
| `vn.edu.quiz.persistence.DomainTypes.PlayerState` | `vn.edu.quiz.game.enums.PlayerState` |
| `vn.edu.quiz.persistence.DomainTypes.AnswerStatus` | `vn.edu.quiz.game.enums.AnswerStatus` |
| `vn.edu.quiz.persistence.DomainTypes.Option` | `vn.edu.quiz.quiz.enums.Option` |
| `vn.edu.quiz.persistence.DomainTypes.SpinEffect` | `vn.edu.quiz.game.enums.SpinEffect` |
| `vn.edu.quiz.system.SystemController.SystemStatus` | `vn.edu.quiz.system.dto.response.SystemStatus` |

QuizApplication giữ nguyên vn.edu.quiz. GameplayRulesSnapshot.Score/SpinRule/StreakRules vẫn là nested record, chỉ đổi owner sang game.dto.GameplayRulesSnapshot, không đổi fields/JSON. DomainTypes bị loại bỏ sau khi tách12 enum; không giữ alias/class cũ. Enum giữ nguyên tên/value/ordinal; Participation thuộc room và game tái sử dụng, Option thuộc quiz và game tái sử dụng, không nhân đôi enum. IdentityEntity là utility ánh xạ ID thực sự được10 entity sử dụng, không phải global domain Entity; giữ @MappedSuperclass/@Id/@GeneratedValue để không đổi persistence.

### Mapping test

| Old test | New test |
|---|---|
| `vn.edu.quiz.system.SystemWebTest` | `vn.edu.quiz.system.controller.SystemWebTest` |
| `vn.edu.quiz.system.MySqlSmokeIT` | `vn.edu.quiz.system.controller.MySqlSmokeIT` |
| `vn.edu.quiz.persistence.MySqlSchemaIT` | `vn.edu.quiz.game.repository.MySqlSchemaIT` |
| `vn.edu.quiz.persistence.GameplayRulesSnapshotTest` | `vn.edu.quiz.game.dto.GameplayRulesSnapshotTest` |
| `vn.edu.quiz.config.WebAccessPropertiesTest` | `vn.edu.quiz.common.config.WebAccessPropertiesTest` |

Giữ các test/assertion/fixtures hiện có; đổi package/import theo owner. MySqlSchemaIT là integration test aggregate game/FK nhiều feature. Không thêm framework/test thay implementation chỉ để hợp sơ đồ.

### Owner và dependency direction

- user/entity, user/repository: danh tính UserAccount và lookup; không auth/login service.
- quiz/entity, repository, enums: nội dung Quiz/Question, Visibility/Option.
- room/entity, repository, enums: Room/RoomMember, participation và membership state. game dùng Participation của room, không import controller/realtime.
- game/entity, repository, enums: roster/session/answer/active-user/state persisted; game/dto chứa snapshot cấu hình thuần dữ liệu. Chưa có Score Engine hoặc orchestration service, không tạo engine/service rỗng.
- auth/security: SecurityConfiguration hiện có quản lý SecurityFilterChain và CORS bean; không triển khai authentication. common/config sở hữu WebAccessProperties allowlist dùng cho cấu hình mạng; common/util chỉ IdentityEntity dùng chung thật.
- system/controller, service, dto/response: health/readiness feature có thật; SystemController → DatabaseProbe → JdbcDatabaseProbe/WebOnlyDatabaseProbe. DatabaseStartupCheck dùng cùng probe; response record riêng. Không đưa health controller vào common/response.
- main ở root scan toàn feature tự động. Không có EntityScan/EnableJpaRepositories/ComponentScan hardcode; một JPQL query của RoomRepository dùng entity name Room, không FQCN constructor DTO/reflection/config tên class. Entity simple names/@Table/mapping giữ nguyên.
- Hướng hiện có: auth → common.config; entity từng feature → common.util; game → quiz.enums + room.enums; system.controller/service → system.dto.response; repository → entity/enums của feature. Không feature nghiệp vụ phụ thuộc concrete WS.
- Ranh giới tương lai: realtime adapter → auth/room/game use-case; transaction và scoring thuộc game/service → game/engine + repository. realtime/session/timer/idempotency/connection quản lý hạ tầng transport/order/replay; engine/model domain không import WS/wire DTO/JPA. Chỉ tạo package/interface khi có class và nhu cầu thật, không circular DI hoặc allow-circular-references.

REST DTO vào <feature>/dto/request hoặc dto/response; wire DTO vào realtime/message/command,event,common. game/dto.GameplayRulesSnapshot là persisted config snapshot, không public REST DTO và không wire message. Không serialize entity hoặc password_hash/correct_option qua public API. Contract canonical được bổ sung tại docs/rest-api-contract.md và docs/websocket-contract.md; phần chưa triển khai có nhãn PLANNED theo task.

### Freeze decisions

| Decision | Value | Status | Task/source | Lý do / điều kiện kiểm tra |
|---|---|---|---|---|
| Base/main/build | vn.edu.quiz.QuizApplication, một Maven module Java21/Boot3.5.16/BOM hiện có | FROZEN | Task1/2, source và baseline | Không đổi namespace/artifact/version; build cùng wrapper/dependency |
| Package architecture | Owner/mapping ở trên; feature/entity/repository/enums/dto; common chỉ dùng chung thật | FROZEN | Task2.5/TASKS.md | Compile sạch, không duplicate/stale class, JPA/runtime discover đúng, không schema diff |
| Frontend | Static HTML/CSS/ES modules cùng origin | FROZEN | Task1, source/HTTP tests | Asset/hash/REST behavior không đổi; không framework mới |
| Migration/schema | Flyway SQL V1/V2, ddl-auto validate, SQL init never | FROZEN | Task2 + MySQL8.0.45 | Hash/source/schema/history trước–sau không đổi; Task2.5 chạy Flyway disabled để không migrate |
| Test | JUnit5/Boot Test/Failsafe mysql-smoke, MySQL riêng rollback fixture | FROZEN | Task1/2, baseline44 tests | Giữ test assertions; không H2/mock hoặc test disable |
| Current time representation | Epoch UTC BIGINT ms, response Instant.now().toEpochMilli | FROZEN trong phần hiện có | Task1/2 source | Package refactor không đổi timestamp/JSON |
| Clock duration abstraction | Chưa có injected Clock/monotonic adapter | PENDING | Task7 | Chốt/kiểm chứng ingress/timer khi triển khai Task7, không tự tạo Clock Task2.5 |
| Auth mechanism | HTTP session JSESSIONID + BCrypt10 + CSRF REST | FROZEN | Task3/API + MySQL network tests | Đã test Register/Login/Logout, fixation/CSRF/expired/logout; chi tiết mục14 và canonical contracts |
| WS transport | Raw WebSocket /ws + browser session cookie + mandatory Origin | FROZEN auth/transport | Task3/9 | Đã101/Principal/Origin/logout/expiry; chưa gameplay command/ACK, thuộc Task9 |
| Idempotency/retry/phase snapshot | Pregame Room boundary/receipt đã code mục16; Game/phase/ingress theo mục3–7 | FROZEN pregame; PROPOSED Game | Task5/7–10/11B | Canonical contracts + RoomNetworkIT/RoomBoundaryTest; không claim Game đã chạy |
| endReason/Winner | G-01/G-02 đã trả lời trong chat04/10 và ghi mục8 | FROZEN về luật, chưa lifecycle | User/Task0, snapshotTask2 | TASKS.md mới yêu cầu tìm nguồn trước hỏi; đã có nguồn nên không hỏi lại, không đổi luật |

Task0–2 giữ DONE theo xác nhận người dùng và evidence đã ghi; Task2.5 không chạy lại việc triển khai của các task đó. TASKS.md mới nhắc hai điểm gameplay thiếu nguồn trong bộ task, nhưng nguồn xác nhận G-01/G-02 có trong phiên và mục8; không có mâu thuẫn cần hỏi lại. Overview §§1–13 của bản TASKS.md mới khớp nguyên văn gameplay-rules. Không sửa/chép lại gameplay. Khảo sát không thấy business/schema blocker mới; lỗi ngoài package nếu phát hiện sẽ ghi task sở hữu, không mở rộng refactor.

Architecture đã **FROZEN** sau kiểm chứng: baseline/sau đều44 tests đạt; 11 entity/11 repository, JPA validate MySQL và JAR runtime thành công; thân code32 production class di chuyển và5 test giữ nguyên (trừ package/import, extraction SystemStatus), 12 enum giữ tên/value/ordinal; graph feature không vòng, JAR không class cũ. Schema dump hai DB trước–sau cùng SHA256589ABF84FAFEC1B07AFD0361765131114DA40F324A6EB96453293A35FE4E673B (bỏ counter AUTO_INCREMENT vốn tăng khi fixture rollback). Migration V1/V2 checksum/history giữ nguyên, Flyway disabled trong test/startup Task2.5. Không sửa resource/config/build/dependency/frontend/TASKS/gameplay. Runtime localhost PID72592, readiness200/MySQL UP, WS Upgrade403; body public/Cache-Control giữ nguyên. Evidence chi tiết ở project-status. Không tự chạy Task3 hoặc commit/push.

## 14. TASK 3 — Authentication và permission

Thực hiện trên architecture đã FROZEN Task2.5; đọc TASKS.md/Overview, docs/status/decisions và hai contract, kiểm tra source/dependency thực tế. Baseline44 tests trước sửa đạt với MySQL8.0.45. Không AGENTS trên đĩa; áp dụng tiếng Việt từ chat. Proposal/Technical Design V2/Topics/Instruction/Submission vẫn thiếu, không chặn task và không claim đạt môn.

| Decision | Value / status | Nguồn / lý do / evidence |
|---|---|---|
| Auth REST | Session cookie JSESSIONID, BCrypt strength10, CSRF / FROZEN | Stack Security6.5.11/Boot3.5.16 hiện có; đơn giản cho same-origin localhost/LAN, không JWT/dependency mới |
| Register/Login | Register201 không auto-login; Login200 đổi sessionID và CSRF, lưu context rõ ràng; đã login thì409 / FROZEN | DaoAuthenticationProvider + ProviderManager, credentials được erase; tránh account switch làm đổi identity socket đang sống |
| Identity | AuthPrincipal.userId từ UserRepository, ROLE_USER duy nhất; Host từ Room.hostUserId / FROZEN | Không Account Entity mới; User ở user/entity/repository; request userId không authorize |
| Password | 8–72 ký tự, tối đa72 byte UTF-8, không trim / FROZEN | Tránh BCrypt cắt password; hash không serialize, DTO toString redacted, lỗi không echo input |
| Username | ASCII3–32, lowercase Locale.ROOT; DB UNIQUE / FROZEN | Case behavior rõ ràng và không phụ thuộc framework mới; duplicate conflict409, không dùng lookup thay UNIQUE chống race |
| WS | Raw /ws cookie browser + Origin allowlist bắt buộc, không query / FROZEN | Spring native adapter; auth GET + Origin chống socket từ site khác, không bearer token URL;101 thật bằng CookieManager |
| Logout/expiry | Invalidate session và đóng mọi WS cùng session4001; HTTP idle30m, WS không renew / FROZEN | Registry auth + container listener + sweep1s, Spring event sang realtime/connection; không game timer/queue Task7 |
| Permission | Policy thuần context + repository guard adapter / IMPLEMENTED, evidence hữu hạn | Host ELIMINATED vẫn Cancel; PLAYING/PLAYER/phases cho action chơi; lifecycle integration PENDING Task8–10/13 |
| API/wire | canonical REST/WS theo source mới / FROZEN phần implement | Chỉ auth REST5 routes, WS AUTH_READY/close; không endpoint/message gameplay giả |

AuthConfiguration/QuizUserDetailsService/AuthService/controller/WS adapters chỉ profile mysql, để web-only giữ health/assets và403 cho chức năng chưa cấu hình, không tạo user store mock/H2. SecurityConfiguration dùng beans session/CSRF của mysql; SecurityContextRepository explicit save sau manual controller login và SessionAuthenticationStrategy (ChangeSessionId + CsrfAuthenticationStrategy) được gọi rõ ràng, theo [Spring Security6.5 session management](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/session-management.html), [CSRF](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html) và [DaoAuthenticationProvider](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/passwords/dao-authentication-provider.html). Dùng API thật6.5.11 trong BOM, không upgrade.

realtime/websocket sở hữu config/interceptor/handler; realtime/message/event.AuthReady chỉ identity/time, realtime/connection.AuthenticatedSocketRegistry bind sockets. auth/security phát AuthSessionRevoked qua Spring event; auth không import realtime, game không import auth/realtime hoặc wire DTO. AuthorizationService đọc repositories của user/room/game, PermissionPolicy dùng enum domain có thật; không vòng DI và không allow-circular-references. Handler đăng ký socket rồi validate lại để không bỏ lỡ logout giữa handshake và onOpen; disconnect chỉ remove binding, không sửa state/history. HTTP session registry runtime chỉ một Server, không durable/failover/cloud.

No migration/config/frontend/dependency/GameplayRulesSnapshot/TASKS.md đổi; UserAccount chỉ thêm JsonIgnore cho getter hash, không ORM/schema đổi. MySQL normalized schema dump sau task khớp Task2.5 (loại counter AUTO_INCREMENT, normalize line ending/trailing newline); V1/V2 source/hash/history giữ nguyên. Cả quizz và quizz_task2_test không còn user fixture. Test schema duy nhất riêng phục vụ test; runtime vẫn một Database quizz.

**Evidence cuối:** verify mysql-smoke80 tests đạt (57 thường +23 integration), fail/error/skip0; JAR startup thật localhost8080 default mysql, JPA validate và readiness200/MySQL UP. AuthNetworkIT6 cases dùng HTTP/WS network + MySQL + cookie browser simulation; AuthorizationRepositoryIT3 dùng fixture/principal tổng hợp rollback; PermissionPolicyTest27 dùng context thuần. Không tuyên bố permission đã test qua full lifecycle/Game Engine. Existing44 tests giữ nguyên. Chưa visual browser/LAN2 máy, rate limit/load, DB outage, auth UI hoặc account-delete push. Policy deadline/resource và action atomic integration thuộc task sở hữu; không mở rộng Task3.

Lựa chọn snapshot/scoring/Recovery/effect/streak/endReason G-01/G-02 và failure/idempotency kế hoạch cũ giữ nguyên. Task3 **DONE** theo nghiệm thu auth/permission ở mức cho phép; chỉ bàn giao review, không tự triển khai Task4.

## 15. TASK 4 — Quiz CRUD, image storage và sample data

Đã đọc lại TASKS.md/Quy tắc chung/Overview, status, architecture/freeze§13–14, canonical REST/WS và source Quiz/Question/JPA/security/build/resources/tests. Task2.5 có evidence đủ; baseline Task3 80 tests kiểm tra lại trước sửa đạt. Không scaffold/upgrade/đổi stack; không có mâu thuẫn gameplay mới. Proposal/Technical Design V2/tài liệu môn vẫn thiếu; không claim đã đọc hoặc đạt môn.

| Decision | Value / status | Reason / evidence |
|---|---|---|
| REST Quiz | POST/GET list/GET byid/PUT/DELETE /api/quizzes, session/CSRF / FROZEN implementation | Feature quiz/controller/service/DTO/entity/repo/enums; dùng AuthService active Principal. Contract canonical ghi field/status/scope thật |
| Metadata/Owner view | DTO metadata riêng vs Owner questions/correctAnswer / FROZEN | PUBLIC non-owner chỉ metadata; PRIVATE chỉ Owner selection/read/manage. list luôn metadata; defensive JsonIgnore Question.correctOption; network/serializer tests |
| Create/edit limits | 1–50 câu để biên soạn, content5000/options2000/title200 / FROZEN kỹ thuật | Không thêm gameplay: trận vẫn10–50 và Start kiểm tra Task8. Không cho body vượt MySQL TEXT với giới hạn đã chọn;4 enum keys +1 correctAnswer |
| Edit aggregate | PUT full replacement + PESSIMISTIC_WRITE Quiz + expected revision / FROZEN | Một concurrent PUT thành công/một409; question-only edit cũng tăng version. Giữ @Version/BOM, touch revision bằng bulk JPA khi parent không dirty rồi refresh managed entity |
| Question generations | Soft-retire toàn bộ câu active, insert với max(order_index)+1..N / FROZEN | Tái dùng UNIQUE hiện có kể cả deleted, không xóa/sửa source Question của lịch sử. Owner position1..N tách allocator DB; giữ FK và snapshot |
| Delete | Quiz/câu soft-delete, không cascade/history/blob delete / FROZEN | Native MySQL fixture với GAME_QUESTION giữ content/options/correctAnswer/image; edit/delete không đụng snapshot |
| Image storage | Local PNG canonical, sha256 reference per Quiz, hard-link publish không overwrite / FROZEN | Giữ quyết định ảnh local bất biến từ Task0/Task2 sha256 column. PNG/JPEG2MiB, dimensions<=4096/tổng4MP, request3MiB; không trust filename/MIME hoặc remote URLs |
| Image auth | Owner upload/read; non-owner chỉ released snapshot khi là GameMember / FROZEN media behavior | Native read-only projection trong QuestionRepository kiểm tra game/quiz/member/ref/openedAt/phase. Không import game source từ quiz để gây vòng; không Game Engine/WS adapter mới |
| Image retention | Không tự cleanup blobs sau edit/delete/fail attach / FROZEN phần hiện có | Tránh xóa file lịch sử; orphan uploads giữ lại. Future cleanup cần tham chiếu DB, không nằm Task4 |
| Sample bootstrap | Opt-in mysql,sample-data + QUIZ_DEMO_PASSWORD, transaction/idempotent/no overwrite / FROZEN | demo_author khác demo_player1..3, một PUBLIC Quiz10 câu; test seed2 lần rollback + thật JAR run/4 Login trên quizz |
| Author participation | QuizAccessPolicy.requireParticipation author không PLAYER, SPECTATOR được / IMPLEMENTED policy | Host vẫn Room permission. requireUsable cho organizer chọn PUBLIC/PRIVATE; roster participation enforcement thực ở Task5/8 PENDING, không tạo fake Room/Start API |

Spring Data JPA thực3.5.13 và Spring Framework6.2.19 từ BOM; đọc [locking3.5](https://docs.spring.io/spring-data/jpa/reference/3.5/jpa/locking.html), [multipart6.2](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-methods/multipart-forms.html). Không chọn API từ major mới rồi nâng dependency. QuizRepository.findLockedById dùng PESSIMISTIC_WRITE; native media read projection giữ dependency direction game → quiz enums, quiz → auth/user qua service, không quiz → game Java service/WS. auth không import quiz service; không vòng DI.

Spring MVC multipart max size parse fail trước xác định controller: QuizUploadExceptionHandler thuộc quiz/controller có advice không package-scope nhưng chỉ xử lý MaxUploadSizeExceededException, không lấy ownership của auth/DB errors. Các Quiz exceptions khác advice scope quiz/controller. GET/POST/PUT/DELETE Quiz được SecurityConfiguration allow authenticated theo method/path, CSRF không tắt. WS behavior không đổi nên không tạo diff websocket-contract chỉ vì Task4.

Image root cấu hình QUIZ_IMAGE_DIRECTORY mặc định data/quiz-images, runtime directory gitignored; test root target/task4-test-images riêng. File tạm được ghi hoàn chỉnh, hard link publish tênhash không thay thế file đã có, verify bytes/hash, xóa file tạm trong finally; GET verifyhash/nosniff/no-store. NTFS thực trên máy hỗ trợhardlink; filesystem không hỗ trợ trả503 IMAGE_UNAVAILABLE, không tự chuyển Cloud/storage engine. Lưu trữ/quyền truy cập ảnh đã code, không bỏ optional image. Snapshot game giữ quizId và imageRef để tái dựng URL; lịch sử/media đã mở đọc được sau Quiz private/delete, câu tương lai không đọc được.

V1/V2/MySQL schema giữ nguyên; không migration Task4 hoặc sample migration. application.yml thêm multipart/image/sample config, config mẫu/.gitignore/README cập nhật trong scope. Entity/repo User không nhân đôi; Question chỉ JsonIgnore bảo vệ serializer, ORM mapping không đổi. Phần source/build/TASKS/gameplay/migration đã freeze có manifest0 file thay đổi. Game/scoring/Recovery/effect mới/G-01/G-02 không đổi.

**Evidence cuối:**89 tests pass (60 thường +29 MySQL IT), fail/error/skip0; 5 QuizNetworkIT gồm HTTP auth/permission/validation, race revision, immutable filesystem/media permissions, snapshots sau edit/delete;1 SampleDataIT rollback,3 policy/serializer. 80 tests cũ vẫn đạt. SQL dump normalized khớp Task3, Flyway source/checksums/history giữ nguyên. Test DB không User/Quiz còn lại; runtime quizz chủ động seed4 User/1 Quiz/10 Question,4 Login và viewOwner/public metadata thực đạt, JPA11 repos/readiness200/MySQL UP.

Runtime review JAR PID78388 tại localhost8080, profilemysql,sample-data; passworddemo trong README là credential mẫu tự tạo, không MySQL password. Seed không overwrites tài khoản đã có/password hoặc Quiz, chỉ create thiếu với checks; defaultmysql không seed. Nếu sourceDB user/titledemo bị sửa/xung đột, seed fail và rollback thay vì reset người dùng.

Task4 **DONE** theo scope CRUD/visibility/image/sample; chưa UI, LAN2máy, full lifecycle/Author Start, stress/load/DB outage hoặc cleanup orphan. Các phần đó chưa kiểm chứng, không dùng fixture game tối giản thay bằng chứng một trận. Task5/8 phải dùng guardauthor/visibility và Start snapshot trong transaction/khóa Quiz để không đọc nửa generation. Dừng review; không tự Task5/commit/push.

## 16. TASK 5 — Waiting Room, membership và pregame boundary

Đọc TASKS/Quy tắc chung/Overview, docs hiện có/canonical contracts và source thật trước sửa. Architecture Task2.5 có evidence44 tests/JPA/JAR/schema giữ nguyên, Task4 baseline89 tests kiểm tra lại đạt. Không AGENTS trên đĩa; tiếng Việt theo chat. Proposal/Technical Design V2/tài liệu môn vẫn thiếu, không claim đã đọc/đạt môn. Không mâu thuẫn gameplay mới; giữ G-01/G-02/bảng điểm/Recovery/effect cấp sau câu/streak.

| Decision | Value/status | Lý do/evidence |
|---|---|---|
| Ownership | Room persistence/use-cases/REST DTO ở room; wire/parser/adapter ở realtime; auth transport cũ / FROZEN | Không duplicate Entity/Account/repository, không global-layer, no concrete WS dependency từ Room service |
| Kênh | REST Create/List/Get/by-code/Edit/Open/Close; WS Join/Leave/Remove/Subscribe/RoomUpdated / FROZEN | Hợp Overview§7, helper preview thực để lookup RoomID bằng code; không REST alias/endpoint giả/game message |
| Host/membership | Host luôn có JOINED row; nhiều Draft; PLAYER/SPECTATOR trước Start / FROZEN | Create atomically tạo Room+Host; dùng QuizAccessPolicy existing, author chỉ SPECTATOR |
| Leave/rejoin | LEFT + timestamp, rejoin cùng ID; Remove không ban / FROZEN | Giữ UNIQUE(room,user)/lịch sử GameMember và method leave/rejoin Task2; test WS+MySQL |
| Host Leave/Close | Host không tự Leave/Remove; Close DRAFT/WAITING; CLOSED terminal giữ JOINED roster cuối / FROZEN | Không chuyển Host ngầm; đóng ACTIVE bị reject để không mất đường Cancel. Source không yêu cầu transfer/ban; chọn operation quản trị tối giản, không thêm điểm/gameplay |
| ACTIVE guards | Config/Quiz/participation/Join/Leave/Remove/Close không đổi; disconnect chỉ binding / FROZEN phần Room | Roster bất biến; chưa support thêm Spectator vào ACTIVE vì cần lifecycle/GameMember/UAG ngoài Task5. Các state ACTIVE fixtures không phải Start thật |
| Quiz scope | Organizer chọn PUBLIC/ownPRIVATE; người Join chỉ cần code, không cần own Quiz / FROZEN | Overview§2/6: người chơi không cần sở hữu Quiz. Selected Quiz lock/usable bởi Host; Author guard toàn roster khi đổi Quiz/participation |
| Limits | maxPlayers3..100, total100 JOINED inclSpectator, duration>0ms, decision5000ms / FROZEN API kỹ thuật | Giới hạn snapshot/capacity một Server; không đổi min3 thực chơi và10–50 câu kiểm tra Start Task8 |
| Room revision | Lock Room PESSIMISTIC_WRITE; @Version + touchRevision nếu chỉ roster/no dirty config / FROZEN | Mỗi mutation tăng một, no-op same participation/Subscribe không tăng; event snapshot cùng revision thật sau flush/commit |
| Serialization | RoomBoundary fair lock/admission queue, 1 processing+64 waiting/scope, lockwait5s / FROZEN pregame | Dùng request threads sẵn, không dedicated thread/Room; config/Join/Leave/Remove chung thứ tự đến khi outbound xong. Task7 ingress/timer/Game workers chưa code |
| Tx→runtime | Enter boundary trước transaction; RoomService transactional5s trả sau commit, lưu receipt rồi ACK/event / FROZEN | Boundary reject outer transaction; RoomChanged Spring domain event không wire/WS. Tests DB revision/membership thấy trước broadcast và FK rollback không ACK/event |
| Request scope | (User,ROOM,roomId,requestId); Create(User,USER_CREATE_ROOM,selfId,requestId) / FROZEN pregame | Start Task8 phải dùng ROOM trước có GameID và cùng boundary; Create scope riêng để retry không sinh Draft thứ hai |
| Fingerprint | SHA256 canonical type/fulltarget/index/payload, sort object keys, giữ arrays / FROZEN | Strict new Room JSON parser, indexnull; cùng key khác fingerprint INVALID_REQUEST_ID. Actor chỉ từ Principal, Remove userId là target |
| Replay/retention | Authorize trước cache, replay trước state/revision; success only cache; Room tới CLOSED+10m; Create10m; sweeper60s / FROZEN pregame | LEFT Leave receipt replay riêng actor; Join biết code replay dữ liệu actor đã nhận, không tự rejoin. Business rejection không cache; không restart replay guarantee |
| Memory capacity | 512scope,1024 global committed/reserved receipts,32subscriptions/socket / FROZEN pregame | Cụ thể hóa proposal4096/User/Room thành global cap để bounded memory nhóm sinh viên; không evict live. Chặn trước side effect, vẫn replay/GET khi admission available; Game cap Task9 chưa freeze |
| Waiting resubscribe | SUBSCRIBE_ROOM trả full Room; retry Join/Subscribe còn JOINED có ACK cũ + private current RoomUpdated / FROZEN | Lấy fresh snapshot/bind trong cùng boundary, không mất event xen giữa. Revision thấp không ghi đè snapshot mới; chưa Game reconnect/socket replacement Task10 |
| Dữ liệu outbound | JOINED RoomResponse metadata/config/roster; LEFT nhận revocation tối thiểu; Leave ACK chỉleft / FROZEN | Không câu/options/correctAnswer/imageRef/password/Player/game phase giả; PRIVATE selected metadata chỉ joined, preview không Quiz/roster |
| Failure | Room write một attempt, DB/Tx lỗi fence mutation scope tới restart; read/replay đã có vẫn được phép / FROZEN pregame | Không retry mù commit ambiguous; rollback không ACCEPT/event; socket fail không undo/repeat DB. Full Game bounded3-attempt failure/startup cleanup vẫn kế hoạch Task8/11B |
| Outbound | ConcurrentWebSocketSessionDecorator send5s/buffer256KiB + Tomcat blocking5s / FROZEN implementation | Serialize nhiều Room/ACK cùng socket; auth/logout giữ cũ. Chưa slow-reader/flood/load experiment |
| ACTIVE Game uniqueness | Task2 generated UNIQUE(active_room_id), UAG PK(user_id) inclSpectator / EXISTING verified | MySqlSchemaIT thật vẫn pass; Task5 không tạo GameSession/UAG. Start cùng/chéo Room phải lockRoom→Quiz→Users tăngID + atomic snapshot/UAG ở Task8 |

Đã đối chiếu API theo dependency thật Framework6.2.19/BOM hiện có: [Spring6.2 WebSocket API](https://docs.spring.io/spring-framework/reference/6.2/web/websocket/server.html) yêu cầu serialize send; constructor3 args của decorator có trong JAR hiện có. [Tomcat10.1 WebSocket](https://tomcat.apache.org/tomcat-10.1-doc/web-socket-howto.html) hỗ trợ BLOCKING_SEND_TIMEOUT trên session user properties. Không lấy major mới để upgrade. Room→quiz repository/policy và game repository (chỉ detect ACTIVE) là dependency use-case; không Room service→realtime/handler. realtime orchestration→Room/Auth, connection listener→domain RoomChanged, no circular beans/allow-circular-references.

RoomBoundary khóa theo ROOM ID dùng chung cả REST và WS; pre-authorization ngoài boundary tránh cấp scope cho ID không tồn tại/không quyền, recheck dưới lock trước cache/operation. MySQL default đã query REPEATABLE-READ; List snapshot cùng read-only transaction, Get/Subscribe/mutation dưới boundary. Current Room snapshot không phải Game phase snapshot. Handler kiểm tra account/session từng frame và sau chờ lock; outbound checks session/account trước Room broadcast. Removed socket chỉ nhận revocation, mọi subscription của User đó bị gỡ khi listener xử lý mutation. Socket disconnect/logout không đổi membership/UAG/game history.

Không nâng dependency, sửa frontend/scoring/entity mapping, schema hay migration. 25 production class mới +4 class cũ sửa trong feature được giao; tổng110 production/13 test files, vẫn11 Entity/11 Repository. Canonical REST/WS được cập nhật đồng bộ; TASKS/gameplay/pom/resources/migration giữ nguyên. Evidence: full verify103 tests (65 thường +38 MySQL IT), fail/error/skip0; Task5 riêng5 pure runtime +9 network cases. JAR mới localhost8080 profilemysql PID61208, JPA validate/MySQL UP; Login/RoomList/Logout200/200/204. Không claim UI/LAN2 máy/Start/gameplay/uncertain COMMIT outage/flood đã test.

Task5 **DONE** theo nghiệm thu Waiting Room/networking/serialization preparation. Task6 chưa bắt đầu; dừng review, không commit/push. Phần Start boundary/roster snapshot/UAG cross-room, timer/deadline, Game failure/Cancel/HostELIM lifecycle nằm đúng task sau, không bổ sung engine để kiểm quyền trong Task5.

## 17. TASK 6 — Pure Score Engine và Ranking Engine

Đã đọc TASKS/Quy tắc chung, đầy đủ Cách chơi Overview §8 và thứ tự scoring §9, status/decisions/architecture, source entities/enums/snapshot/tests/build và hai contract canonical. Overview §1–13 khớp nguyên văn extract; không sửa nguồn. Task 2.5 có evidence đạt; baseline Task 5 kiểm tra lại 103 tests pass. Không AGENTS trên đĩa; tiếng Việt theo chat. Proposal, Technical Design V2, Topics, Instruction, Submission vẫn thiếu; không xác nhận đã đọc hoặc đạt môn. Không business conflict mới; G-01/G-02 giữ nguyên.

| Decision | Value / status | Lý do / bằng chứng |
|---|---|---|
| Package/API | game/engine: ScoreEngine, RankingEngine, PlayerScore, ScoringInput, ScoringResult / FROZEN | Records immutable, calculator final; chỉ Java standard library, game enums và plain rules record. Không Spring bean, Entity, Repository, wire hoặc WS |
| Bảng điểm | GameplayRulesSnapshot truyền vào constructor / FROZEN | Tái sử dụng snapshot version 1 Task 2, không tạo bảng điểm thứ hai trong engine. Calculate chỉ đọc snapshot/input, không factory/Quiz/DB/clock |
| Version support | Chỉ luật snapshot v1 đã freeze / FROZEN | Constructor đối chiếu factory v1 và reject snapshot khác luật đó, không âm thầm chấm bằng bảng bị thay. Đổi luật tương lai cần version support rõ ràng, không sửa historical snapshot |
| State | PlayerScore(state,score,totalAnswerTimeMs,winStreak,loseStreak,momentum,recovery) / FROZEN | Thuần scoring, không identity/phase/connection/resources/ORM. Validate state/time/streak nhất quán; initialPlayer lấy initial20 từ snapshot |
| Input | ScoringInput(player,outcome,spin nullable,starSelected,answeredTimeMs nullable) / FROZEN | Outcome Server xác định khi scoring. ACCEPTED_UNSCORED bị reject; null Spin chọn normal/StarOnly |
| Spin/Star | Nhận lựa chọn đã chấp nhận từ caller, reject HARDSHIP+Star / FROZEN | Không random/reroll, pool allocation hoặc resource consumption. Phase/quyền/tài nguyên/Spin-before-Star thuộc Task 8/9 |
| Thời gian | Correct/Wrong elapsed >=0 và <duration; NO_ANSWER elapsed null, cộng full duration / FROZEN | Milliseconds do Server caller đo, không đọc clock/network trong engine. Chỉ consistency validation, không chứng minh ingress/deadline Task 7/9 |
| Scoring order | Table → effect cũ → score/time → elimination → alive streak/grants / FROZEN | Overview §9/Task 6. Return state mới, không mutate input/persist. Output có base/final delta/time/consumed/granted/newElim flags |
| Recovery | BasePenalty âm của Wrong/NoAnswer: min(base+3,0), consume kể cả final0 / FROZEN | Correct/base0 giữ effect. Không đổi outcome theo dấu final delta; Wrong final0 vẫn tăng loseStreak nếu sống |
| Momentum | Correct +3 rồi consume; Wrong/NoAnswer giữ / FROZEN | Chỉ effect có trước câu. Câu tạo streak cấp effect sau score/alive check, không tác động ngược chính câu đó |
| Streak | Threshold5, reset opposite; NO_ANSWER reset cả hai khi sống / FROZEN | Nếu mới eliminated thì giữ streak đầu câu, không update/reset/grant, kể cả NoAnswer. Theo nguồn §9: loại và dừng streak. Existing effect đã tiêu thụ trước elimination vẫn ghi đúng |
| Nonstack/coexistence | Boolean tối đa một mỗi effect; Momentum/Recovery cùng tồn tại / FROZEN | Consume effect cũ rồi grant ở threshold là một effect cho câu sau; không áp dụng bonus/reduction hai lần trong câu hiện tại |
| Eliminated input | Reject IllegalStateException, không output mới / FROZEN | Caller skip eliminated trong roster scoring; score/time/streak đã đóng băng. Câu gây loại vẫn tính score/time trước khi dừng streak |
| Output/error | Immutable ScoringResult, Math.addExact cho score/time / FROZEN kỹ thuật | Chặn overflow, không trả partial state. Invalid input reject; chưa mapping sang wire/lifecycle vì không endpoint mới |
| Ranking | Score DESC/time ASC, competition 1,1,3, gồm eliminated Players / FROZEN | Entry(userId,score,totalAnswerTimeMs) → immutable RankedPlayer. UserId chỉ để trình bày nhóm đồng hạng ổn định, không đổi rank hoặc luật Winner |
| Ranking boundary | Không quyết định endReason/Winner, caller chỉ đưa Player entries / FROZEN | Không filter eliminated hoặc thêm Spectator. ALL_ELIM vẫn có ranking; official Winner/Cancel/G-01/G-02 thuộc lifecycle Task 8/11B |
| Orchestration | Chưa có game/service để gọi engine / PENDING Task 8 | Không tạo transaction/lifecycle mới. Chưa persist result vào PlayerSession/Answer hay broadcast toàn câu |
| Communication | REST/WS code và contracts không đổi / FROZEN phần hiện có | Task 6 chỉ pure engine; không fake scoring endpoint/Game event |

GameplayRulesSnapshot là persisted record dữ liệu thuần, không JPA Entity/public REST DTO. Engine biên dịch standalone cùng snapshot và 3 enum bằng javac21, không framework classpath: 15 class files gồm nested/synthetic. JAR có 8 class files trong package engine. Giữ Java21/JUnit5/Failsafe/BOM hiện có; không thêm dependency, package hoặc interface rỗng. Dependency engine → game.dto/enums, không persistence/realtime/Spring.

Ví dụ từ source: initial20 → năm Wrong thường lần lượt16/12/8/4/0; vẫn sống, nhận Recovery ở câu5 và reset loseStreak0. Wrong tiếp theo base-4→final-1, score-1/eliminated, consume Recovery và dừng streak. Năm Correct20→30/40/50/60/70 chỉ cấp Momentum ở câu5; câu6 +13→83. SAFE+Star Wrong với Recovery final0 vẫn đếm Wrong; SAFE+Star NoAnswer base0 giữ Recovery/reset streak. Có oldMomentum +winStreak4 thì Correct hiện tại chỉ nhận bonus3 của effect cũ; consume cũ rồi cấp mới cho câu sau, không bonus6.

Expected gold trong ScoreEngineTest chép số từ Overview §8.2/8.6/8.7 và các ví dụ §8.4/8.5. Modifier expected là số tính tay literal; không gọi production table/calculate hoặc copy modifier formula để sinh expected. 39 cases bảng thường +39 cases cùng cả hai effect +13 cases streak mọi mode, 5 Recovery boundaries, sequence/elimination/time/validation/overflow. RankingTest dùng order/ranks literal theo §8.8. Total **119 engine tests pass** (113 Score +6 Ranking); toàn repo **222** (184 thường +38 MySQL IT), fail/error/skip0.

Determinism không phải exactly-once: cùng input → cùng output; engine không giữ receipt/questionID hoặc tự tránh chấm trùng. Task 8 phải xác định outcome từ Answer/Question snapshot, skip eliminated, chấm toàn câu vào state tạm, persist atomic dưới processor/transaction rồi publish sau commit. Answer ACCEPT chưa chấm khi Cancel không được chuyển thành Wrong/NoAnswer để gọi engine. Chưa có orchestration này ở Task 6.

MySQL8.0.45 thật: regression constraints/JPA/HTTP/WS cũ vẫn đạt; normalized schema hai DB khớp Task 5, V1/V2 source/history giữ nguyên, fixture đã dọn sạch. Pure engine không dùng DB nên đây không phải bằng chứng scoring transaction/lifecycle trên MySQL. JAR mới PID57628/readiness200/MySQL UP; gameplay NOT_IMPLEMENTED vẫn đúng vì chưa có network/lifecycle trận. Manifest136 file cũ, 0 thay đổi; chỉ thêm 5 production engine +2 test và cập nhật status/decisions/checklist/README. **Task 6 DONE; dừng review, chưa Task 7, không commit/push.**

## 18. TASK 7 — Ingress, clock, worker pool và cleanup

| Decision | Value / status | Lý do / boundary |
|---|---|---|
| Ingress / FROZEN | SessionQueue + Ingress; timestamp/sequence/enqueue cùng monitor per Session; thành công trả Submission | Chờ lock chưa ingress. Timer dùng cùng FIFO; handler ngoài lock, không dùng client time. Request đã ingress trước deadline không bị timer vượt |
| Clock / FROZEN | Một ServerClock dùng chung runtime/handler, nanoTime elapsed ms và currentTimeMillis epoch; Sample inject cho test | PhaseWindow lấy clock mới lúc thực sự mở; openedAt <= receivedAt < deadline. Epoch deadline = epoch lúc mở + duration, chỉ hiển thị; epoch jump không đổi hạn. Timer early wakeup reschedule |
| Worker / FROZEN | Default4 worker, 512 Session, 64 command chờ + 1 reserved timer/Session; batch32 rồi nhường; constructor limits cho test | Một runner/Session, ready queue bounded512, không CallerRuns. Test FIFO, A blocked/B progress và fairness khi pool đầy |
| Timer / FROZEN | gameId/generation/question/phase/token/deadline; một timer live, cancel/remove khi thay/retire/consume | Shared ScheduledThreadPoolExecutor1, removeOnCancelPolicy; identity/generation loại stale và lặp. Game giữ window/gate; scheduler chỉ enqueue |
| Retention/cleanup / FROZEN | ACTIVE + 600000ms sau retire theo §5; retire chỉ từ handler sau terminal commit ở Task 8. Cleanup terminal expired/idle, periodic bởi lifecycle tương lai; register cũng reclaim | Giữ handler cho authorized retry/replay/rejection, chưa receipt cache Task 9. Không cleanup khi đang xử lý/chờ; old key/timer no-op sau ID reuse. close cancel/reject pending/interrupt pool, không cam kết rollback DB đang chạy |
| Callback/failure / FROZEN | onCommand/onTimer nhỏ; armTimer/retire chỉ từ owner handler. Không await submission của chính Session. Unexpected exception fence/fail pending, không tự retry | Expected business rejection xử lý trong handler. Failed Session giữ bounded tới shutdown, failure lifecycle thuộc Task 8. QuestionCloseGate nhận roster PLAYING gồm disconnected; recordValidAnswer sau validation/commit, chỉ cấp một lần callback close, không thay scoring transaction/retry |

Evidence: 21 Task 7 tests và toàn bộ 205 tests mặc định đạt, fail/error/skip0; compile + JAR lớp riêng đạt. Không đổi wiring/config/persistence/Score Engine/communication nên không chạy lại MySQL IT hoặc startup mysql Server. Handler đếm callback không chứng minh full trận/exactly-once DB. Gameplay/stack/schema/contracts cũ giữ nguyên.

## 19. TASK 8 — Start, lifecycle và transaction failure policy

Không đổi stack/dependency/schema/Score Engine. G-01/G-02 đã có xác nhận người dùng (mục8 và chat): ALL_ELIMINATED/ONE_SURVIVOR trước COMPLETED; mọi rank1 là Winner cả ALL_ELIMINATED. SERVER_INTERRUPTED không Winner. Không còn PENDING ở hai nhánh này; không sửa Overview gốc/TASKS.

| Quyết định mới / FROZEN phần đã triển khai | Giá trị / lý do |
|---|---|
| Start boundary/handoff | RoomOperations/RoomBoundary Task5, scope User/ROOM/id/requestId, fingerprint START_GAME/target/null/revision+N chung REST/WS. Capacity reservation trước transaction không tạo runtime Game; receipt giữ sau commit, install processor một lần. Handoff lỗi compensate hữu hạn, không Start lại |
| Lock/isolation | READ_COMMITTED, transaction timeout5s; Room → Quiz (Start) hoặc Game (lifecycle) → User theo ID tăng dần. Game refresh PESSIMISTIC_WRITE sau Room lock tránh snapshot cũ khi score cùng câu. UAG UNIQUE là authority gồm Spectator; delete+flush UAG trước Game FINISHED vì composite FK tới status ACTIVE |
| Snapshot/resources | Start một transaction lưu roster bất biến, GameQuestion shuffled1..N/config đầy đủ/Quiz author+title/nội dung/options/đáp án/imageRef; initial20, N/10 Spin và 1 Star. Tái sử dụng schema/enums/repositories; dùng instance trả về saveAndFlush cho entity @Version |
| Ownership | GameTransactions/Projection/Lifecycle trong game/service; queue/time adapter GameRuntime/GameOperations trong realtime/session, wiring clock/scheduler realtime/timer. GameLifecycle chỉ dùng callback/Time/PhaseWindow và dữ liệu copied, không biết concrete queue/timer/WS. Controller/DTO của Game thuộc game |
| Commit/scoring | Engine tính tất cả PLAYING vào state tạm trước managed mutation. Atomic Answer/PlayerSession/effect before+after/elimination/rank/Room/UAG; scoredAt đánh dấu một lần dưới DB lock. Copied view chỉ lắp vào volatile runtime view/publish sau transaction proxy commit; không chấm lại vì outbound lỗi |
| Phase/time | DECISION→QUESTION_OPEN→QUESTION_CLOSED→SCORING→RESULT; RESULT publish rồi enqueue DECISION tiếp, không invent duration. Clock mới sau queue/DB lock lúc mở phase; nội bộ monotonic, epoch display clamp >=startedAt nếu clock lùi. Accepted answer elapsed từ monotonic ingress, receivedEpoch từ phase anchor; offline vẫn thuộc close gate |
| Retry | Phase/accept/scoring rollback chắc chắn (TransientDataAccessException/CannotCreateTransactionException) tối đa3 attempts,100/300ms. SessionQueue.defer continuation của cùng operation, giữ ingress sau nó/FIFO, nhường worker; timer/command vẫn ingress với thời gian thật. Nontransient/commit chưa rõ không blind scoring retry |
| Terminal runtime/error | UNAVAILABLE + stop timer + GAME_UNAVAILABLE không phải persisted success. Cleanup SERVER_INTERRUPTED tối đa3 attempts100/300ms; commit rồi mới GAME_END/retire/Room WAITING/release UAG. Mất DB tiếp: cleanupPending=true, giữ bounded occupancy/runtime, khôi phục MySQL/restart; không kẹt SCORING runtime hay fake Winner |
| Handoff/startup cleanup | Compensate Start đã commit nhưng install thất bại tối đa3 lần trên request thread; không retry Start transaction. Startup cleanup ACTIVE cũ tối đa3 lần trước ready/Start; không commit được thì fail startup. Backoff startup/handoff trên request/startup thread, không Session worker. Task11B hoàn thiện policy, không phải nơi đầu tiên có cleanup |
| Retention/read/transport | Retire chỉ sau terminal commit,10 phút Task7; sweeper60s release queue/timer/capacity, terminal snapshot sau retention đọc DB. REST Start+Game snapshot và WS START/lifecycle public events đã code; gameplay command/receipt/auth/context/reconnect Task9/10 vẫn chưa expose |

Evidence: GameLifecycleIT18 đạt; diff cuối `verify` 206 unit +56 MySQL IT =262 tests đạt, fail/error/skip0; JAR readiness200/MySQL8.0.45 UP/LIFECYCLE_ONLY. MySQL thật cho transaction/race/rollback; injection có kiểm soát để buộc lỗi sau SQL flush hoặc cleanup connection, không thay DB bằng mock/H2. Không tuyên bố đã cắt MySQL service giữa COMMIT hoặc đã hoàn thành full gameplay WS/UI. Giới hạn/log tại project-status Task8; Task8 DONE, dừng review.

## 20. TASK 9 — Gameplay WS và replay cache

Giữ transport/cookie/Origin/Room boundary/queue/timer/failure policy Task3/5/7/8; schema/dependency/Score Engine/Overview/G-01/G-02 không đổi.

| Quyết định mới / FROZEN phần đã triển khai | Giá trị / lý do |
|---|---|
| Wire/ownership | Cùng `/ws`, envelope v1/registry; Waiting adapter route ANSWER/USE_SPIN/USE_STAR tới GameCommandAdapter/Parser, DTO wire trong realtime/message. GameplayAction/resources/persistence trong game/service/enums, không message dependency trong game. ACK type ANSWER mang semantic ANSWER_ACK; giữ QUESTION_START/QUESTION_RESULT/GAME_END đã có |
| Replay boundary | Node GameRuntime sở hữu GameReplayCache; User+Game+UUID, fingerprint cũ bao gồm type/target/index/payload canonical. Processor: revalidate auth/membership → lookup/mismatch → replay → admission → business/transaction → runtime/cached ACK sau commit → send. Cache hit không validate phase/PlayerState hiện tại, không subscription admission; không replay nếu identity invalid |
| Cache capacity/retention | 128/User/Game,8192/Game,65536 toàn Server; reserve slot trước side effect, rollback trả slot, không business-error cache/không ACCEPT trước commit. Thay mức4096/User/Game dự kiến bằng128 (max56 successful gameplay/Player), giới hạn tổng memory. ACTIVE+10m sau retire; không clear End, clear chỉ queue expired/idle/shutdown, không evict trong cam kết. Không restart replay guarantee |
| Spin action | SpinSelector trong game/service, SecureRandom weighted tổng pool còn lại/config snapshot. Chọn một lần sau guards, capture effect qua 3 TX retries; useSpin persist credit/pool/currentSpin, useStar persist availability/selection. Không random trong scoring hoặc lại khi retry. Star-only khóa Spin, HARDSHIP khóa Star, one Spin/question |
| Answer | GameLifecycle/Transactions cũ, ACK private chỉ lựa chọn/resources/ACCEPTED_UNSCORED; không correctness/scoreDelta. One Answer bảo vệ cả queue và DB unique, dù requestId khác. Ingress deadline guard trước TX; old accepted ACK replay trước guard mới |
| Result/events | Thêm streak/time/state/elimination vào Answer.result_snapshot JSON đã có; không migration. GameProjection trả effect before/after/consumed/granted +streak; GameLifecycleEvent mang copied private Player map và terminal Room snapshot. Registry cá nhân hóa đúng recipient/commit, không query phase mới để ghép event cũ, không lộ pool/selection người khác |
| Event ordering | QUESTION_RESULT → PLAYER_ELIMINATED nếu có → LEADERBOARD_UPDATED → GAME_END nếu terminal → ROOM_UPDATED WAITING. Cùng Game revision có thể nhiều event, eventId phân biệt. Room snapshot copy trong terminal TX, fan-out sau commit; Room revision khác Game revision. QUESTION_START/DECISION/ACK/end có target/index/deadline/server time canonical |
| Readiness | mysql ready báo GAMEPLAY_WS thay LIFECYCLE_ONLY; chỉ capability Answer/Spin/Star đã code, không claim UI/Cancel/History/reconnect |

Evidence ở project-status Task9: diff cuối `verify` BUILD SUCCESS, **216 unit +67 MySQL integration =283 tests**, failure/error/skip0; JAR readiness HTTP200/MySQL8.0.45 UP/GAMEPLAY_WS. MySQL thật/WS/cookie/concurrent/rollback/TTL, expected điểm từ Overview; sampler/clock/latches điều khiển cho test, không mock/H2 thay MySQL. Test fixture dùng chung giữa Task8 và9, giữ đầy đủ18 lifecycle tests/races cũ; chưa cắt MySQL vật lý giữa COMMIT, LAN/load/reconnect/UI. **Task9 DONE, dừng review.**

## 21. TASK 10 — Connection generation và reconnect

| Quyết định mới / FROZEN | Lý do / hành vi |
|---|---|
| Ownership | Registry `active.compute(User)` publish owner/generation nguyên tử sau kiểm tra auth; một socket authenticated/User trên `/ws` dùng chung Waiting/gameplay, không thêm mode/envelope. Counter int64 tăng trong process, không per-User map generation tồn tại vô hạn. AUTH_READY trả generation; previous owner nhận SESSION_REPLACED best effort/close4002 |
| Stale operation | Check current binding trong validation đầu processor là điểm cho phép bắt đầu; chưa bắt đầu thì reject trước receipt/side effect, operation đã bắt đầu tiếp tục finite TX retry/commit một lần. Send bỏ connection bị thay; socket mới replay User+Game receipt. Disconnect/revoke remove bằng exact binding, logout session cũ không gỡ socket session mới |
| Snapshot read boundary | RECONNECT target Game/index null/payload {}, strict v1; read fresh, requestId correlation không action receipt. GameRuntime dùng queue Task7 capture business snapshot GameLifecycle/GameProjection hiện có → subscribe → send ACK cùng turn, không timer/DB/gameplay writes. Host Spectator player=null; role trong immutable members theo AUTH_READY userId |
| Ordering/terminal | Snapshot và lifecycle Game events cùng processor/outbound, ACK snapshot trước event operation tiếp theo; revision theo target, same revision vẫn xử lý eventId khác nhau, ACK replay không rollback state. RECONNECT cleanup expired idle queue trước lookup; nếu FINISHED không runtime hoặc enqueue bị từ chối do terminal actor hết TTL/còn bận replay/cleanup race thì đọc DB immutable ngoài actor, không recreate actor/resources. Không fallback DB ACTIVE, không lộ future/correctAnswer trước scoring |
| Evidence boundary | Tests real cookie/raw WS/MySQL; clock/latches kiểm soát pending vs started/retry và snapshot RESULT trước DECISION. Unit registry chứng minh simultaneous ownership và stale disconnect/logout. Không claim UI/LAN/load, physical DB outage hay Server-crash recovery |

Evidence diff cuối: **220 unit +78 MySQL integration =298 tests**, failure/error/skip0; 11 GameReconnectNetworkIT và18 GameLifecycleIT đều đạt, giữ concurrent/retry FINISHED Task9. Packaging BUILD SUCCESS sau giải phóng khóa JAR review trên Windows; một Room fixture cập nhật theo replacement và nhóm Room9 chạy lại đạt. JAR readiness HTTP200/MySQL8.0.45 UP, fixture/UAG còn sót0. Chi tiết lệnh/log/giới hạn tại project-status Task10. **Task10 DONE, dừng review.**

## 22. TASK 11A — Web Client Account/Quiz/Room/Waiting

| Quyết định mới | Lý do / hành vi |
|---|---|
| Frontend/module/build | Giữ native ES modules; `static/client` chia UI/validation/network, hash routing, DOM text nodes không innerHTML userdata. Boot GET `/client/*.js` public; APIs giữ auth. Python stdlib kiểm tra graph/UTF-8/copy artifact, Maven đóng cùng JAR; không Node/dependency/framework mới |
| Auth/privacy | Same-origin fetch cookie/CSRF, làm mới CSRF sau Login; Me khi reload, Logout/401/WS4001 xóa state theo auth epoch và chặn REST response cũ. Không password/token storage; chỉ Owner render questions/correctAnswer và dùng endpoint ảnh đã có |
| Room networking/retry | WS thật, không polling roster. Snapshot/ACK/event dùng revision Room không giảm; cùng revision vẫn tiêu thụ. Mutation capture requestId/revision/payload một lần cho explicit retry, feedback giữ qua render/update. Quiz CRUD chưa idempotent: mất xác nhận không tự retry Create/PUT/Delete, hướng dẫn đọc lại dữ liệu |
| Replacement/LAN | AUTH_READY/SESSION_REPLACED/4002 từ transport Task10; socket cũ dừng, callbacks so đúng socket; chỉ nút explicit reconnect, không tranh owner. UUID dùng getRandomValues (không phụ thuộc randomUUID chỉ có secure context), URL REST/WS lấy cùng origin cho LAN |
| Start/entry boundary | Host role từ roster, Author PLAYER bị chặn và Server kiểm tra lại. Start chờ metadata/số câu, N10–50 và ít nhất3 PLAYER; request scope ROOM. ACK hoặc GAME_STARTED đi tới Game entry xác minh membership bằng snapshot REST; không render questions/results/gameplay/history ở Task11A |
| Browser evidence | Chrome headless profile riêng, bốn incognito context tách cookie, thực sự click/submit DOM + REST/raw WS/MySQL. Test làm mất một ACK thật để kiểm tra retry/replay; không API mock. Python/CDP stdlib chỉ tooling test, đóng đúng Chrome sở hữu; không dùng tabs/profile người dùng |

CDP dùng các API hiện có được đối chiếu tại [Target](https://chromedevtools.github.io/devtools-protocol/tot/Target/) và [Runtime](https://chromedevtools.github.io/devtools-protocol/tot/Runtime/). Evidence:15 unit frontend +22 smoke flow Chrome, SystemWebTest4/AuthNetworkIT6/RoomNetworkIT9 đạt; JAR/MySQL readiness200. Chi tiết/giới hạn tại project-status; không thay gameplay, schema, engine hay WS envelope. Task11A DONE, dừng review.

## 23. TASK 11B — History/Cancel và durable cleanup

Chốt trước triển khai: giữ nguyên policy Task8/§19, không framework retry mới. Cancel dùng cùng processor và cache User+Game+requestId của Task9; REST/WS cùng fingerprint CANCEL_GAME/GAME/null/{}. Host theo Room, không phụ thuộc PlayerState; scoring đang xử lý hoàn tất cả câu trước Cancel, Cancel trước Close dừng timer và không gọi score. Transaction terminal gồm standings, Game FINISHED/CANCELLED, Room WAITING và release UAG; runtime/cache/ACK/End chỉ sau commit, giữ retention10 phút.

Rollback chắc chắn retry tối đa3 lần100/300ms qua defer cũ; lỗi commit chưa rõ không blind retry. Exhaust → runtime UNAVAILABLE/stop timer/GAME_UNAVAILABLE, interrupt cleanup tối đa3 lần. Còn lỗi: cleanupPending, không ACCEPT/History thành công; phục hồi DB rồi restart để ApplicationRunner cũ cleanup ACTIVE. Cleanup durable chỉ báo GAME_END sau commit; không restore active engine sau crash.

History trong game/service/controller/DTO, chỉ GameMember bất biến được list/detail FINISHED (kể cả Spectator/LEFT hiện tại); Quiz Owner không thuộc roster không tự có quyền. Page0+, size1..100, sort finishedAt/id giảm. Detail chỉ câu đã mở, snapshot nguồn và timeline mọi Player đã tham gia câu; không lộ câu tương lai hoặc correctAnswer chưa chấm. ACCEPTED_UNSCORED giữ choice/receivedAt/elapsed, scoring fields/result null và không cộng elapsed vào total. Scored Answer bổ sung Spin/Star vào JSON result_snapshot hiện có, không migration; legacy thiếu Star ghi null thay vì đoán. Final snapshot thêm hasOfficialWinner derived: chỉ COMPLETED/ONE_SURVIVOR/ALL_ELIMINATED true; CANCELLED/SERVER_INTERRUPTED false/winners rỗng, vẫn standings.

History đọc transaction read-only REPEATABLE_READ để final state/timeline cùng snapshot DB. Cancel REST revalidate HTTP session/user khi processor bắt đầu, dùng chung strict command/receipt với WS; ACK Cancel chỉ có finalSnapshot, không tạo resource fields giả cho Spectator. Continuation mở phase đã enqueue phải no-op khi Game terminal/UNAVAILABLE. finishedAt clamp theo started/opened/received/scored timestamp đã commit để History không đảo thời gian khi wall clock lùi; duration/deadline vẫn monotonic như Task7.

Evidence Task11B:37 unit +52 MySQL integration =89 tests đạt, gồm12 History/Cancel cases và40 lifecycle/command/reconnect regression. SQL rollback sau durable mutations, finite exhaustion/pending cleanup và startup stale-state kiểm chứng thật; KILL đúng transaction connection trong DB test xác nhận mất kết nối thật, không dừng MySQL service. JAR startup/readiness200/MySQL8.0.45 UP; không migration/dependency/engine/frontend changes. Chưa kiểm chứng outage toàn dịch vụ giữa COMMIT/LAN/load/UI. Lệnh/log/giới hạn tại project-status; Task11B DONE, dừng review.

## 24. TASK 12 — Gameplay/Final/History Web Client

Giữ frontend/auth/transport Task11A; thêm game-state (envelope/guard/order thuần), game (view/controller) và history (REST view), không client/framework mới. Controller Game nhận lifecycle snapshot đã cá nhân hóa từ WS; initial REST read hỗ trợ loading, RECONNECT trong transport cũ xác lập subscription/snapshot trước khi enable action. Same revision được nhận, revision thấp bị bỏ; receipt chỉ patch resources riêng nếu cùng câu/revision hợp lệ. Pending frame cố định đến ACK hoặc lỗi definitive, retry không tạo UUID mới dù phase đã đổi. Không tính score/outcome/effect/ranking hoặc random Spin ở Client.

Countdown lấy deadlineEpochMs/serverTimeMs tại nhận state rồi trừ elapsed performance.now, chỉ cập nhật label; không tạo Close/scoring/timer nghiệp vụ. Dispose interval/controller khi rời Game/expire User. Giữ panel kết quả vừa chấm khi Server mở DECISION tiếp ngay, dùng results snapshot cho RESULT/FINISHED. Spectator player=null và Host eliminated đều render được; permission bật action theo Server phase/Player/resources + sync/connection, Cancel theo role Host. Final dùng hasOfficialWinner/winners từ Server; History chỉ render DTO đã authorize, unscored/legacy nullable thể hiện đúng nghĩa. Không đổi API/message canonical.

Tool browser plugin bootstrap lỗi trusted dependency path; dùng lại Python/CDP kiểm thử Chrome riêng của repo, bốn context tách cookie, không profile người dùng hay API mock. Lượt cuối32 unit +9 nhóm flow browser/MySQL thật đạt; build11 modules/13 assets, JAR/assets/readiness khớp,162 backend/build/task files giữ hash nên không lặp backend regression. HARDSHIP/RESULT snapshot/SERVER_INTERRUPTED có component test; reconnect live DECISION/OPEN/FINISHED, không claim UI outage/crash recovery. Lệnh/evidence/giới hạn tại project-status. Task12 DONE, dừng review.

## 25. TASK 13 — Kiểm chứng hệ thống

Không thêm architecture-test framework: dùng source/package checks, JPA Metamodel/11 repositories và Spring context thực tế với circular references=false. Snapshot assertions lấy field/nullability/privacy từ canonical contracts, gắn vào test REST/raw WS hiện có để không tạo test trùng. Multi-room test giữ SQL scoring A sau `answers.flush()` bằng latch theo transaction thread; B phải tiến triển/cleanup/replay độc lập. Repository spy `flush()` là interface, không gọi `callRealMethod`; SQL đã chạy qua persistence-context flush và transaction proxy vẫn commit/rollback thật. Clock/latch điều khiển thứ tự, timeout chỉ là bound khi test lỗi.

Giữ browser evidence Task12 khi hash backend và static/JAR không đổi; tăng assertion co-winners và chạy lại32 unit DOM trong Chrome. `-ReportName`/`--report-name` tùy chọn cho gameplay test lưu JSON trong target, default cũ giữ nguyên, tránh ghi đè evidence đã nghiệm thu. Full Maven verify/MySQL chạy một lượt sau focused tests; fail injection được assert riêng với business rejection. Không đổi quyết định runtime/gameplay/transport/retention. Kết quả và giới hạn tại project-status; Task13 DONE, dừng review.

## 26. TASK 14 — Auth tại Room boundary và broadcast

P1 tái hiện: request Start REST đã qua filter/preauthorization nhưng chờ Room lock; Logout hoàn tất trước admission vẫn tạo Game vì callback REST rỗng. Capture login session gốc bằng `AuthSessionRegistry.validation(HttpSession,userId)`, chạy `requireValid` tại Room boundary trước cache/replay/transaction; dùng cho Start và Room REST Create/Edit/Open/Close, không tin Authentication object đã capture như bằng chứng phiên vẫn còn sống. WS giữ callback session/generation cũ; trusted service ports không trở thành endpoint. Logout sau validation là operation đã bắt đầu, transaction vẫn được hoàn tất theo policy đã freeze. Không thay auth mechanism, queue/lock/receipt scope hoặc thêm retry framework.

P2 tái hiện: account không active lúc Game broadcast bị gộp với DB failure và đóng1011; registry nay tách AuthFailure→4001 SESSION_EXPIRED, DataAccessException→1011 AUTH_UNAVAILABLE, gỡ đúng binding và tiếp tục fan-out cho User khác. UI transport hiện có nhận4001→expired/Login, không cần sửa UI. Canonical WS cũng sửa dòng cũ liệt kê CANCEL_GAME là unsupported; không tạo message/envelope mới. Requirement→module→evidence cập nhật trong course checklist từ Overview; tài liệu môn gốc và experiment vẫn chưa có, không xác nhận đạt môn. Kết quả regression/giới hạn tại project-status.

## 27. TASK 15 — Baseline, phép đo và hồ sơ

Giữ production nguyên trạng; main ExperimentServer nằm test classpath/profileexperiment, không đóng JAR. Observer runtime future dùng System.nanoTime cho server processing gồm queue/service/commit/cache, trước adapter delivery. Baseline xóa committed receipts/counts/release budget (không close cache) trước giao wrappedACK; trial retry sau loss-injector đã nhận ACK nên lookup miss, business guards1Spin/q/Answer/q giữ nguyên. Reflection test-only fail nếu runtime/cache structure khác; ablation không phải no-cache implementation tối ưu hiệu năng. Reconnect baseline mở socket+SUBSCRIBE_ROOM, proposed RECONNECT/fullsnapshot; không endpoint/flag mặc định demo hoặc framework mới.

Client Python stdlib/cookie riêng Account đo perf_counter_ns send→receipt và open→apply snapshot vào harness model; không gọi thời gian Server sent là Client resync/không claim browser paint.30trial/scenario,3round/mức3/5/10/20,1Roomactive; sequentialmodes/dataJIT/CPUshared là threats. Workloadseed cố định, SecureRandomSpin/UUID không seeded; rawACK/history giữdraw thực. CSV/env/seed/config/log được flush/lưu, bảng/PNG-SVG từ CSV/summary thật. Expected business rejection tách system error. Không thay deadline/scoring/transport hoặc kế thừa kết quả loopback thành LAN.

README ứng dụng và report fallback độc lập mẫu môn chưa có; tên file do project chọn, chưa quy định nộp. Nhóm4 nhãn theo người dùng, thông tin/actualcontribution trống; phân công gần25% dự kiến cân bằng code/độ khó/UI/test/experiment/report, không tự gán tác giả hoặc25%thực tế. Project-level evidence và historical reuse có provenance/sourcehash, không cộng suites trùng. Task15 technical localPASS nhưng tổng thểPARTIAL vì hồ sơ/LAN/máy mới/Compilatio/môn chưa đủ; không commit/push.

## 25. Cập nhật yêu cầu sau triển khai — 07/10/2026

Nguồn: yêu cầu trực tiếp người dùng, thay mô tả Decision5000ms/RESULT chuyển ngay ở mục3/19 lịch sử. FROZEN cho đợt cập nhật này: Decision7000ms cho trận mới; FlywayV3 chỉ Room idle/default, snapshot cũ bất biến. RESULT mở cửa sổ chung1500ms sau scoring commit bằng PhaseWindow/monotonic clock và timer SessionQueue. Hết hạn mới mở DECISION kế tiếp với đủ7000ms. Không cộng RESULT vào answer time. Terminal scoring lưu FINISHED/ranking/Room WAITING/UAG release ngay; QUESTION_RESULT và GAME_END cùng deadline trình bày câu cuối. Actor retire ngay, UI dùng deadline còn lại để chuyển Final; sau hết hạn/retention deadline null. Cancel/UNAVAILABLE xóa window và vô hiệu hóa timer.

UI chỉ câu hỏi/4 lựa chọn và leaderboard bên phải bật/tắt; resource/effect nhỏ, toast3s sau ACK, delta từ result; đúng xanh/sai đã chọn đỏ, reduced motion. requestId/questionIndex dedup lưu trong app cùng Game; snapshot khôi phục state không phát thông báo cũ. Giữ ScoreEngine/ranking/auth/transport/dependency.
