# WebSocket contract — canonical

Cập nhật TASK 15, 06/10/2026. **IMPLEMENTED: raw WebSocket authentication, Waiting Room, START_GAME, ANSWER/USE_SPIN/USE_STAR/CANCEL_GAME, gameplay replay/lifecycle, RECONNECT và socket replacement tại /ws trong profile mysql.** Giữ envelope v1/transport cũ; History dùng REST canonical. Gameplay/History UI Task12 đã nối transport thật; Task15 chỉ cập nhật trạng thái tài liệu, không đổi wire field/API.

## Handshake và browser authentication — FROZEN

URL cùng Server HTTP: ws://127.0.0.1:8080/ws (HTTPS dùng wss); Localhost/LAN theo bind và ALLOWED_ORIGINS. Browser đã login REST tự gửi cookie HttpOnly JSESSIONID khi gọi new WebSocket; JavaScript không đọc cookie, không tự đặt Authorization header trong constructor. Server lấy Authentication/AuthPrincipal đã được Spring Security phục hồi từ HTTP session; kiểm tra active User và session còn hiệu lực.

Ví dụ sau khi đã Login và lấy CSRF mới theo [REST contract](rest-api-contract.md):

```javascript
const url = new URL('/ws', location.href);
url.protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
const socket = new WebSocket(url);
socket.onmessage = event => console.log(JSON.parse(event.data));
socket.onclose = event => console.log(event.code, event.reason);
```

Handshake yêu cầu Origin HTTP(S) có trong allowlist chính xác; không origin cũng bị reject. Browser phát Origin tự động. Không query parameter, bearer/JWT/query token/userId; không nhận Client identity/timestamp. WS handshake GET không cần header CSRF, dùng cookie + mandatory Origin để chặn mở socket từ site khác. HTTP CORS cũng kiểm tra cùng allowlist. Chỉ endpoint GET /ws được authenticated; profile web-only vẫn403 và không có handler.

| Trước Upgrade | Hành vi đã code |
|---|---|
| Cookie hợp lệ + account active + Origin hợp lệ + không query | 101 Switching Protocols, Principal server-side |
| Chưa auth, cookie giả/hết hạn/logout | 401 UNAUTHENTICATED từ Security filter/handshake |
| Account deleted trước mở socket | 401 UNAUTHENTICATED |
| Origin ngoài allowlist | 403; HTTP CORS framework có thể reject trước interceptor, không cam kết JSON body |
| Thiếu Origin | 403 ORIGIN_FORBIDDEN (AuthError) |
| Có query kể cả userId | 400 INVALID_REQUEST sau auth/origin check (AuthError) |
| DB lỗi khi handshake kiểm tra account | 503 AUTH_UNAVAILABLE (AuthError) |

AuthError field/type theo REST contract. Browser WebSocket API không expose response body/status của handshake thất bại: nhận error/close (thường1006). Client kiểm tra GET me và REST login khi cần; không dựa vào code401 là WS close code.

## Event xác thực cơ sở

Sau kết nối, Server đăng ký socket theo session ID rồi kiểm tra lại account/session để bắt logout xảy ra giữa handshake và onOpen. Server gửi riêng một AUTH_READY; chưa subscribe Room nào và không Game broadcast.

```json
{
  "kind": "EVENT",
  "type": "AUTH_READY",
  "serverTimeMs": 1791156904343,
  "payload": {"userId": 123, "username": "student", "connectionGeneration": 42}
}
```

| Field | Type / required / nullable | Scope |
|---|---|---|
| kind | string / có / không | EVENT |
| type | string / có / không | AUTH_READY |
| serverTimeMs | integer int64 / có / không | Epoch UTC ms Server |
| payload | object / có / không | Danh tính authenticated của socket |
| payload.userId | integer int64 / có / không | ID từ Principal/MySQL, không Client |
| payload.username | string / có / không | Username normalized |
| payload.connectionGeneration | integer int64 / có / không | Generation tăng trong một Server process; identity của connection, không Game revision |

AuthReady record thuộc realtime/message/event. Không password/hash/correctAnswer, không revision/phase vì event chưa gắn Game. AUTH_READY không ACK Login hay receipt command; đây là schema auth Task3, Waiting envelope Task5 ở dưới.

## Session, logout, expiry và close

Mặc định timeout30 phút HTTP inactivity, cấu hình server.servlet.session.timeout hữu hạn/dương. HTTP request cùng session gia hạn thời gian hoạt động; WS text/binary/ping không gia hạn HTTP session. Browser có thể GET me để kiểm tra/tạo hoạt động HTTP; Task3 không thêm heartbeat command.

AuthSessionRegistry theo dõi session login, listener container và sweep mỗi1 giây phát AuthSessionRevoked. Registry này là lifecycle authentication, không timer/ingress gameplay Task7. realtime/connection nghe event và đóng mọi socket của session; auth không phụ thuộc WS concrete. Logout phiên hiện tại không logout phiên độc lập khác của cùng User.

| Close code / reason | Trigger |
|---|---|
| 4001 LOGGED_OUT | POST logout thành công; mọi socket cùng HTTP session đóng |
| 4002 SESSION_REPLACED | Socket authenticated mới của cùng User được đăng ký, kể cả HTTP session khác; gửi event SESSION_REPLACED trước best-effort close |
| 4001 SESSION_EXPIRED | Idle timeout/invalid session; sweep hoặc validation; đóng trong khoảng timeout + lịch sweep, không hard real-time |
| 1008 UNSUPPORTED_MESSAGE | Binary hoặc type chưa implement. START_GAME/ANSWER/USE_SPIN/USE_STAR/RECONNECT/CANCEL_GAME đã code; dùng tên khác không tự thành alias. Không authorize bằng userId |
| 1011 SEND_UNAVAILABLE | Outbound I/O/buffer/send lỗi, đóng connection; DB commit/receipt giữ nguyên |
| 1011 AUTH_UNAVAILABLE | DB access thất bại khi onOpen/nhận frame hoặc broadcast kiểm tra account/session |
| 4001 SESSION_EXPIRED | Account/session không còn hợp lệ khi onOpen/nhận frame hoặc broadcast; reason hiện dùng chung, không event xóa account riêng |

Giới hạn text/binary message8192 byte; container có thể đóng1009 khi vượt giới hạn. Không claim đã đo flood/load hoặc proactive account-delete push. `/ws` dùng chung Waiting/gameplay nên registry giữ một socket authenticated hiện tại/User; socket mới thay socket cũ sau kiểm tra account/session. Disconnect chỉ gỡ binding nếu đúng socket/generation; không bỏ USER_ACTIVE_GAME, loại Player, hoàn resource hay sửa history. Logout session cũ không đóng socket mới của session khác.

## Policy / mức bằng chứng

Handshake chứng minh danh tính User; quyền Room/Game phải kiểm tra trong từng use-case từ authoritative state. PermissionPolicy/AuthorizationService ở auth, bảng quyền trong REST contract. Task8 tích hợp Start/View; Task9 kiểm tra Spectator/outsider/ELIMINATED, client identity giả và auth trước queued replay sau logout. Task11B có Cancel thật: Host theo Room, kể cả Spectator/ELIMINATED; eliminated không Answer/Spin/Star, test bằng cookie/raw WS/MySQL.

Evidence AuthNetworkIT: Java HttpClient CookieManager + WebSocket thật + MySQL, account/register/hash/login/cookie/CSRF/Origin/unauth/forged/userId/logout/expiry. Đây là mô phỏng transport cookie browser, chưa visual/manual browser hay LAN2 máy.

## Phạm vi đã triển khai

Cancel/End/History/failure/cleanup Task11B đã code và kiểm chứng MySQL; gameplay/History UI Task12 và smoke bàn giao Task15 dùng contract này. Không có message thử nghiệm baseline trong contract demo.

Waiting envelope/fingerprint/Room revision Task5, queue/timer/lifecycle Task7/8 và gameplay command/replay Task9 dùng cùng transport. Answer ACK là ACCEPTED_UNSCORED sau commit, chưa correctness. Nguồn gameplay TASKS.md/Overview + G-01/G-02 giữ nguyên; evidence tại [project-status](project-status.md).

## IMPLEMENTED: Waiting Room commands — Task5

Browser login/Origin/cookie/logout/expiry dùng nguyên auth ở trên. Mỗi frame kiểm tra account/session/current generation, và kiểm tra lại sau chờ Room boundary trước khi lookup receipt/persistence. Socket disconnect chỉ gỡ binding, không Leave; `/ws` dùng chung Waiting/gameplay giữ một owner/User theo Task10.

```json
{
  "v":1,"kind":"COMMAND","requestId":"15a1b0be-2ccd-4771-8acf-a717d7f41877",
  "type":"JOIN_ROOM","target":{"kind":"ROOM","id":12},"questionIndex":null,
  "payload":{"roomCode":"ABCD2345EFGH","participation":"PLAYER"}
}
```

User nhập code → REST GET /api/rooms/by-code/{code} lấy id → JOIN_ROOM cùng id/code. Host Create có id/code và ROOM_MEMBER sẵn → SUBSCRIBE_ROOM; không cần Join Draft. Socket mới/reconnect Waiting dùng SUBSCRIBE_ROOM nếu còn JOINED; đây là Room resubscription, không Game reconnect Task10.

| Envelope field | Type / required / nullable | Validation/scope |
|---|---|---|
| v, kind | integer, string / có / không | 1 và COMMAND; không float/overflow |
| requestId | string / có / không | UUID lowercase8-4-4-4-12 |
| type | string / có / không | 4 loại Waiting dưới đây và START_GAME Task8 |
| target | object / có / không | Chỉ kind,id |
| target.kind, target.id | string, integer int64 / có / không | ROOM, positive ID |
| questionIndex | null / có / có | Room command không gắn câu; bỏ field hoặc gửi số đều invalid |
| payload | object / có / không | Fields đúng theo type, không unknown key |

JSON strict: unknown/duplicate fields/trailing JSON/null/type sai → ERROR INVALID_MESSAGE, connection giữ mở. Type chưa code/binary →close1008. Không nhận identity/score/timestamp Client. userId ở REMOVE_MEMBER là **target thành viên**, actor luôn Principal; top-level userId bị reject.

| Type / payload | Permission/state | Side effect / response |
|---|---|---|
| JOIN_ROOM `{roomCode:string,participation:PLAYER/SPECTATOR}` | Auth active, code khớp, WAITING, Quiz còn usable bởi Host; tác giả không PLAYER | New member JOINED, LEFT rejoin cùng row ID; JOINED đổi participation trước Start hoặc no-op cùng participation; capacity Player/tổng100; ACK RoomResponse + subscribe socket + RoomUpdated sau commit nếu thay đổi |
| LEAVE_ROOM `{}` | Auth JOINED non-Host, WAITING | Mark LEFT/time, không delete; private ACK payload `{left:true}`, gỡ subscription socket này; sockets khác của cùng User nhận revocation; RoomUpdated cho member còn lại |
| REMOVE_MEMBER `{userId:positive int64}` | Host, WAITING, target JOINED và không Host | Mark target LEFT; ACK RoomResponse cho Host; member còn lại RoomUpdated; mọi subscribed socket của target nhận revocation và unbind |
| SUBSCRIBE_ROOM `{}` | Auth JOINED, DRAFT chỉ Host; cho WAITING/ACTIVE/CLOSED | Không đổi DB/revision; private ACK RoomResponse, subscribe nếu chưa CLOSED; không event room-wide. ACTIVE chỉ Room config/roster, không bịa Game state |

Host đổi Participate/Spectate bằng REST PUT config.hostParticipation hoặc JOIN_ROOM khi WAITING; Author chỉ SPECTATOR. ACTIVE reject Join/Leave/Remove/config/Close để không thêm/sửa roster hiện tại; CANCEL_GAME Task11B giữ quyền của Host ELIMINATED. CLOSED giữ roster cuối để Get/Subscribe-read, không re-open; event Closed được gửi rồi unbind mọi subscription.

### ACK/error/event fields

| Message/field | Type / required / nullable | Meaning |
|---|---|---|
| ACK.v,kind,requestId,type,target | như COMMAND / có / không | v1, kindACK, tương quan request authenticated |
| ACK.status,revision,serverTimeMs | string,int64,int64 / có / không | ACCEPTED, revision Room commit, epoch ms lúc receipt tạo |
| ACK.payload | object / có / không | RoomResponse theo [REST schema](rest-api-contract.md#response-fields-và-quyền-dữ-liệu); LEAVE chỉleft:true |
| ERROR.v,kind | integer,string / có / không | 1,ERROR |
| ERROR.requestId,target | string,RoomTarget / có / có | null nếu strict parser chưa trả command; không echo input không hợp lệ |
| ERROR.code,message,retryable,serverTimeMs | string,string,boolean,int64 / có / không | message hiện bằng code; không exception/SQL/credential |
| EVENT.v,kind,type,target | integer,string,string,RoomTarget / có / không | 1,EVENT,ROOM_UPDATED hoặcROOM_ACCESS_REVOKED |
| EVENT.eventId,revision,serverTimeMs | UUIDstring,int64,int64 / có / không | Event ID, Room revision commit, epoch send ms |
| ROOM_UPDATED.payload | RoomResponse / có / không | Full Room/config/JOINED roster snapshot, không câu/đáp án/ảnh hoặc Player state |
| ROOM_ACCESS_REVOKED.payload | object `{reason:string}` / có / không | NOT_A_MEMBER, không gửi roster sau Leave/Remove |

Broadcast chỉ tới sockets đã subscribe đúng Room và User còn active/session valid. Event ID dùng chung cho operation gửi nhiều người; revocation có cùng id/revision với RoomUpdated của mutation đó. Caller Join nhận ACK trước event cùng operation; REST response có thể đến trước/sau event nhưng đều sau commit. Changed member/config tăng revision một; no-op Join/Subscribe không tăng. Không broadcast lại cached receipt. RoomChanged domain event không import wire/WebSocket; registry chuyển sang RoomEvent sau commit.

### Request scope, retry, snapshot ordering

Key `(authenticatedUserId, ROOM, roomId, requestId)` bao gồm cả REST Room mutations, không dùng gameSessionId. Fingerprint canonicalSHA256 type/target/index/payload; object keys sort, arrays giữ order. Cùng ID khác fingerprint ERROR INVALID_REQUEST_ID; cùng ID/payload đồng thời chờ boundary rồi nhận cùng ACK, không side effect thứ hai. Auth/target authorization trước cache; state/revision validation sau cache. Business rejection hiện **không cache**, được đánh giá lại khi retry; transient/uncertain DB không được ghi ACCEPT.

JOIN dùng code để authorize receipt ngay cả sau Leave/Close; chỉ replay dữ liệu **đã được chính actor nhận trước đây**, không tự rejoin hoặc grant roster mới. LEAVE replay cho chính actor có historical LEFT row. REMOVE replay yêu cầu actor còn là Host. SUBSCRIBE replay yêu cầu JOINED hiện tại. Với retry JOIN/SUBSCRIBE còn JOINED, sau ACK cũ Server gửi riêng ROOM_UPDATED snapshot **hiện tại**, bắt trong cùng boundary và subscribe nếu chưa CLOSED. Nếu LEFT, Join receipt cũ không subscribe/snapshot mới; muốn Join lại phải requestId mới.

Client retry phải giữ requestId/type/target/index/payload; giữ revision cao nhất từ full snapshots, bỏ snapshot có revision thấp hơn. EventId phân biệt event; ACK cũ vẫn hoàn tất pending request, không ghi đè snapshot mới. GET Room hoặc requestId mới SUBSCRIBE_ROOM trả snapshot hiện tại; không ghép state từ Client. Waiting snapshot không phase/questionIndex/deadline của Game vì chưa có Game state.

Retention/capacity/failure cụ thể ở [REST pregame boundary](rest-api-contract.md#pregame-dedup-và-serialization): Room tới CLOSED+10m,1024receipt toàn Server/512scope/65 admission scope, lockwait5s,32subscription/socket; replay/GET vẫn phục vụ khi cachefull nếu admissionavailable. Dedup memory không survive restart. CREATE_ROOM dùng self scope riêng, không WS type Create. **START_GAME đã dùng keyROOM và boundary này trước khi GameSession tồn tại**, chi tiết dưới đây. Task8 đã nối Game queue/timer; Game command/dedup/reconnect thuộc Task9/10.

Outbound dùng ConcurrentWebSocketSessionDecorator, buffer256KiB/send limit5s, Tomcat blocking send timeout5s; serialize sends per connection. Nếu send/bufferfail đóng connection, không retry DB. Inbound8KiB giữ nguyên. Giới hạn và failure có code thật; chưa đo slow-reader/flood/load/LAN. Pre-game chỉ fair lock queue trên request threads có sẵn, không thread riêng/Room; Task7 bổ sung ingress command/timer và bounded Game workers, không viết lại transport.

ERROR code Waiting tương ứng [Room REST errors](rest-api-contract.md#room-errors), thêmINVALID_MESSAGE. Frame auth failure vẫnclose4001/1011; Room transaction failure ERROR ROOM_UNAVAILABLE, fence mutation tới restart, không giả vờ đã ghi rollback/commit hoặc tự retry operation không rõ kết quả. Test FK rollback thật chứng minh không ACK/broadcast và không đổi DB; chưa chủ động ngắt MySQL ở giữa COMMIT.

Evidence Task5: RoomNetworkIT9 real HTTP/WS/MySQL cases và RoomBoundaryTest5 pure runtime cases; schema constraints Task2 gồm active User Spectator/Room uniqueness. Evidence Task8 bổ sung dưới đây; UI Waiting, LAN2 máy và throughput experiment chưa kiểm chứng.

## IMPLEMENTED: START_GAME và lifecycle events — Task 8

START_GAME dùng envelope COMMAND v1 hiện có, target ROOM/id, questionIndex bắt buộc null. Payload strict `{revision:int64>=0,questionCount:integer 10..50}`, cả hai bắt buộc/non-null. Identity lấy Principal; quyền Host/WAITING/roster/Quiz và các lỗi như [REST Start](rest-api-contract.md#implemented-start-và-game-snapshot--task-8-profile-mysql). Trước operation, socket Host được subscribe Room; chưa đủ capacity thì từ chối trước side effect. Auth/session kiểm tra lại trong Room boundary. REST POST Start và WS command chia sẻ key `(User,ROOM,roomId,requestId)` và fingerprint type/target/null/payload, không phải Game target; giữ requestId/payload khi retry chéo kênh.

ACK START_GAME: trường ACK như Waiting, target ROOM, revision **Room tại Start commit**, payload StartGameResponse `{gameSessionId,room,serverTimeMs}`. ACK sau commit/handoff; lifecycle events có thể tới trước ACK vì processor chạy độc lập. Replay chỉ ACK receipt cũ, không restart phase/broadcast Game lại. ROOM_UPDATED ACTIVE cũng được phát sau Start commit theo Room boundary hiện có. ERROR target ROOM, code/retryable như REST/Waiting; chưa rõ kết quả commit phải giữ requestId và kiểm tra snapshot/receipt, không gửi Start mới mù.

Lifecycle EVENT v1: `kind=EVENT`, `type` theo bảng dưới, `target={kind:"GAME",id:gameSessionId}`, `questionIndex` integer bắt buộc:1..N, null khi GAME_STARTED/index0 hoặc lỗi trước mở câu; `eventId` UUID string, `revision` Game int64, `serverTimeMs` epoch int64, `payload` GameSnapshot; các trường khác bắt buộc/non-null. Payload theo [GameSnapshot](rest-api-contract.md#gamesnapshot-fields-và-quyền-dữ-liệu). Task9 cá nhân hóa `player` từ copied state của chính event commit cho User nhận; Spectator null. Không query state của phase mới để ghép event cũ, không gửi selectedOption/Spin/Star/pool của người khác. `members` toàn roster gồm eliminated/Spectator; không correctAnswer trước scoring commit. Revision Game và Room khác scope.

| Type | Thời điểm/phase trong payload |
|---|---|
| GAME_STARTED | Sau Start commit/handoff; ACTIVE, DECISION, index0, INITIALIZING, deadline null |
| DECISION_STARTED | Sau mở DECISION commit; index1..N, deadline/remaining của window mới, question null |
| QUESTION_START | Sau mở QUESTION_OPEN commit; câu snapshot/options/imageRef, correctAnswer null, deadline mới |
| QUESTION_CLOSED | Đủ valid Answer của toàn roster PLAYING (offline vẫn thuộc roster) hoặc timer; sau CLOSED commit, deadline null |
| SCORING_STARTED | Sau SCORING commit; giữ score committed cũ, chưa kết quả/đáp án |
| QUESTION_RESULT | Sau atomic scoring commit cả câu; RESULT hoặc FINISHED; results đầy đủ score/time/streak/effect before/after/consumed/granted/elimination, đáp án câu hiện tại và private Player của người nhận |
| PLAYER_ELIMINATED | Sau QUESTION_RESULT nếu có eliminatedNow; cùng revision/snapshot, results có userId/state/eliminatedAtMs/eliminatedQuestionIndex. Không cấp effect mới cho người bị loại |
| LEADERBOARD_UPDATED | Sau Result/Elimination; cùng revision, payload.members chứa rank competition/score/totalAnswerTimeMs, kể cả eliminated; Spectator rank/score null |
| GAME_END | Sau durable terminal commit; FINISHED/endReason/hasOfficialWinner/winners/UAG đã release. CANCELLED/SERVER_INTERRUPTED official=false/winners=[], vẫn standings. Sau event này, ROOM_UPDATED target ROOM với RoomResponse WAITING/revision của terminal commit được fan-out theo scope Room |
| GAME_UNAVAILABLE | Terminal **runtime error**, không phải persisted Game End. runtimeState UNAVAILABLE; cleanupPending=true có thể status ACTIVE/phase cũ/score committed cũ; không tuyên bố cleanup hoặc Winner thành công |

Fan-out chỉ socket hiện tại đã subscribe Room, User thuộc GAME_MEMBER và auth/session còn valid; dùng registry cũ. Command gameplay mới subscribe Room trước side effect, sau replay lookup; cached replay chỉ trả ACK cũ và không cần subscription capacity. RECONNECT subscribe và gửi snapshot trong session processor; SUBSCRIBE_ROOM vẫn dùng cho Waiting. Event thứ tự trong Session: QUESTION_RESULT → PLAYER_ELIMINATED nếu có → LEADERBOARD_UPDATED → GAME_END nếu kết thúc → ROOM_UPDATED; nếu chưa kết thúc enqueue DECISION tiếp, không thêm RESULT duration. Same revision có nhiều event, Client không bỏ event chỉ vì revision bằng nhau; eventId chung khi fan-out nhiều User, private player khác nhau theo recipient. Deadline ingress `<deadline`; epoch chỉ hiển thị. Send/buffer lỗi không retry transaction, đóng socket theo policy cũ.

Lỗi DB runtime: retry operation rollback chắc chắn tối đa3 lần100/300ms, giữ FIFO nhưng nhường worker; exhaust/commit chưa rõ → GAME_UNAVAILABLE, stop timer, cleanup SERVER_INTERRUPTED tối đa3 lần. Cleanup thành công mới GAME_END/no Winner; còn mất DB thì giữ cleanupPending/UAG, khôi phục MySQL rồi restart để startup cleanup. Handoff thất bại sau Start commit dùng cùng terminal distinction/3 cleanup attempts, receipt vẫn giữ. Nếu DB không cho kiểm tra auth, WS có thể đóng1011 AUTH_UNAVAILABLE thay vì nhận event; REST read trả503. Không phát Game End persisted giả khi DB down.

Evidence Task8: GameLifecycleIT18 MySQL/HTTP/WS Start/lifecycle. Task9 thêm GameCommandNetworkIT và unit parser/cache/weighted selector. Task10 thêm registry generation và GameReconnectNetworkIT trên MySQL/raw WS/cookie thật; kết quả tại project-status. UI/Cancel/History/LAN/load và cắt MySQL vật lý giữa COMMIT chưa kiểm chứng.

## IMPLEMENTED: gameplay commands, ACK và replay — Task 9

Source: realtime/websocket/GameCommandParser/GameCommandAdapter; message/command/GameCommand, common/GameAck/GameWireError; idempotency/GameReplayCache/CommandFingerprint; session/GameRuntime gọi game/service/GameLifecycle/GameTransactions/SpinSelector. Scoring engine giữ nguyên và không random/network/DB.

| Command field | Type / required / nullable | Validation/scope |
|---|---|---|
| v,kind,requestId,type | integer,string,UUID string,string / có / không | v1/COMMAND, UUID lowercase; type ANSWER/USE_SPIN/USE_STAR |
| target | object `{kind:string,id:int64}` / có / không | GAME, id>0; không ROOM cho gameplay |
| questionIndex | integer / có / không | 1..50 và khi command mới phải khớp index1..N hiện tại. Replay kiểm tra fingerprint trước phase/index hiện tại |
| payload | object / có / không | ANSWER chỉ `{option:A/B/C/D}`; USE_SPIN/USE_STAR chỉ `{}` |

Unknown/duplicate fields, null, float/overflow, client userId/timestamp/effect/score, trailing JSON đều INVALID_MESSAGE; frame biết type nhưng schema sai giữ socket mở, không side effect. Type chưa code/binary vẫn close1008. Actor từ Principal/cookie, không message.

| Type / semantic | Guard command mới | Effect sau commit |
|---|---|---|
| USE_SPIN | Game ACTIVE, PLAYING Player, DECISION/index đúng, receivedAt<deadline; chưa Star/chưa Spin câu này; remainingSpins>0/pool không rỗng | Weighted draw một lần từ pool còn lại, trừ1 Spin, loại effect khỏi pool riêng, lưu currentSpin. Weight từ config snapshot, chuẩn hóa tương đối tổng còn lại; không equal-probability/reroll |
| USE_STAR | ACTIVE/PLAYING/DECISION/index/deadline; starAvailable, currentSpin không HARDSHIP | starAvailable=false/starSelected=true. Spin→Star được; Star-only khóa Spin trong câu này |
| ANSWER (ANSWER_ACK là ACK type ANSWER) | ACTIVE/PLAYING/QUESTION_OPEN/index/deadline; chưa valid Answer Player/GameQuestion | Lưu ACCEPTED_UNSCORED và server elapsed/receivedAt. Một Answer dù đổi requestId. ACK không outcome/correctAnswer/scoreDelta; chưa chấm. Đủ valid Answer toàn roster PLAYING thì đóng sớm/chấm một lần |

ACK gameplay dùng schema ACK v1 như Waiting: `v=1,kind=ACK,requestId,type,target=GAME,questionIndex:int1..N,status=ACCEPTED,revision:int64,serverTimeMs:int64,payload:object`; tất cả bắt buộc/non-null. revision/time là của commit gốc; replay nguyên response, không ghép state mới.

| ACK payload field | Type / required / nullable | Quyền/ý nghĩa |
|---|---|---|
| selectedOption | A/B/C/D / có / có | Chỉ ANSWER non-null, lựa chọn riêng đã ACCEPT; Spin/Star null |
| alreadyAnswered | boolean / có / không | Đã có valid Answer câu này; ANSWER true, DECISION false |
| spinEffect | SpinEffect string / có / có | currentSpin riêng đã commit; null nếu chưa Spin/Star-only |
| starSelected,remainingSpins,starAvailable | boolean,integer,boolean / có / không | Tài nguyên/lựa chọn riêng sau commit |
| remainingSpinPool | list<SpinEffect> / có / không | Pool riêng, thứ tự như snapshot; không gửi cho User khác |

SpinEffect enum BONUS/SAFE/BREAKTHROUGH/SPEED/DECISIVE/HARDSHIP. Error gameplay: `v=1,kind=ERROR,requestId:UUID|null,target:GameTarget|null,questionIndex:integer|null,code:string,message:string,retryable:boolean,serverTimeMs:int64`; mọi field bắt buộc, chỉ correlation fields nullable nếu parser chưa tạo command. message=code, không SQL/credential. ACK/error private về socket gửi, events có payload cá nhân theo recipient. ACK được cache trước send; ANSWER ACK trước close/result trong chính processor, lifecycle event có thể đến từ command khác trước ACK retry.

| Code / retryable | Điều kiện |
|---|---|
| INVALID_MESSAGE / false | Schema/JSON/option/target/index sai |
| INVALID_REQUEST_ID / false | Cùng User+Game+UUID nhưng khác type/target/index/canonical payload |
| FORBIDDEN / false | Spectator/ELIMINATED command mới; ngoài GAME_MEMBER bị từ chối cả replay |
| INVALID_STATE; QUESTION_CLOSED; DECISION_CLOSED / false | Sai phase/index/FINISHED hoặc receivedAt>=deadline dù timer chưa xử lý |
| ALREADY_ANSWERED; ALREADY_SPUN; SPIN_AFTER_STAR / false | ID mới đòi Answer/Spin lần hai hoặc Spin sau Star cùng câu |
| SPIN_NOT_AVAILABLE; STAR_NOT_AVAILABLE; STAR_FORBIDDEN_HARDSHIP / false | Hết resources/pool, Star đã dùng, Khó khăn không cho Star |
| RECEIPT_LIMIT_REACHED; SUBSCRIPTION_LIMIT_REACHED / true | Admission command mới; chưa draw/trừ/commit; replay cũ vẫn lookup trước admission |
| SERVICE_UNAVAILABLE / true | Runtime UNAVAILABLE, DB write/retry exhausted/ambiguous commit, route hết retention hoặc queue full. Không tự đổi ID và replay mutation mới |

Auth/session không valid ở đầu frame **và trong processor trước lookup**: close4001 SESSION_EXPIRED; DB kiểm tra auth lỗi close1011 AUTH_UNAVAILABLE. Logout proactive vẫn4001 LOGGED_OUT từ registry cũ. Không trả ACCEPT khi rollback; DB retry hữu hạn/fence/terminal distinction theo Task8.

Key `(authenticatedUserId,GAME,gameSessionId,requestId)`; pregame giữ Room key cũ, START_GAME không chuyển Game scope. Fingerprint SHA256 `type,target,questionIndex,payload`, keys object sort/arrays giữ order. Auth + membership → lookup/mismatch → replay → admission → phase/permission/resources → draw một lần nếu Spin → transaction → runtime copy → put ACK cache → send, tất cả action/callback trong session processor. Concurrent same ID nhận một response; khác ID vẫn chịu one Answer/Spin mỗi câu. Business rejection không cache; rollback/transient failure không cache ACCEPT. Retry transaction của Spin dùng cùng choice đã capture, không sample lại.

Memory cache một Server, không restart guarantee: ACTIVE +600000ms sau retire/FINISHED theo Task7/8. End không xóa queue/cache; retry sau close/FINISHED/ELIMINATED/Room WAITING trả ACK gốc trước guard mới. Không evict trong retention. Giới hạn thực tế128 receipt/User/Game,8192/Game,65536 toàn Server; admission trước side effect, slot rollback trả capacity. Max gameplay thành công mỗi Player là50 Answer+5 Spin+1 Star=56, đủ dưới128. Sweeper60s xóa queue terminal expired/idle rồi cache/capacity; gameplay action ingress sau TTL bị503, REST/RECONNECT snapshot terminal vẫn đọc DB. Retention xét ingress vào queue; operation đã vào trước hạn có thể xử lý sau. Receipt không durable qua Server restart.

## IMPLEMENTED: RECONNECT và connection generation — Task 10

Socket mới dùng cookie/Origin như cũ, nhận AUTH_READY với generation mới. Đăng ký connection mới là điểm replacement, trước nhận command: registry publish owner theo User nguyên tử; gửi riêng socket cũ `SESSION_REPLACED`, rồi close4002. Event notification best effort nếu socket cũ đã mất mạng; owner mới vẫn có hiệu lực. Không kế thừa subscriptions: dùng RECONNECT cho Game hoặc SUBSCRIBE_ROOM cho Waiting. Client cũ nhận event/close phải dừng auto-reconnect, tránh hai tab tranh nhau.

SESSION_REPLACED: `kind:string=EVENT,type:string=SESSION_REPLACED,serverTimeMs:int64,payload:{connectionGeneration:int64,replacementGeneration:int64}`; mọi field required/non-null, chỉ socket bị thay nhận. Generation lớn hơn generation cũ trong cùng process; không có Room/Game revision vì đây là connection ownership.

| RECONNECT command field | Type / required / nullable | Validation |
|---|---|---|
| v,kind,type,requestId | integer,string,string,UUID string / có / không | 1/COMMAND/RECONNECT, UUID lowercase |
| target | GameTarget / có / không | kind=GAME, id positive int64; User phải thuộc GAME_MEMBER |
| questionIndex | null / có / có | Không biết phase/index hiện tại; Server trả index thực |
| payload | object / có / không | Chỉ `{}`, không userId/generation/client timestamp |

Ví dụ: `{"v":1,"kind":"COMMAND","requestId":"15a1b0be-2ccd-4771-8acf-a717d7f41877","type":"RECONNECT","target":{"kind":"GAME","id":12},"questionIndex":null,"payload":{}}`.

ACK RECONNECT giữ envelope v1: `v=1,kind=ACK,requestId,type=RECONNECT,target,status=ACCEPTED,revision,serverTimeMs,payload=GameSnapshot`; thêm `connectionGeneration:int64` required/non-null. `questionIndex:integer|null` required, 1..N theo snapshot; null trước câu đầu/index0. Revision/time đúng snapshot; payload theo REST GameSnapshot canonical. Identity/role/participation lấy User của AUTH_READY tra members; Host Spectator có player=null. Read này luôn lấy fresh committed snapshot, requestId chỉ correlation, không action receipt/cache, không tiêu thụ resource hay restart timer. Các action receipt User+Game vẫn giữ nguyên và replay trên connection mới.

Errors dùng GameWireError hiện có: INVALID_MESSAGE/false khi sai field; FORBIDDEN/false khi không GameMember (kể cả Game không tồn tại); SERVICE_UNAVAILABLE/true khi ACTIVE không có runtime/queue đầy; SUBSCRIPTION_LIMIT_REACHED/true khi đã subscribe32 Room. Auth hết hạn/DB lỗi theo4001/1011; stale generation close4002. Không trả ACCEPT snapshot nếu read/auth/admission thất bại.

| Phase snapshot | Dữ liệu / quyền |
|---|---|
| DECISION | Role/roster/leaderboard + own score/streak/effects/resources/currentSpin/starSelected; deadline window gốc, question=null/results rỗng |
| QUESTION_OPEN | Chỉ câu/options/image hiện tại, deadline gốc/remaining hiện tại, own alreadyAnswered/selectedOption; correctAnswer=null/results rỗng |
| QUESTION_CLOSED / SCORING | Câu đã mở, khóa input/deadline null, own Answer đã ACCEPT; correctAnswer=null/results rỗng, không state scoring tạm |
| RESULT | Câu vừa chấm/correctAnswer/results đầy đủ score/streak/effect/elimination/leaderboard đã commit; không câu tương lai |
| FINISHED | EndReason/hasOfficialWinner/winners/final standings/trace; giữ kết quả câu cuối đã chấm hoặc ACCEPTED_UNSCORED khi Cancel/interrupted trước chấm; không lộ câu chưa mở |

Ordering: RECONNECT được ingress vào cùng queue Game với command/timer; auth/generation kiểm tra lại lúc bắt đầu. Processor chụp committed snapshot, subscribe và gửi ACK trong cùng turn, trước event từ operation tiếp theo. Trong retry/transaction ACTIVE đang chạy, RECONNECT chờ operation hoàn tất, không đọc state tạm. Sau terminal TTL, cleanup actor idle rồi đọc final DB snapshot immutable; nếu terminal actor còn bận replay cũ hoặc vừa bị cleanup khi enqueue thì cũng đọc DB FINISHED, không chờ actor đã hết retention. Final state không thể tiến thêm; không tạo runtime/trận mới. Client giữ revision theo target Game/Room độc lập, bỏ snapshot/event có revision nhỏ hơn; same revision vẫn xử lý eventId khác nhau. ACK replay cũ xác nhận operation nhưng không ghi đè state mới. Chỉ nhận dữ liệu từ socket/generation hiện tại; không ghép stream socket cũ vào stream mới.

Command cũ chưa bắt đầu processor khi owner đã thay bị reject trước replay/side effect. Check generation trong processor là điểm cho phép bắt đầu operation; operation đã bắt đầu (kể cả finite transaction retry) hoàn tất một lần theo Task8/9, ACK/send cho socket bị thay bị bỏ. Connection mới RECONNECT/retry cùng requestId để biết kết quả thật; không undo operation vì replacement. Old disconnect/logout không thể xóa owner mới. Offline Player vẫn trong roster/gate và bị NO_ANSWER theo deadline; không reset score/streak/Answer/resources/timer. Không cam kết recovery sau Server crash.

## IMPLEMENTED: CANCEL_GAME và terminal cleanup — Task 11B

Cùng `/ws`/envelope v1/adapter/queue/cache, không socket hoặc retry framework mới. COMMAND fields required: v=1,kind=COMMAND,requestId UUID lowercase,target={kind:GAME,id:int64>0},type=CANCEL_GAME,questionIndex=null,payload={}. Không userId/phase/deadline/reason từ Client. Auth/generation/membership kiểm tra lại khi processor bắt đầu; Host hiện tại theo Room được Cancel dù SPECTATOR/ELIMINATED, non-Host403-equivalent FORBIDDEN. Transaction kiểm tra lại Host/ACTIVE.

ACK fields required/non-null: v=1,kind=ACK,requestId,type=CANCEL_GAME,target,status=ACCEPTED,revision:int64,serverTimeMs:int64,payload={finalSnapshot:GameSnapshot}; questionIndex required/null. Payload không giả các resource fields cho Host Spectator; finalSnapshot.player=null khi không có PlayerSession, có standings/phaseFINISHED/endReasonCANCELLED/hasOfficialWinner=false/winners=[]/deadline null. Snapshot Question.correctAnswer và results vẫn chỉ có scoring đã commit; Answer chưa chấm giữ trạng thái/choice ở snapshot private, timeline qua History REST.

Scope `(authenticatedUserId,GAME,gameId,requestId)`, fingerprint type/target/null index/canonical{}. REST Cancel map cùng command/key, không receipt thứ hai. Lookup/mismatch trước phase validation; committed Cancel retry sau FINISHED trả ACK gốc, không phát End/RoomUpdated lần hai. Giữ actor/cache10 phút sau terminal; hết TTL hoặc Server restart không cam kết replay, đọc snapshot/History để đối chiếu. Errors GameWireError như Task9, questionIndex=null: INVALID_MESSAGE/false cho field/index/payload sai; FORBIDDEN/false; INVALID_STATE/false cho Cancel mới sau End; INVALID_REQUEST_ID/false khi reuse UUID khác fingerprint hợp lệ; SERVICE_UNAVAILABLE/RECEIPT_LIMIT_REACHED retryable=true. Session4001/auth DB1011 và replacement4002 giữ nguyên.

Thứ tự: ingress Cancel/timer cùng boundary. Close/scoring operation đã bắt đầu (kể cả finite retry) hoàn tất toàn câu, rồi xử lý Cancel đang chờ; Cancel trước Close không tạo NO_ANSWER/chấm câu. Cancel TX commit → copied runtime FINISHED +cache/ACK → GAME_END → ROOM_UPDATED WAITING. REST response có thể đến trước/sau event nhưng đều sau commit. Timers và queued mở phase cũ no-op; score/streak/effect/elimination/tài nguyên không reset, elapsed của unscored không cộng vào total. Normal terminal không bị Cancel đến sau đổi endReason/Winner.

DB failure giữ policy3 attempts100/300ms cũ: rollback không ACK/broadcast success/cache ACCEPT; exhaust/uncertain → GAME_UNAVAILABLE/stop timer/UNAVAILABLE, cleanup SERVER_INTERRUPTED tối đa3 lần. Durable cleanup commit mới GAME_END/RoomUpdated/release UAG; DB còn mất thì cleanupPending, không fake Winner/history success; restore DB/restart để startup cleanup. History, snapshot và Game End dùng hasOfficialWinner cùng predicate COMPLETED/ONE_SURVIVOR/ALL_ELIMINATED, không xét rank1 để biến abnormal End thành thắng.
