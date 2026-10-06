# REST API contract — canonical

Cập nhật TASK 15, 06/10/2026. Contract canonical cho System, authentication, Quiz/ảnh, Room, Start, Game snapshot, Cancel và History đã triển khai. Base URL mặc định http://127.0.0.1:8080; localhost/LAN theo cấu hình bind/origin trong README. Server và Web Client cùng origin; gameplay/History UI Task12 đã tích hợp thật. Task15 không đổi endpoint/field, chỉ sửa nhãn trạng thái tài liệu cũ.

## IMPLEMENTED: System

Source: [SystemController](../src/main/java/vn/edu/quiz/system/controller/SystemController.java), [SystemStatus](../src/main/java/vn/edu/quiz/system/dto/response/SystemStatus.java), [DatabaseStatus](../src/main/java/vn/edu/quiz/system/dto/response/DatabaseStatus.java).

| Method/path | Auth/quyền | Request | HTTP / response | Scope / side effect |
|---|---|---|---|---|
| GET /api/system/status | Public; vẫn kiểm tra CORS allowlist | Không body, không query/path parameter bắt buộc | 200 SystemStatus khi HTTP phục vụ, kể cả DB chưa ready | Trạng thái toàn ứng dụng; chỉ SELECT1/probe, không thay domain data |
| GET /api/system/ready | Public; vẫn kiểm tra CORS allowlist | Như trên | 200 nếu database.status=UP và server=UP; 503 khi DB chưa ready hoặc startup cleanup chưa commit; body SystemStatus ở cả hai | Readiness DB/lifecycle; không tạo tài khoản/trận |

Response Content-Type application/json, Cache-Control no-store. Không trả credential, JDBC URL hoặc exception. Status liveness không đồng nghĩa MySQL/domain gameplay chạy đúng. DB mất kết nối tại runtime có nhánh DOWN trong code; baseline/tests kiểm chứng UP với MySQL và NOT_CONFIGURED với web-only, chưa chủ động dừng service DB để đo DOWN.

| Field | Type | Required / nullable | Giá trị / ý nghĩa |
|---|---|---|---|
| application | string | Có / non-null | competitive-quiz |
| server | string | Có / non-null | UP; STARTING khi startup cleanup chưa hoàn tất |
| database | object | Có / non-null | DatabaseStatus bên dưới |
| database.status | string | Có / non-null | UP, DOWN hoặc NOT_CONFIGURED |
| database.message | string | Có / non-null | Thông báo tiếng Việt được sanitize; không phải error code để Client phân nhánh |
| serverTimeMs | integer int64 | Có / non-null | Epoch UTC milliseconds từ Server |
| gameplay | string | Có / non-null | mysql: LIFECYCLE_INITIALIZING hoặc GAMEPLAY_WS (Answer/Spin/Star/Start/RECONNECT/Cancel và REST History/UI đã code); web-only: NOT_IMPLEMENTED |

Ví dụ schema response hiện có (serverTimeMs chỉ minh họa):

```json
{
  "application": "competitive-quiz",
  "server": "UP",
  "database": {"status": "UP", "message": "Đã truy vấn MySQL thành công."},
  "serverTimeMs": 1791156904343,
  "gameplay": "GAMEPLAY_WS"
}
```

Profile mysql dùng JDBC SELECT1 và ApplicationRunner kiểm tra DB/cleanup ACTIVE Game cũ thành SERVER_INTERRUPTED trước khi cho Start. Cleanup tối đa3 lần, backoff100/300ms; không commit được thì startup thất bại. Profile web-only không có DataSource/JPA/Flyway; status200, ready503, database NOT_CONFIGURED. Không dùng web-only làm bằng chứng MySQL.

## IMPLEMENTED: Authentication (profile mysql)

Source: [AuthController](../src/main/java/vn/edu/quiz/auth/controller/AuthController.java), [AuthService](../src/main/java/vn/edu/quiz/auth/service/AuthService.java), [SecurityConfiguration](../src/main/java/vn/edu/quiz/auth/security/SecurityConfiguration.java). Content-Type application/json; response auth có Cache-Control no-store. Không trả JPA Entity/password/password_hash. Register không tự login.

| Method/path | Auth / CSRF | Request | Thành công / side effect |
|---|---|---|---|
| GET /api/auth/csrf | Public | Không body | 200 CsrfResponse; tạo anonymous session nếu cần, cung cấp token CSRF |
| POST /api/auth/register | Public + X-CSRF-TOKEN hợp lệ | RegisterRequest | 201 UserResponse; tạo app_user sau transaction commit, BCrypt strength10; không tạo Account khác |
| POST /api/auth/login | Public + X-CSRF-TOKEN hợp lệ | LoginRequest | 200 UserResponse; kiểm tra User active/MySQL, đổi session ID chống fixation, lưu SecurityContext vào session, xóa token CSRF cũ |
| GET /api/auth/me | Session đã login | Không body | 200 UserResponse của server Principal; kiểm tra User chưa deleted và session còn hiệu lực |
| POST /api/auth/logout | Session đã login + X-CSRF-TOKEN hợp lệ | Không body | 204 không body; invalidate session, clear context/CSRF/cookie, đóng mọi WS cùng session với 4001 LOGGED_OUT |

Request fields:

| DTO / field | Type / required / nullable | Validation / diễn giải |
|---|---|---|
| RegisterRequest.username, LoginRequest.username | string / có / không | ASCII A–Z, a–z, 0–9, underscore; 3–32 ký tự; lưu và lookup lowercase Locale.ROOT |
| RegisterRequest.password, LoginRequest.password | string / có / không | 8–72 ký tự và tối đa72 byte UTF-8, không trim; BCrypt không được cắt phần vượt72 byte |
| RegisterRequest.displayName | string / có / không | Non-blank, tối đa100 ký tự trước strip; lưu strip |
| UserResponse.id | integer int64 / có / không | ID MySQL của User trên server |
| UserResponse.username, displayName | string / có / không | Public identity, không password hash |
| CsrfResponse.token | string / có / không | Token masked do Spring Security cấp; không phải authentication credential |
| CsrfResponse.headerName, parameterName | string / có / không | X-CSRF-TOKEN và _csrf; Web Client dùng headerName/token |
| AuthError.code, message | string / có / không | Code ổn định và thông báo sanitize; không exception/SQL/password |
| AuthError.serverTimeMs | integer int64 / có / không | Epoch UTC milliseconds |

Không lấy danh tính từ request body/query/header userId. Field JSON ngoài request DTO bị Jackson bỏ qua; userId không có quyền chọn tài khoản. me/logout lấy Authentication server-side. Chưa có endpoint chỉnh User hoặc reset password.

| HTTP / code | Điều kiện đã code |
|---|---|
| 400 INVALID_REQUEST | DTO/JSON không hợp lệ, password quá72 byte UTF-8 |
| 401 INVALID_CREDENTIALS | Login sai password, không tồn tại hoặc soft-deleted; cùng thông báo |
| 401 UNAUTHENTICATED | Endpoint yêu cầu login hoặc me/handshake phát hiện session/account không hợp lệ |
| 403 CSRF_INVALID | POST thiếu/sai token; token trước login hết hiệu lực |
| 403 FORBIDDEN | Security filter từ chối thao tác ngoài allowlist endpoint |
| 409 USERNAME_TAKEN | Tên đăng nhập tồn tại, kể cả khác case; UNIQUE MySQL bảo vệ concurrent Register |
| 409 ALREADY_AUTHENTICATED | Login khi session đã login; phải logout trước khi đổi tài khoản |
| 503 AUTH_UNAVAILABLE | DB access thất bại trong auth controller/handshake; thông báo sanitize |

CSRF filter chạy trước kiểm tra quyền: POST với session hết hạn và token cũ có thể nhận403 CSRF_INVALID; GET me hoặc WS reconnect nhận401 UNAUTHENTICATED. Browser xử lý bằng lấy lại csrf/login, không tự replay command gameplay. JSON malformed và DTO lỗi không phản chiếu dữ liệu bị reject/password. CORS reject do framework trả403, không cam kết body AuthError cho lỗi CORS/Tomcat hoặc endpoint chưa có.

## IMPLEMENTED: cookie, browser và HTTP security

Browser cùng origin nhận cookie JSESSIONID (HttpOnly, SameSite=Lax, path /). Khi phục vụ HTTPS, cookie secure theo request; môi trường localhost/LAN hiện dùng HTTP. Mặc định session30 phút HTTP inactivity, điều chỉnh bằng server.servlet.session.timeout phải dương và hữu hạn. Login đổi session ID và token CSRF; sau Login gọi lại GET csrf. Logout chỉ ảnh hưởng session hiện tại và các socket thuộc nó; session độc lập khác của cùng User vẫn còn hiệu lực. Không JWT, Basic auth, bearer token URL/localStorage, tài khoản mặc định hoặc global HOST role.

Luồng Web Client: GET csrf → POST register (nếu chưa có User) → POST login với token đó → GET csrf mới → GET me / mở WS → POST logout với token mới. fetch cùng origin gửi cookie tự động; có thể ghi credentials: 'same-origin'. Với request cross-origin được cho phép cần credentials: 'include'; browser còn áp dụng SameSite, nên triển khai ưu tiên client/server cùng origin.

CORS lấy ALLOWED_ORIGINS chính xác, credentials=true, GET/POST/PUT/PATCH/DELETE/OPTIONS và Content-Type/X-CSRF-TOKEN. Không wildcard/path/trailing slash. Allow-method không mở endpoint chưa có. Assets/System GET và /error public; Task11A bổ sung GET `/client/*.js` cho các ES module tĩnh (không mở API hoặc write). Auth/WS theo bảng trên, Quiz/Room/Start/Game snapshot theo bảng bên dưới; các route khác denyAll. Profile web-only không DataSource/auth service/WS handler: vẫn chỉ assets/System public, ready503; /ws và endpoint chức năng bị403 như baseline.

## Policy quyền và mức tích hợp

[PermissionPolicy](../src/main/java/vn/edu/quiz/auth/security/PermissionPolicy.java) thuần context; [AuthorizationService](../src/main/java/vn/edu/quiz/auth/service/AuthorizationService.java) đọc active User, Room/RoomMember, GameSession/GameMember/PlayerSession từ repository theo Principal. Không nhận userId từ Client.

| Action nội bộ | Policy |
|---|---|
| Room VIEW | User active + membership JOINED của đúng Room |
| Room CONFIGURE | Như VIEW + Room.hostUserId là User + DRAFT hoặc WAITING |
| Room START | Như VIEW + Host + WAITING; điều kiện đủ Player/Quiz/config thuộc Task5/8 |
| Game VIEW | User active + GameMember của đúng Game, kể cả Spectator/ELIMINATED/FINISHED |
| Game CANCEL | User active + GameMember + Host theo Room + Game ACTIVE; ELIMINATED hoặc Spectator vẫn được |
| Game ANSWER | GameMember + participation PLAYER + PlayerState PLAYING + ACTIVE + QUESTION_OPEN |
| Game SPIN/STAR | GameMember + participation PLAYER + PlayerState PLAYING + ACTIVE + DECISION |

Guard nội bộ: outsider/non-Host/ELIMINATED chơi →403 FORBIDDEN; sai phase/trạng thái →409 INVALID_STATE; resource không tồn tại →404 NOT_FOUND; User/principal giả/deleted/chưa auth →401 UNAUTHENTICATED. Endpoint dùng error table từng feature. Start/Game snapshot/Answer/Spin/Star/Cancel đã tích hợp; Host ELIMINATED vẫn Cancel. Game dùng GameMember snapshot, không dùng LEFT hiện tại để xóa quyền lịch sử/replay.

Use-case tương lai phải kiểm tra lại guard trong transaction/queue xử lý cùng state, rồi kiểm tra deadline/resource/ownership Quiz theo task tương ứng. Policy không thực hiện Start/Cancel/Answer, không chứng minh atomic/lifecycle/race. Không authorize chỉ một lần tại handshake.


## IMPLEMENTED: Quiz CRUD và ảnh (profile mysql)

Source: [QuizController](../src/main/java/vn/edu/quiz/quiz/controller/QuizController.java), [QuizService](../src/main/java/vn/edu/quiz/quiz/service/QuizService.java), [QuizImageStore](../src/main/java/vn/edu/quiz/quiz/service/QuizImageStore.java). Tất cả route yêu cầu session authenticated và User active. POST/PUT/DELETE giữ CSRF X-CSRF-TOKEN; response JSON/ảnh/204 có Cache-Control no-store. Không return Entity, không Client chọn ownerUserId, không hard delete.

| Method/path | Scope/quyền | Request | Response / side effect |
|---|---|---|---|
| POST /api/quizzes | User active; Owner lấy từ Principal | CreateQuizRequest | 201 QuizOwnerResponse, tạo Quiz và câu trong một transaction |
| GET /api/quizzes | Active Quiz PUBLIC hoặc do User sở hữu | page=0 mặc định, size=20 mặc định | 200 QuizListResponse; luôn chỉ metadata, kể cả Quiz của Owner; ID giảm dần |
| GET /api/quizzes/{id} | Owner cả PUBLIC/PRIVATE; non-owner chỉ PUBLIC | id int64 | 200 QuizOwnerResponse nếu Owner, 200 QuizMetadataResponse nếu non-owner PUBLIC; PRIVATE không thuộc User/deleted/missing404 |
| PUT /api/quizzes/{id} | Chỉ Owner, Quiz chưa deleted | EditQuizRequest (full replacement) | 200 QuizOwnerResponse sau commit; soft-retire câu cũ, insert generation mới, revision tăng một |
| DELETE /api/quizzes/{id} | Chỉ Owner, Quiz chưa deleted | revision int64 bắt buộc query | 204; soft-delete Quiz và mọi câu active, giữ FK/history/ảnh; stale revision409 |
| POST /api/quizzes/{id}/images | Chỉ Owner, Quiz chưa deleted | multipart/form-data, part file | 201 ImageResponse; lưu blob PNG bất biến, chưa gắn vào câu/không đổi revision |
| GET /api/quizzes/{id}/images/{hash} | Owner hoặc GameMember đủ điều kiện bên dưới | hash64 hex lowercase; gameSessionId int64 tùy chọn | 200 image/png bytes; nosniff/no-store; không redirect ra URL ngoài |

Request JSON fields ngoài DTO bị bỏ qua, không có tác dụng cấp quyền; không chấp nhận nested account/owner/Quiz ID làm identity. Owner responses dùng mapper riêng, Public/list dùng DTO không có trường nội dung/đáp án. PRIVATE edit/delete của non-owner403; PRIVATE GET404 để không cấp nội dung. Không endpoint question CRUD rời hoặc PATCH chưa code.

### Request fields và validation

| DTO / field | Type / required / nullable | Validation |
|---|---|---|
| CreateQuizRequest.title, EditQuizRequest.title | string / có / không | Non-blank, tối đa200 ký tự; lưu strip |
| visibility | string enum / có / không | PUBLIC hoặc PRIVATE (case-sensitive) |
| questions | array QuestionRequest / có / không | 1–50 phần tử non-null, nested validation; thứ tự array là thứ tự biên soạn |
| EditQuizRequest.revision | integer int64 / có / không | >=0, bằng revision hiện tại |
| QuestionRequest.content | string / có / không | Non-blank, tối đa5000 ký tự; strip |
| QuestionRequest.options | object / có / không | Đúng4 key A/B/C/D; value string non-blank, tối đa2000 ký tự; key khác enum hoặc thiếu key bị400 |
| QuestionRequest.correctAnswer | string enum / có / không | Chính xác một A/B/C/D; không array/multiple answer |
| QuestionRequest.imageRef | string / không / có | null hoặc sha256: +64 lowercase hex; phải có file upload của chính Quiz |

Giới hạn lưu1–50 câu là kỹ thuật API biên soạn; không đổi luật trận10–50. Quiz ít hơn10 câu chưa đủ Start; Task8 kiểm tra số câu/config thật khi tạo GameSession. Chưa state Draft Quiz/endpoint ready/start trong Task4.

Create chưa có Quiz ID để upload ảnh: tạo với imageRef null → Owner upload vào Quiz vừa tạo → PUT full body/revision để gắn imageRef. Gán reference không thuộc Quiz hoặc chưa upload400 IMAGE_NOT_FOUND; Create transaction rollback, không lưu Quiz nửa chừng. Không nhận image URL tùy ý/base64 hoặc tin filename/MIME Client.

Ví dụ body Create (rút gọn một câu hợp lệ về quản lý, chưa đủ Start):

```json
{
  "title": "Java Basics",
  "visibility": "PUBLIC",
  "questions": [{
    "content": "Kiểu nào là primitive?",
    "options": {"A": "int", "B": "String", "C": "List", "D": "Integer"},
    "correctAnswer": "A",
    "imageRef": null
  }]
}
```

### Response fields

| DTO / field | Type / required / nullable | Diễn giải |
|---|---|---|
| QuizMetadataResponse.id, ownerUserId | integer int64 / có / không | Quiz ID và tác giả; không phải quyền Host toàn hệ thống |
| title, visibility | string / có / không | Tiêu đề/public-private |
| questionCount | integer / có / không | Số câu active, không tính generation đã soft-retire |
| createdAtMs, revision | integer int64 / có / không | Epoch UTC ms, aggregate revision |
| QuizOwnerResponse | object / có / không | Các field metadata ở trên và questions |
| QuizOwnerResponse.questions | array QuestionResponse / có / không | Chỉ Owner, thứ tự biên soạn active |
| QuestionResponse.id | integer int64 / có / không | ID generation hiện tại, thay đổi sau PUT; Client không gửi id để sửa câu lịch sử |
| position | integer / có / không | 1..questionCount trong view; khác DB order_index là allocator không tái sử dụng |
| content, options, correctAnswer | string, object A/B/C/D strings, enum / có / không | Owner-only |
| imageRef | string / có / có | null hoặc sha256 hash |
| QuizListResponse.items | array QuizMetadataResponse / có / không | Không câu/options/correctAnswer/imageRef, kể cả Owner |
| page, size | integer / có / không | page>=0, size1..100 |
| totalElements | integer int64 / có / không | Số Quiz visible; không đếm PRIVATE của người khác |
| ImageResponse.imageRef, url, contentType | string / có / không | Ref bất biến, relative URL có quizId/hash, image/png |
| QuizError.code, message, serverTimeMs | string, string, integer int64 / có / không | Shape như AuthError; thông báo sanitize |

### Edit, delete và lịch sử

PUT là full replacement: lock PESSIMISTIC_WRITE hàng Quiz, kiểm tra Owner/revision; soft-retire câu cũ rồi insert câu mới với order_index chưa từng dùng. UNIQUE(quiz_id,order_index) hiện có vẫn enforce cả câu deleted. Owner view position liên tục1..N; snapshot GAME_QUESTION có order riêng1..N ở Task8. Không sửa câu cũ hoặc tái sử dụng source Question ID. Sửa metadata/câu/không đổi nội dung đều tăng revision một, kể cả khi ORM không dirty các field Quiz khác. Không nhận revision mới do Client tự cấp.

DELETE cũng lock Quiz/check revision, không xóa GameSession/GameQuestion/GameMember/Answer hay blob ảnh. Start Task8 phải đọc/snapshot cùng transaction/khóa Quiz phù hợp, không chép một nửa generation. Task4 chỉ kiểm chứng CRUD transaction/history fixture, chưa Start hoặc lifecycle.

### Image storage và quyền snapshot

PNG/JPEG hợp lệ, file tối đa2 MiB, multipart request tối đa3 MiB; mỗi cạnh<=4096 và tổng<=4.000.000 pixel. Server kiểm tra format/dimension từ bytes trước decode; normalize PNG loại metadata/extra bytes. Hash SHA-256 trên PNG đã lưu, path do server tạo: QUIZ_IMAGE_DIRECTORY/<quizId>/<hash>.png, mặc định ./data/quiz-images. Cần filesystem hỗ trợ hard link (NTFS trên máy đã test); publish file hoàn chỉnh không overwrite. Upload trùng trả cùng ref, blob đã tồn tại phải đúng bytes/hash, mismatch503. GET kiểm tra hash trước trả; file là local durable asset, không nằm static public.

Owner đọc upload/ảnh cũ của Quiz ngay cả sau soft-delete. Non-owner PUBLIC không tự có quyền đọc ảnh câu hỏi. Nếu truyền gameSessionId, Server đọc projection MySQL: đúng GameSession.quizId, User có GameMember, có GameQuestion imageRef tương ứng và openedAtMs non-null + phase QUESTION_OPEN/QUESTION_CLOSED/SCORING/RESULT/FINISHED. DECISION/câu tương lai bị403; User ngoài Game hoặc game của Quiz khác cũng403. Không tin gameSessionId để giả danh. Khi snapshot đã hợp lệ, đổi Quiz PRIVATE hoặc xóa Quiz không chặn đọc ảnh đã mở trong trận/lịch sử. Không endpoint dựng Game hoặc history mới; quyền ảnh trên Game rows hiện có là code thật, test bằng fixture DB.

Sau edit/delete không xóa blobs; upload xong chưa attach hoặc transaction sau đó fail có thể để lại blob không dùng. Không auto cleanup để tránh xóa ảnh lịch sử; cleanup riêng phải đối chiếu references khi task đó được giao. Sao lưu MySQL và thư mục ảnh cùng bộ dữ liệu; chạy Server cùng working directory/config để ref không trỏ sang root khác.

### Error/status thực tế

| HTTP/code | Điều kiện |
|---|---|
| 400 INVALID_REQUEST | DTO/nested options/enum/field/revision/page/size/id/query không hợp lệ hoặc thiếu required parameter/part |
| 400 INVALID_IMAGE | Bytes không PNG/JPEG hoặc malformed/dimension quá giới hạn |
| 400 IMAGE_NOT_FOUND | Gán imageRef chưa upload cho Quiz |
| 401 UNAUTHENTICATED | Chưa login/account không active; filter/advice |
| 403 CSRF_INVALID | POST/PUT/DELETE thiếu/sai CSRF |
| 403 FORBIDDEN | Non-owner quản lý/upload hoặc không có quyền ảnh |
| 403 QUIZ_AUTHOR_CANNOT_PLAY | Guard participation; Task5 đã dùng khi tạo/sửa/Join Room, chưa Start/lifecycle |
| 404 QUIZ_NOT_FOUND | Missing/deleted hoặc non-owner GET/selection PRIVATE |
| 404 IMAGE_NOT_FOUND | Authorized reader nhưng blob không tồn tại |
| 409 REVISION_CONFLICT | PUT/DELETE stale revision hoặc ORM optimistic conflict |
| 409 QUIZ_LIMIT_REACHED | Allocator order_index hết range int32; không thêm luật số câu trận |
| 413 IMAGE_TOO_LARGE | File/multipart vượt giới hạn; advice riêng vì parser fail trước xác định controller |
| 503 QUIZ_UNAVAILABLE, IMAGE_UNAVAILABLE | DB hoặc filesystem/integrity thất bại; không SQL/path/exception/password |

Unsupported method/content type và CORS/Tomcat lỗi ngoài controller theo framework, không cam kết QuizError cho route chưa code.

### Selection và Author policy

[QuizAccessPolicy](../src/main/java/vn/edu/quiz/quiz/service/QuizAccessPolicy.java): PRIVATE chỉ Owner chọn/sử dụng để tạo Room; PUBLIC người khác có thể chọn nhưng không sửa/xóa. QuizService.requireUsable kiểm tra active User/Quiz và visibility, chưa tạo Room. requireParticipation từ chối authorId==userId khi PLAYER, cho SPECTATOR; Host quyền vẫn theo Room, độc lập participation. User tham gia bằng Room code không cần sở hữu Quiz (Overview§2/6); quyền chọn bộ của người tổ chức khác kiểm tra roster Player không phải tác giả.

Task5 đã dùng guards khi tạo/sửa/Join Room; Task8 phải kiểm tra lại roster và dùng author snapshot trong GameSession. Task4 chỉ có policy tests; Task5 đã test Author Host Spectator trong Room thật, chưa trong trận. Sample seed opt-in sample-data profile tạo demo_author và demo_player1..3, Quiz PUBLIC10 câu của Author; canonical API không thêm seed endpoint.

## IMPLEMENTED: Room REST — profile mysql

Source: room/controller/RoomController → realtime/session/RoomOperations/RoomBoundary → room/service/RoomService. Session authentication/active User và CSRF giữ nguyên Task3. Tất cả response thành công/error từ controller có Cache-Control:no-store; không serialize Entity. Host lấy từ Principal khi Create và Room.hostUserId khi quản lý, không request userId. Request JSON strict riêng feature Room: unknown/duplicate fields, trailing JSON, enum/required/null/type sai đều400; không coercion số dạng string hoặc float thành integer.

| Method/path | Auth/scope | Request | Response/side effect |
|---|---|---|---|
| POST /api/rooms | User active + CSRF | CreateRoomRequest | 201 RoomResponse, DRAFT + Host ROOM_MEMBER JOINED trong một transaction; cho nhiều Draft |
| GET /api/rooms?page=0&size=20 | User active | page>=0, size1..100 | 200 RoomListResponse, chỉ Room có membership JOINED của chính User; DRAFT chỉ có Host |
| GET /api/rooms/{id} | User active, JOINED; DRAFT chỉ Host | id int64 | 200 RoomResponse nhất quán trong boundary; LEFT/outsider403, missing404 |
| GET /api/rooms/by-code/{code} | User active, biết code chính xác | code uppercase12 ký tự | 200 RoomPreviewResponse chỉ WAITING; không roster/Quiz/câu, mã sai/DRAFT/ACTIVE/CLOSED404 |
| PUT /api/rooms/{id} | Host active + CSRF, DRAFT/WAITING | EditRoomRequest | 200 RoomResponse, full config replacement, kiểm tra revision, tăng revision một, broadcast sau commit |
| POST /api/rooms/{id}/open | Host active + CSRF, DRAFT | RoomRevisionRequest | 200 RoomResponse; DRAFT→WAITING, tăng revision một |
| POST /api/rooms/{id}/close | Host active + CSRF, DRAFT/WAITING | RoomRevisionRequest | 200 RoomResponse; →CLOSED terminal, giữ roster/lịch sử, broadcast sau commit |

Join/Leave/Remove/Subscribe dùng `/ws`, không có REST alias. Không endpoint Start/Cancel hay DRAFT→ACTIVE trong Task5. ACTIVE→WAITING thuộc lifecycle Task8/11B.

### Request fields

| Field | Type / required / nullable | Validation |
|---|---|---|
| CreateRoomRequest.requestId | string / có / không | UUID lowercase dạng8-4-4-4-12; do Client tạo, không ID User |
| CreateRoomRequest.config | object RoomConfigRequest / có / không | Toàn bộ config dưới đây |
| EditRoomRequest.requestId, config | như Create / có / không | Full replacement, không patch |
| EditRoomRequest.revision | integer int64 / có / không | >=0, revision đã đọc |
| RoomRevisionRequest.requestId, revision | như trên / có / không | Dùng Open/Close |
| config.quizId | integer int64 / có / không | >0; Quiz active, Host có quyền chọn: PUBLIC hoặc PRIVATE của mình |
| config.name | string / có / không | không blank, <=200 ký tự trước strip |
| config.maxPlayers | integer / có / không | 3..100; >=số Player JOINED sau đổi hostParticipation |
| config.questionDurationMs | integer int64 / có / không | >0; thời gian ms, chưa timer ở Task5 |
| config.hostParticipation | string enum / có / không | PLAYER/SPECTATOR; tác giả Quiz chỉ SPECTATOR |

decisionDurationMs cố định5000 theo baseline Overview, không nhận field để sửa; min3 Player và Quiz10–50 là điều kiện **Start Task8**, không điều kiện Join/Open. maxPlayers<=100 và tổng100 JOINED (kể cả Spectator) là giới hạn kỹ thuật của Waiting API/snapshot, không thay luật scoring. Join PUBLIC/PRIVATE đã được Host chọn không yêu cầu người tham gia sở hữu Quiz; PRIVATE vẫn không cho họ đọc Quiz REST.

```json
{
  "requestId":"15a1b0be-2ccd-4771-8acf-a717d7f41877",
  "config": {
    "quizId":1,"name":"Java tối thứ sáu","maxPlayers":10,
    "questionDurationMs":15000,"hostParticipation":"SPECTATOR"
  }
}
```

### Response fields và quyền dữ liệu

RoomResponse là snapshot Room đầy đủ cho JOINED member, kể cả Host Spectator; không questions/options/correctAnswer/imageRef, không PlayerSession/gameplay state chưa code.

| Field | Type / required / nullable | Ý nghĩa |
|---|---|---|
| id, hostUserId, quizId | integer int64 / có / không | ID do Server lưu; Host theo Room |
| quizTitle, quizDeleted | string, boolean / có / không | Metadata của Quiz được chọn; deleted báo nguồn đã soft-delete, không xóa Room/history |
| roomCode, name, status | string / có / không | code ngẫu nhiên12 ký tự A–Z/2–9 (generator bỏ I/O), DRAFT/WAITING/ACTIVE/CLOSED |
| maxPlayers | integer / có / không | Capacity PLAYER; Spectator không chiếm slot Player |
| questionDurationMs, decisionDurationMs, createdAtMs, revision | integer int64 / có / không | ms epoch/duration; revision authoritative của Room |
| members | array Member / có / không | Chỉ JOINED, sort userId tăng; CLOSED giữ roster cuối, không expose LEFT rows |
| Member.membershipId, userId | integer int64 / có / không | Row ID được tái sử dụng khi rejoin và User ID |
| Member.displayName, host, participation, joinedAtMs | string, boolean, enum, int64 / có / không | Tên công khai, Host flag, PLAYER/SPECTATOR, epoch ms lần Join gần nhất |
| RoomListResponse.items, page, size, totalElements | array RoomResponse, integer, integer, int64 / có / không | Page theo Room.id giảm; transaction read snapshot MySQL REPEATABLE-READ, không list phòng của outsider |
| RoomPreviewResponse.id, roomCode, name, status, maxPlayers | như trên / có / không | Minimal lookup bằng code, không quizId/roster |
| RoomError.code, message, retryable, serverTimeMs | string, string, boolean, int64 / có / không | message hiện bằng code, không SQL/credential; auth filter vẫn AuthError theo Task3 |

Host không Leave/Remove chính mình; dùng Close ở DRAFT/WAITING. ACTIVE khóa config/participation/new membership/Leave/Remove/Close, kể cả Spectator; disconnect không Leave. Điều này giữ Host/roster để Cancel hợp lệ ở Task8/11B, không tự chuyển Host hoặc viết Cancel giả. CLOSED không mở lại; JOINED relationship giữ để đọc final Room config/roster, nhưng không có Game mới hoặc broadcast tiếp. Remove làm LEFT, không ban; có thể Join lại WAITING nếu biết code/còn slot. UNIQUE(room,user) được giữ, không delete row.

### Pregame dedup và serialization

REST Create/Edit/Open/Close và Start capture HTTP login session gốc rồi kiểm tra lại session/user trong Room boundary, **trước replay/cache hoặc persistence**. Logout/expiry trong lúc chờ boundary →401 UNAUTHENTICATED, không side effect/receipt/event; đăng nhập lại có thể gửi cùng requestId/payload chưa commit. Operation đã qua kiểm tra tại boundary được hoàn tất transaction như policy command đang xử lý; không rollback một commit vì Logout đến sau. WS vẫn revalidate session và connection generation trong callback đã có.

Tất cả mutation của Room đã có (REST Edit/Open/Close, WS Join/Leave/Remove/Subscribe) dùng key `(authenticatedUserId, ROOM, roomId, requestId)` cùng boundary; type **không** nằm trong key. Fingerprint SHA-256 của JSON canonical gồm type/target/questionIndex=null/payload: object keys sort, array order giữ, payload config/revision được bao gồm. REST requestId tách khỏi payload; EDIT_ROOM payload `{revision,config}`, OPEN_ROOM/CLOSE_ROOM `{revision}`. Type/target/index/payload đổi với cùng key →409 INVALID_REQUEST_ID. Identity/target authorization trước replay, replay trước state/revision; receipt cũ không phát mutation/event lần hai. Timestamp/response/revision cũ giữ nguyên. WS retry đã Join có snapshot hiện tại riêng, xem canonical WS.

Create chưa có roomId: key `(authenticatedUserId, USER_CREATE_ROOM, authenticatedUserId, requestId)`, fingerprint CREATE_ROOM/self target/null index/config; giữ10 phút từ commit. Replay201 trả cùng RoomID/response, không tạo Draft thứ hai. Đừng retry Create ngoài retention hoặc qua Server restart như một bảo đảm exactly-once.

Room receipt giữ đến CLOSED+10 phút; sweeper60s chỉ thu hồi scope rảnh đã hết retention, không evict live receipt. Giới hạn thực Task5:512 scope,1024 committed/reserved receipt toàn Server,65 thao tác đang xử lý/chờ mỗi scope (1+64),32 subscriptions/socket. Capacity checked trước persistence; full503, replay cũ/GET snapshot vẫn phục vụ nếu admission còn chỗ. Đây là giới hạn của pregame implementation, thay proposal4096/User/Room bằng cap toàn Server phù hợp memory một Server; Game retention/cap chưa code, chốt Task9.

Fair lock/admission queue cùng Room xử lý từ service transaction đến outbound, chờ lock tối đa5s; Room row PESSIMISTIC_WRITE và @Version/touchRevision bảo vệ DB. Config/Join/Leave/Remove không chen nhau; Start Task8 **phải vào đúng boundary/key ROOM trước khi mở transaction**, khóa Room→Quiz→User tăng userId rồi snapshot/claim UAG. Boundary reject outer transaction để không phát trước commit. Chưa có ingress timestamp/timer Task7 hoặc Start transaction; không coi pregame lock là bằng chứng deadline/Game Engine.

Room DB write thử một lần. DataAccess/transaction failure fence scope ROOM_UNAVAILABLE cho mutation mới tới restart, không retry mù khi commit chưa rõ; read/replay receipt đã có vẫn được phép nếu DB/account còn truy cập. Rollback không ACK/broadcast; sau commit lưu receipt rồi gửi private ACK/RoomUpdated theo cùng lock. Send fail đóng socket, không undo hoặc persist lại. Game failure policy3 attempts/startup cleanup vẫn PLANNED, chưa triển khai ở Task5.

### Room errors

| HTTP/code | Trigger |
|---|---|
| 400 INVALID_REQUEST | REST strict parse/validation/page/size |
| 401 UNAUTHENTICATED;403 CSRF_INVALID | Session/active User/CSRF như auth contract |
| 403 FORBIDDEN | Non-Host quản lý; outsider/LEFT đọc roster; DRAFT non-Host |
| 403 QUIZ_AUTHOR_CANNOT_PLAY | Host/Join/config đổi thành PLAYER của tác giả Quiz |
| 404 ROOM_NOT_FOUND;404 QUIZ_NOT_FOUND | Target/code missing, lookup non-WAITING, hoặc Quiz deleted/private selection không đúng Owner |
| 409 INVALID_STATE | Operation không hợp status; kể cả Room WAITING nhưng DB còn ACTIVE Game |
| 409 REVISION_CONFLICT | Config/Open/Close stale revision |
| 409 INVALID_REQUEST_ID | Cùng key khác fingerprint |
| 409 ROOM_FULL;MEMBER_LIMIT_REACHED | Player slots/tổng JOINED đạt giới hạn |
| 409 HOST_CANNOT_LEAVE | Host tự Leave/Remove (WS) |
| 503 SERVER_BUSY;RECEIPT_LIMIT_REACHED;SUBSCRIPTION_LIMIT_REACHED | Admission/runtime capacity, không persistence; retryable=true |
| 503 ROOM_UNAVAILABLE | Fence sau lỗi DB write; retryable=false, kiểm tra DB/restart trước mutation |
| 503 SERVICE_UNAVAILABLE | Read/authorize DB failure; retryable=true; WS connection auth failure dùng close1011 |

FK/UNIQUE Task2 vẫn enforce một ACTIVE Game/Room và một ACTIVE Game/User, **kể cả Spectator**, đã test MySQL constraints. User có thể có nhiều Draft/Waiting membership; chỉ Start Task8 chiếm USER_ACTIVE_GAME cho toàn roster. Task5 không tạo GameSession/PlayerSession/UAG, không tuyên bố đã kiểm chứng Start atomic cùng/chéo Room.

## PLANNED

Task10 đã có reconnect/socket replacement qua WS; Task11B bổ sung History/Cancel và kiểm chứng durable cleanup bên dưới. Waiting/gameplay/reconnect/Cancel commands/events dùng [websocket-contract](websocket-contract.md).

Evidence đến Task5: SystemWebTest, MySqlSmokeIT, AuthNetworkIT, PermissionPolicyTest, AuthorizationRepositoryIT; Task4 QuizNetworkIT (HTTP/MySQL/filesystem, history rows tổng hợp), QuizAccessPolicyTest/SampleDataIT; Task5 RoomNetworkIT9 và RoomBoundaryTest5. Task8 bổ sung lifecycle integration ở dưới; UI Quiz/Waiting và LAN2 máy chưa kiểm chứng. Kết quả/giới hạn tại [project-status](project-status.md).

## IMPLEMENTED: Start và Game snapshot — Task 8 (profile mysql)

Source: game/controller/GameController, game/service/GameTransactions/GameLifecycle/GameProjection và realtime/session/GameOperations/GameRuntime. JSON strict dùng decoder hiện có; response Cache-Control no-store. Không trả Entity.

| Method/path | Auth/scope | Request | Thành công |
|---|---|---|---|
| POST /api/rooms/{roomId}/start | Session active + CSRF, Host Room; roomId positive int64 | `{requestId:UUID lowercase, revision:int64>=0, questionCount:integer 10..50}`, tất cả bắt buộc/non-null, không field thừa | 200 StartGameResponse sau Start commit/handoff; replay trả receipt cũ |
| GET /api/games/{gameId}/snapshot | Session active; thuộc GAME_MEMBER của Game này (Player hoặc Spectator), gameId positive int64 | Không body | 200 GameSnapshot hiện tại; private Player chỉ của Principal |

StartGameResponse: `gameSessionId` int64/non-null, `room` RoomResponse/non-null tại Start commit, `serverTimeMs` int64 epoch/non-null tại tạo receipt. Receipt có thể cũ hơn phase hiện tại; GET snapshot để lấy state mới. Start kiểm tra WAITING, Host, revision, >=3 Player và <=maxPlayers, số câu active >=N, Quiz usable (PRIVATE chỉ Owner), tác giả không Player, tất cả User active/chưa USER_ACTIVE_GAME gồm Host Spectator. Cùng Room boundary với config/Join/Leave/Remove; fingerprint/key/retention như pregame ở trên, type START_GAME, questionIndex null, payload revision/questionCount; REST và WS chia sẻ receipt. Không dùng Game ID làm scope Start. Start commit atomic GameSession/roster/GameQuestion/config/resources/UAG/Room ACTIVE; handoff queue chỉ sau commit. Replay không tạo Game/khởi động timer lại, kể cả trận đã kết thúc.

Thứ tự câu được chọn ngẫu nhiên một lần và lưu order1..N; config/bảng điểm/streak/effects/tác giả/title/nội dung/options/correctAnswer/imageRef đều snapshot. Initial20 điểm, floor(N/10) Spin, 1 Star. GAME_MEMBER bất biến, Spectator không PlayerSession nhưng vẫn chiếm UAG. Sửa/xóa Quiz không đổi trận hoặc imageRef bất biến. Kết thúc sau chấm toàn câu: ALL_ELIMINATED/ONE_SURVIVOR trước COMPLETED; tất cả rank1 là Winner kể cả ALL_ELIMINATED (G-01/G-02 người dùng đã xác nhận). SERVER_INTERRUPTED không Winner, giữ ACCEPTED_UNSCORED khi chưa chấm.

### GameSnapshot fields và quyền dữ liệu

| Field | Type / required / nullable | Ý nghĩa |
|---|---|---|
| gameSessionId,roomId,quizId,quizAuthorUserId | int64 / có / không | Identity nguồn/snapshot |
| quizTitleSnapshot | string / có / không | Title cố định từ Start |
| status,phase | string / có / không | ACTIVE/FINISHED; DECISION/QUESTION_OPEN/QUESTION_CLOSED/SCORING/RESULT/FINISHED |
| questionIndex,questionCount,revision | integer,integer,int64 / có / không | Index0 khi mới commit Start, rồi1..N; revision Game commit tăng theo mutation; không so với revision Room |
| serverTimeMs | int64 / có / không | Epoch ms lúc tạo snapshot/event |
| deadlineEpochMs,remainingMs | int64 / có / có | DECISION/OPEN đã mở: epoch deadline và monotonic remaining>=0; CLOSED/SCORING/RESULT/FINISHED/UNAVAILABLE/initializing chưa window: null |
| runtimeState,cleanupPending | string,boolean / có / không | INITIALIZING/READY/RETRYING/FINISHED/UNAVAILABLE; cleanupPending=true chỉ khi terminal runtime lỗi chưa ghi durable cleanup |
| endReason,winners,hasOfficialWinner | string,list<int64>,boolean / có / chỉ endReason nullable | endReason null khi ACTIVE; FINISHED: COMPLETED/ONE_SURVIVOR/ALL_ELIMINATED/CANCELLED/SERVER_INTERRUPTED. Official chỉ true với ba endReason bình thường; CANCELLED/SERVER_INTERRUPTED hoặc ACTIVE false/winners rỗng, vẫn standings |
| config | GameplayRulesSnapshot object / có / không | Schema version1 hiện có: N, durations ms, initial/resources, toàn bộ normal/starOnly/spins/streaks, elimination/ranking/endReason rules; không field đáp án câu hỏi |
| members | list<Member> / có / không | Full GAME_MEMBER gồm eliminated và Spectator; không phụ thuộc socket connected |
| members[].userId,displayName,role,participation | int64,string,string,string / có / không | Roster snapshot; HOST/MEMBER, PLAYER/SPECTATOR |
| members[].playerState,score,totalAnswerTimeMs,rank | string,integer,int64,integer / có / có | Player PLAYING/ELIMINATED, điểm/time/rank competition; Spectator null |
| question | Question object / có / có | DECISION chưa lộ câu; OPEN/CLOSED/SCORING có câu đã mở; RESULT/FINISHED giữ câu hiện tại đã mở. Interrupted trước mở vẫn null |
| question.id,content,options | int64,string,map<A/B/C/D,string> / có / không | GameQuestion snapshot hiện tại; không câu tương lai |
| question.imageRef,correctAnswer | string, A/B/C/D / có / có | Image optional, truy cập endpoint ảnh Task4 với gameSessionId; correctAnswer chỉ non-null sau scoring commit |
| results | list<Result> / có / không | Kết quả câu hiện tại đã scoring; trước đó rỗng. Không biến ACCEPTED_UNSCORED thành kết quả |
| results[].userId,outcome,baseDelta,scoreDelta,scoreAfter | int64,string,integer,integer,integer / có / không | CORRECT/WRONG/NO_ANSWER; không selectedOption/Spin/Star người khác |
| results[].answerTimeMs,totalAnswerTimeMs,winStreak,loseStreak | int64,int64,integer,integer / có / không | Thời gian ms/streak sau chấm, giữ trong result_snapshot; NO_ANSWER dùng full duration, không timestamp received giả |
| results[].hasMomentumBefore,hasRecoveryBefore,hasMomentumAfter,hasRecoveryAfter,momentumConsumed,recoveryConsumed,momentumGranted,recoveryGranted,eliminatedNow | boolean / có / không | Dấu vết effect trước/sau/consume/grant từ scoring, không áp effect mới cho câu tạo streak |
| results[].playerState,eliminatedAtMs,eliminatedQuestionIndex | string,int64,integer / có / chỉ markers nullable | State sau chấm, elimination trace khi bị loại; không mất trong snapshot/history |
| player | Player object / có / có | REST/WS: chỉ Principal/recipient nếu Player; Spectator null. Domain public view không player; WS mapper cá nhân hóa từ state copied của chính commit |
| player.userId,state,score,totalAnswerTimeMs,winStreak,loseStreak | int64,string,integer,int64,integer,integer / có / không | State/time ms/streak sau commit |
| player.momentum,recovery,remainingSpins,starAvailable,remainingSpinPool,starSelected,alreadyAnswered | boolean,boolean,integer,boolean,list<SpinEffect>,boolean,boolean / có / không | Dữ liệu riêng; gameplay commands đã implement Task9 theo WS canonical |
| player.currentSpin,selectedOption,eliminatedAtMs,eliminatedQuestionIndex | SpinEffect,A/B/C/D,int64,integer / có / có | Current decision/answer riêng; elimination trace giữ nguyên qua các câu sau |

Durations/deadline nội bộ monotonic, receivedAt do ingress Server, hợp lệ `<deadline` kể cả processor chậm; không nhận client timestamp. Epoch hiển thị lấy mốc lúc Server mở phase + duration; wall clock lùi trước startedAt được clamp epoch, không đổi monotonic window. Phase mở lấy clock mới sau chờ queue/DB lock, không dùng command/timer cũ. RESULT persist/publish rồi enqueue mở DECISION tiếp, không thêm thời lượng RESULT. GET trong RETRYING trả state committed trước đó; không state tạm. Sau retention Game terminal lấy projection DB, runtimeState FINISHED/không live deadline; History ở Task11B bên dưới. Task10 RECONNECT dùng cùng GameSnapshot, capture/subscribe/send trong session processor; generation/replacement/ordering tại WS canonical, không thêm REST endpoint.

| Error | Điều kiện thực tế |
|---|---|
| 400 INVALID_REQUEST | JSON/field/count ngoài10..50/revision/type không hợp lệ |
| 401 UNAUTHENTICATED; 403 CSRF_INVALID | Auth/CSRF như contract Auth |
| 403 FORBIDDEN; QUIZ_AUTHOR_CANNOT_PLAY | Non-Host/ngoài Game; Author Player |
| 404 ROOM_NOT_FOUND; GAME_NOT_FOUND; QUIZ_NOT_FOUND | Target không tồn tại/PRIVATE non-owner (snapshot kiểm tra membership trước, outsider có thể403) |
| 409 INVALID_STATE; REVISION_CONFLICT; NOT_ENOUGH_PLAYERS; NOT_ENOUGH_QUESTIONS; USER_ACTIVE_GAME; QUIZ_UNAVAILABLE; USER_UNAVAILABLE; INVALID_REQUEST_ID | Guard Start/fingerprint; không commit/broadcast Game |
| 503 ROOM_UNAVAILABLE | Start DB lỗi/commit chưa rõ: Room fence, không retry transaction Start tự động; retryable=false |
| 503 SERVICE_UNAVAILABLE; SERVER_BUSY; RECEIPT_LIMIT_REACHED | Lifecycle chưa ready/admission/capacity/read DB hoặc handoff thất bại; không cam kết Game đã không được tạo |

Body lỗi dùng RoomError `{code,message,retryable,serverTimeMs}` như Room; GameFailure message=code, không SQL/credential. Handoff lỗi sau commit giữ receipt, báo GAME_UNAVAILABLE, compensate SERVER_INTERRUPTED tối đa3 lần100/300ms; retry receipt không Start lại. Scoring/phase/Cancel operation có lỗi transient với rollback chắc chắn: tối đa3 lần100/300ms, giữ FIFO Session, nhường worker khi backoff. Lỗi không transient/commit chưa rõ không chấm lại mù; terminal runtime UNAVAILABLE, stop timer rồi cleanup tối đa3 lần. GAME_END chỉ sau durable commit; nếu DB tiếp tục mất, cleanupPending=true/UAG còn giữ, không báo persisted success; khôi phục MySQL rồi restart để startup cleanup. Send failure không retry scoring đã commit. Chi tiết actual events ở WS contract. Evidence: GameLifecycleIT trên MySQL/HTTP/raw WS và SessionQueueTest; xem trạng thái/evidence mới tại project-status.

## IMPLEMENTED: Cancel và History — Task 11B

Source: game/controller/GameController/GameHistoryController, game/service/GameLifecycle/GameTransactions/GameHistoryService/GameProjection; queue/cache/transport giữ trong realtime. Response no-store, auth cookie/CSRF cũ; không nhận actor userId.

| REST | Auth/quyền | Request / response |
|---|---|---|
| POST `/api/games/{gameId}/cancel` | Auth+CSRF; Host hiện tại của Room và GameMember, cả Spectator/ELIMINATED; command mới chỉ ACTIVE | gameId int64>0; body đúng `{requestId:UUID-lowercase}` required/non-null, không field khác. 200 CancelGameResponse; UUID dùng chung REST/WS, replay trước phase validation |
| GET `/api/games/history?page=0&size=20` | Auth; chỉ FINISHED có GameMember của actor, kể cả LEFT hiện tại/Spectator | page int32>=0, size1..100, offset page*size<=2147483647; sort finishedAtMs DESC,id DESC. 200 GameHistoryPage |
| GET `/api/games/history/{gameId}` | Auth+GameMember; Quiz Owner ngoài roster không tự có quyền; chỉ FINISHED | gameId int64>0. 200 GameHistoryDetail; ACTIVE hoặc pending chưa durable trả409 HISTORY_NOT_READY |

| Response field | Type / required / nullable | Nội dung |
|---|---|---|
| CancelGameResponse.requestId,gameSessionId,revision,serverTimeMs,finalSnapshot | UUID,int64,int64,int64,GameSnapshot / có / không | Sau commit, timestamp/revision/snapshot từ receipt gốc; private player của actor (Host Spectator=null) |
| GameHistoryPage.items,page,size,totalElements | array Item,int32,int32,int64 / có / không | Empty items khi không có lịch sử/hết trang; không list ACTIVE |
| Item.gameSessionId,roomId,quizId,quizTitleSnapshot,startedAtMs,finishedAtMs,endReason,hasOfficialWinner | int64,int64,int64,string,int64,int64,EndReason,boolean / có / không | Metadata snapshot, năm terminal endReason; official flag như GameSnapshot |
| GameHistoryDetail.startedAtMs,finishedAtMs,finalSnapshot,questions | int64,int64,GameSnapshot,array Question / có / không | Durable final state; deadline/remaining null, runtimeState FINISHED, cleanupPending=false; serverTimeMs thời điểm đọc |
| Question.gameQuestionId,questionIndex,content,options,imageRef,correctAnswer | int64,int32,string,map A/B/C/D,string,Option / có / chỉ imageRef/correctAnswer nullable | Chỉ câu đã mở, order1..N gốc; nội dung/ảnh snapshot; correctAnswer chỉ khi scoredAtMs non-null; không câu tương lai |
| Question.questionDurationMs,openedAtMs,deadlineAtMs,scoredAtMs,answers | int64,int64,int64,int64,array Answer / có / chỉ scoredAtMs nullable | ms; deadline gốc. answers sort userId ASC, chỉ rows thật; không tạo NO_ANSWER cho câu Cancel chưa chấm |
| Answer.answerId,userId,answerStatus,selectedOption,receivedAtMs,answerTimeMs,scoredAtMs | int64,int64,enum,Option,int64,int64,int64 / có / selectedOption/receivedAtMs/scoredAtMs nullable | ACCEPTED_UNSCORED/CORRECT/WRONG/NO_ANSWER; NO_ANSWER choice/received null, elapsed=duration; unscored choice/received/elapsed có, scoredAt null |
| Answer.baseDelta,scoreDelta,scoreAfter,result | integer,integer,integer,GameSnapshot.Result / có / nullable | Toàn bộ null với ACCEPTED_UNSCORED; scored rows đầy đủ, Result có streak/effect/time/elimination theo canonical, không suy ra outcome khi chưa chấm |
| Answer.spinEffect,starSelected | SpinEffect,boolean / có / nullable | Spin null khi NORMAL hoặc legacy thiếu trace; Star null khi legacy không lưu. Scored rows mới lưu decision trong result_snapshot; unscored câu hiện tại lấy decision đã commit từ PlayerSession, giữ result_snapshot null |

Cancel/scoring cùng queue: Close đã bắt đầu giữ processor cho cả CLOSED→SCORING→RESULT/retry rồi Cancel; Cancel xử lý trước Close không chấm câu đó. FINISHED bình thường không bị Cancel mới ghi đè. Queued next-phase/timer cũ no-op sau terminal. Transaction gồm standings/FINISHED/CANCELLED/Room WAITING/UAG release; không reset điểm/elimination/resources hoặc cộng elapsed chưa chấm. Runtime/cache/ACK/End chỉ sau commit; giữ actor/receipt10 phút, không crash/restart replay guarantee. finishedAtMs clamp >=started/opened/input/scored timestamp đã commit nếu wall clock lùi, không đổi deadline monotonic.

Abnormal End vẫn final ranking, hasOfficialWinner=false/winners=[]; snapshot.results chỉ câu hiện tại đã chấm, timeline đầy đủ tại History. Startup cleanup ACTIVE bỏ lại FINISHED/SERVER_INTERRUPTED cùng Room/UAG, không phục hồi actor. Hết retry mà DB còn lỗi: runtime UNAVAILABLE/cleanupPending, không persisted End/History success; DB read lỗi503, đọc được nhưng ACTIVE409. Restore MySQL rồi restart để cleanup durable.

Errors:400 INVALID_REQUEST (JSON/UUID/unknown field/ID/page/size/offset);401 UNAUTHENTICATED;403 CSRF_INVALID/FORBIDDEN;404 GAME_NOT_FOUND (detail);409 HISTORY_NOT_READY/INVALID_STATE/INVALID_REQUEST_ID;503 SERVICE_UNAVAILABLE/RECEIPT_LIMIT_REACHED. REST Cancel revalidate session/user khi processor bắt đầu. Replay vẫn kiểm tra identity/membership; cache hết TTL/missing actor503, đọc History/snapshot để đối chiếu. Không serialize Entity/password/SQL/credential.
