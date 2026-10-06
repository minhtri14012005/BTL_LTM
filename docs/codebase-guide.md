# Hướng dẫn đọc các thư mục code

Tài liệu này giải thích cấu trúc source hiện có, giúp chọn đúng chỗ để đọc code. Hướng dẫn cài và chạy ở [README chính](../README.md). Project có một Backend Spring Boot và một Frontend trình duyệt, cùng được đóng gói bằng Maven; không có project Frontend thứ hai.

## 1. Bản đồ source

```text
src/
├── main/
│   ├── java/vn/edu/quiz/       Backend Java
│   │   ├── auth/              Đăng nhập và kiểm tra quyền
│   │   ├── user/              Dữ liệu tài khoản
│   │   ├── quiz/              Bộ câu hỏi và ảnh
│   │   ├── room/              Phòng và thành viên
│   │   ├── game/              Luật, trận đấu và lịch sử
│   │   ├── realtime/          WebSocket, queue, timer, reconnect và replay
│   │   ├── system/            Kiểm tra tình trạng Server/DB
│   │   └── common/            Cấu hình và tiện ích nhỏ dùng chung
│   └── resources/
│       ├── application*.yml   Cấu hình Spring Boot
│       ├── db/migration/      Phiên bản schema MySQL
│       └── static/            Frontend HTML/CSS/JavaScript
│           └── client/        Các module JavaScript
└── test/java/vn/edu/quiz/      Test theo feature tương ứng
```

`vn/edu/quiz` là đường dẫn của package Java `vn.edu.quiz`. File khởi động Backend là `QuizApplication.java` ngay trong package này. Mỗi feature giữ phần code thuộc trách nhiệm của mình; không có một thư mục `service` hoặc `entity` toàn hệ thống.

## 2. Backend: mỗi feature phụ trách gì?

Các đường dẫn trong bảng dưới bắt đầu từ `src/main/java/vn/edu/quiz/`.

| Thư mục | Ý nghĩa dễ hiểu | Khi nào cần đọc |
|---|---|---|
| `auth/` | Register/Login/Logout, mật khẩu hash, phiên đăng nhập, CSRF và quyền thao tác. `security/` chứa phần xác thực, kiểm tra session và policy quyền. | Tìm luồng đăng nhập, lỗi hết hạn hoặc quyền Host/Player. |
| `user/` | Entity và Repository tài khoản trong DB. Không có Entity Account riêng trùng User, và Host không phải vai trò toàn hệ thống của User. | Tìm dữ liệu tài khoản hoặc truy vấn User. |
| `quiz/` | Tạo/sửa/xóa/xem Quiz, câu hỏi, PUBLIC/PRIVATE, quyền Owner, hạn chế Author và lưu ảnh bất biến. | Tìm CRUD Quiz, validation 4 options hoặc vì sao non-owner không thấy đáp án. |
| `room/` | Phòng, cấu hình, trạng thái, thành viên, participation Player/Spectator và quyền Host. Đồng bộ thao tác trước Start nối với Room boundary bên `realtime/session/`. | Tìm Waiting Room, Join/Leave, roster, cấu hình và điều kiện mở/đóng phòng. |
| `game/` | Phần nghiệp vụ trận đấu: luật tính điểm, phase, scoring transaction, snapshot, kết thúc, Cancel và History. History nằm trong feature này. | Tìm điều gì xảy ra sau Start, kết quả câu và kết quả cuối trận. |
| `realtime/` | Hạ tầng đưa command/event đến đúng người và đúng thứ tự: raw WebSocket, socket registry, ingress, queue, timer, replay và reconnect. | Tìm mất ACK, duplicate, deadline, nhiều socket hoặc thứ tự xử lý. |
| `system/` | Endpoint status/readiness và kiểm tra DB/startup. | Phân biệt Server HTTP còn sống với MySQL/ứng dụng đã sẵn sàng. |
| `common/` | Cấu hình dùng chung và tiện ích kỹ thuật nhỏ. | Tìm cấu hình nền hoặc lớp hỗ trợ; luật game vẫn ở `game`. |

## 3. Những tên thư mục lặp lại trong Backend

Không phải feature nào cũng có đủ các thư mục này. Chỉ có những thư mục đang chứa code thực tế.

| Tên | Nó chứa gì? | Ví dụ cách hiểu |
|---|---|---|
| `controller/` | Nhận HTTP request, nối với service và trả response. | Khi cần tìm endpoint REST, bắt đầu ở đây. |
| `service/` | Xử lý nghiệp vụ, phối hợp policy/truy vấn và transaction. | Kiểm tra quyền Owner, tạo Room hoặc chấm cả câu. |
| `entity/` | Mô hình dữ liệu JPA ánh xạ vào bảng MySQL. | Dữ liệu được lưu lâu dài, khác dữ liệu hiển thị trên UI. |
| `repository/` | Truy vấn và lưu Entity thông qua Spring Data JPA. | Tìm cách đọc dữ liệu, khóa bản ghi hoặc kiểm tra uniqueness. |
| `enums/` | Các giá trị hữu hạn của feature. | Trạng thái Room, phase Game, lựa chọn A/B/C/D. |
| `dto/` | Cấu trúc dữ liệu trao đổi hoặc snapshot; không phải Entity. | Tách dữ liệu công khai với dữ liệu riêng, tránh serialize Entity trực tiếp. |
| `dto/request/` | Dữ liệu Client gửi qua REST. | Field nhập vào và validation. |
| `dto/response/` | Dữ liệu Server trả qua REST. | Field Frontend nhận được và nullability. |
| `auth/security/` | Xác thực, phiên đăng nhập và kiểm tra quyền. | User thật là ai, session còn hiệu lực không, được làm action nào. |
| `common/config/` | Cấu hình Spring và thuộc tính hạ tầng chung. | Bind/origin/security hoặc wiring nền theo class thực tế. |
| `common/util/` | Tiện ích nhỏ dùng lại được. | Không dùng làm nơi gom toàn bộ nghiệp vụ. |

## 4. Các thư mục riêng trong Game và Realtime

| Đường dẫn | Ý nghĩa và ranh giới |
|---|---|
| `game/engine/` | Tính score/ranking từ input và trả output thuần. Không tự đọc DB, clock, network hoặc random lại Spin. Đây là chỗ đọc bảng điểm, streak, Momentum và Recovery. |
| `game/runtime/` | Dữ liệu/guard thuần cho phase và đóng câu: `PhaseWindow`, `QuestionCloseGate`. Không phải nơi chứa socket hoặc worker pool. |
| `game/service/` | Lifecycle và transaction nghiệp vụ: mở phase, chấm cả câu, snapshot, History, kết thúc và cleanup. Runtime/event chỉ cập nhật sau commit. |
| `realtime/websocket/` | Nhận frame raw WS, parse/validate và chuyển command sang xử lý; map sự kiện nghiệp vụ thành message gửi Client. |
| `realtime/message/command/` | Cấu trúc command Client gửi lên. |
| `realtime/message/event/` | Cấu trúc event Server gửi xuống. |
| `realtime/message/common/` | Phần wire dùng chung như target, envelope hoặc ACK/error theo class hiện có. |
| `realtime/connection/` | Quản lý socket của User, generation, replacement và gửi dữ liệu đến đúng kết nối. |
| `realtime/session/` | Room boundary trước Game ID; session processor, ingress timestamp/sequence, queue tuần tự từng Game và điểm nối với lifecycle. |
| `realtime/timer/` | Clock và timer scheduling/event. Timer đi qua cùng ingress với command; nghiệp vụ quyết định timer có còn hợp lệ không. |
| `realtime/idempotency/` | Fingerprint và replay receipt để retry cùng command trả response gốc, tránh side effect lặp. |

Đọc [REST contract](rest-api-contract.md) để biết request/response HTTP và [WS contract](websocket-contract.md) để biết wire command/event. Tên lớp nội bộ không thay thế contract giao tiếp.

## 5. Frontend nằm ở đâu?

Frontend gốc nằm ở `src/main/resources/static/`. Spring Boot phục vụ các file này cùng origin với API.

| Đường dẫn tương đối trong `static/` | Ý nghĩa |
|---|---|
| `index.html` | Trang HTML ban đầu được trình duyệt tải. |
| `styles.css` | Kiểu hiển thị và bố cục của UI. |
| `app.js` | Entry point Frontend: khởi tạo ứng dụng, điều hướng trang, nối API/WS và chuyển event đến màn hiện tại. File này nằm ngay trong `static`, không nằm trong `client`. |
| `client/` | Các module JavaScript mà `app.js` và những module khác import. Không có framework/bundler thứ hai. |

Trong `client/`, tên module giúp chọn luồng để đọc:

| Nhóm/module | Ý nghĩa |
|---|---|
| `account.js` | Home, Register/Login và màn liên quan tài khoản. |
| `quizzes.js` | Danh sách, chi tiết và chỉnh sửa Quiz. |
| `rooms.js` | My Rooms, cấu hình, Join và Waiting Room. |
| `game.js`, `game-state.js` | Hiển thị gameplay/Final và nhận/apply state, snapshot, revision. Điểm và kết quả vẫn do Server quyết định. |
| `history.js` | Danh sách và chi tiết History từ REST. |
| `api.js` | Gọi REST, session/CSRF và xử lý response/error. |
| `transport.js` | Raw WebSocket, command/requestId, receipt, retry và kết nối lại/replacement. |
| `core.js`, `dom.js` | Helper quản lý dữ liệu và dựng giao diện dùng chung. |

`target/client/` chỉ là output sao chép từ source này, không phải nơi chỉnh sửa UI gốc.

## 6. Cấu hình, migration và test

| Đường dẫn | Vai trò |
|---|---|
| `src/main/resources/application.yml` | Cấu hình chung Server, session, JPA/Flyway, upload ảnh và origin. |
| `src/main/resources/application-mysql.yml` | Profile MySQL và nhập cấu hình local. |
| `src/main/resources/application-web-only.yml` | Profile kiểm tra HTTP/static không DB; không phải bản gameplay đầy đủ. |
| `src/main/resources/db/migration/` | DDL phiên bản V1/V2 do Flyway quản lý. JPA validate schema, không tự thay schema bằng ddl-auto update. |
| `src/test/java/vn/edu/quiz/` | Test theo cùng feature với production: auth, quiz, room, game, realtime, common và system. Test engine tách khỏi test hạ tầng realtime. |
| `scripts/` | Tool bên ngoài runtime: build native client, test bằng Chrome, chuẩn bị DB, chạy benchmark và tổng hợp evidence. |
| `config/` | Mẫu cấu hình và cấu hình local; credential thật không phải source bàn giao. |
| `data/quiz-images/` | Ảnh runtime; không phải source hay cache build. Xóa ảnh còn được DB tham chiếu có thể làm hỏng hiển thị/lịch sử. |
| `target/` | Output build và artifact kiểm chứng. Một số file sinh lại được, nhưng thư mục cũng chứa evidence lịch sử; không coi toàn bộ là rác. |

## 7. Hai flow ngắn để định hướng đọc

**REST quản lý Quiz:** UI → `client/api.js` → `quiz/controller` → `quiz/service` kiểm tra quyền/validation → `quiz/repository` và MySQL → response DTO → UI.

**Gameplay WS:** UI → `client/transport.js` → auth/socket generation → `realtime/websocket` → ingress và queue ở `realtime/session` → replay/guard → lifecycle/transaction ở `game/service` → `game/engine` khi scoring → DB commit → runtime/cache/event → socket → `game-state.js` → UI.

Start dùng Room boundary trước khi có GameSession. Timer dùng cùng ingress/queue với command. Reconnect lấy snapshot từ Server, không tạo lại PlayerSession hoặc reset điểm/tài nguyên.

## 8. Chọn tài liệu để đọc tiếp

| Tài liệu | Vai trò |
|---|---|
| [README chính](../README.md) | Hướng dẫn toàn project: cài, build, cấu hình, chạy và test. |
| [Protocol thực nghiệm](../experiments/experiment-protocol.md) | Baseline/proposed, workload, metric và cách tái lập phép đo. |
| [Mục lục evidence bàn giao](../experiments/handoff/evidence-index.md) | Giải thích file bằng chứng và phạm vi kết quả đã lưu. |
| [Luật gameplay](gameplay-rules.md) và [TASKS/Overview](../TASKS.md) | Nguồn luật để đối chiếu, gồm bảng điểm. |
| [Decisions](implementation-decisions.md) | Lý do lựa chọn kỹ thuật và các quyết định đã freeze. |
| [Schema](database-schema.md) | ERD, cột và constraint DB. |
| [Final report](final-report.md) | Trình bày dự án và Results thực nghiệm. |

Các README trong `.cache/` thuộc Maven hoặc thư viện tải về. Không sửa/đổi tên tài liệu của dependency để tổ chức README project.
