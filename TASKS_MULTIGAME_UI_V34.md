# TASKS — MULTIGAME UI/UX + CHỨC NĂNG V3.4

**Dành cho Codex tại `D:\BTL_LTM`.** Chỉ thực hiện **một task được chỉ định**; bắt đầu từ **T00**. Sau mỗi task báo cáo file sửa, test thực chạy (PASS/FAIL/SKIP), phần chưa kiểm chứng và dừng. **Không tự commit/push, không chuyển task, không xóa/reset dữ liệu.**

**Nguồn nghiệp vụ:** `THIET_KE_NANG_CAP_MULTIGAME_V3_4_ADMIN_ACCEPTANCE_FINAL_REVIEW(1).md` (hoặc tên file V3.4 thực có tại repo). Codex phải tự đọc file này khi làm task liên quan. Không sửa luật 7 chế độ, ScoreEngine/RankingEngine, WebSocket contract, reconnect, CSRF, FIFO; Server giữ quyền quyết định điểm/deadline/thưởng. Mục được đánh dấu **KHUYẾN NGHỊ** trong V3.4 **chưa được phép hardcode thành luật** nếu người dùng chưa phê duyệt.

**Phong cách giao diện đã chốt:** Modern Gaming Dashboard — dark theme, card rõ ràng, màu nhấn tiết chế, responsive, animation nhẹ; không thay framework nếu chưa cần. **Tách thư mục theo chức năng và đúng owner, không trộn Entity/Repository/DTO/Enum hoặc API/WS/UI cùng file.**

**Mẫu yêu cầu:** `Thực hiện Txx trong TASKS_MULTIGAME_UI_V34.md. Chỉ làm Txx, test, báo cáo và dừng.`

---

## T00 — AUDIT & IMPLEMENTATION PLAN (BẮT BUỘC ĐẦU TIÊN)

**Lệnh:** Hãy thực hiện T00, KHÔNG SỬA CODE, DB, migration hoặc file production. Chỉ khảo sát và lập kế hoạch. Nếu được phép tạo artifact kế hoạch thì viết `docs/implementation/MULTIGAME_IMPLEMENTATION_PLAN.md` và bản checklist nếu cấu trúc docs hiện có phù hợp; nếu chưa được phép sửa file thì báo toàn bộ kế hoạch trực tiếp.

Kiểm tra:
- Cây source thật `src/main/java`, `src/main/resources/static`, `templates`, versioned DB migrations, `src/test`, build config.
- Frontend architecture thật: HTML entry, file JS/CSS nào quá lớn/trộn trách nhiệm, `game.js`, login/lobby/room, router/navigation, script dependency, direct global vars, WebSocket dispatch, static resource serving path.
- Server modules: auth/user/room/game/realtime; Game queue, Answer commit, QUESTION_OPEN/CLOSED/SCORING, CloseGate; RoomMember reused row; user_active_game; SocketOutbound FIFO, session registry; award/leaderboard/friendly/admin đã tồn tại hay chưa.
- Kết quả thực tế của `git status`, toolchain, build/test profiles; các hạn chế không được thay đổi.
- Vẽ `Current structure → Proposed structure` có **mapping từng file chính**, số lượng file di chuyển thật sự cần, kiểm soát risk. Không tạo 20 folder trống để làm đẹp.
- Tạo dependency map của T01–T18, dự kiến DB migrations/API/events/tests, tối thiểu cần refactor nào; chỗ nào có blocker.
- Liệt kê toàn bộ mục V3.4 `ĐÃ CHỐT` vs `KHUYẾN NGHỊ`, các policy gate cần người dùng duyệt.
- Rà bốn blocker ADMIN trong V3.4 §18.14; AC-40/41/42; và **test 31 occupancy setup** ở ghi chú test 31/AC-42 trong T09.

**AC/T00:** Có sơ đồ folder đề xuất theo code thật + contract inventory + danh sách test baseline + bảng approval/blockers + task order; không sửa implementation; không nói test runtime PASS nếu chỉ review tĩnh.

## T01 — REFACTOR FRONTEND STRUCTURE, NO BEHAVIOR CHANGE

**Lệnh:** Hãy thực hiện T01; áp dụng cấu trúc feature-based từ cấu trúc feature-based trong T01 ở mức tối thiểu với source thật, **chưa redesign giao diện**.

Công việc:
- Tách các file JS/CSS quá lớn theo `auth`, `lobby`, `room`, `game`, `realtime`, `shared` khi có ranh giới rõ; giữ nguyên event flow/IDs/contracts.
- Chuyển transport WebSocket/HTTP vào hạ tầng tập trung **nếu hiện đang bị copy**, không tạo reconnect handler thứ hai.
- Đảm bảo mỗi feature quản lý subscribe/unsubscribe/timer/listener khi vào/rời màn; tránh leak sau nhiều trận/đổi route.
- Tạo `README` cấu trúc module ngắn trong source/docs hiện có, giải thích folder → trách nhiệm, entrypoint, ownership.
- Không chuyển đổi toàn app sang React/Vue/TypeScript hoặc thêm build step.


**Chuẩn chia folder phải áp dụng khi refactor (điều chỉnh theo source thực tế; không tạo folder rỗng):**
- Frontend `js/core/`: HTTP/CSRF, session, WebSocket transport, router; **không có DOM hay luật game**.
- Frontend `js/features/`: `auth/`, `lobby/`, `room/`, `game/` (renderers theo mode nếu cần), `results/`, `profile/`, `leaderboard/`, `friendly/`, `admin/`. Mỗi feature sở hữu `api`, `state`, `view`, `controller` **khi thật sự cần**.
- Frontend `js/shared/`: UI components và tiện ích dùng từ **ít nhất hai feature**; CSS tại `styles/tokens.css`, `styles/base.css`, `styles/components/`, `styles/features/`.
- Backend: giữ base package hiện có; phân theo owner `auth/`, `room/`, `game/`, `realtime/`, `achievements/`, `leaderboard/`, `friendly/`, `admin/`. Khi cần, dùng subfolder `controller/`, `service/`, `repository/`, `entity/`, `dto/`, `model/`, `security/`; không để Entity/Repository/Enum trộn chung một folder. Không di chuyển code cũ hàng loạt chỉ để giống sơ đồ.
- Không tạo các file `all.js`, `utils.js`, `GameManager.java` khổng lồ chứa nhiều trách nhiệm; không duplicate Socket manager, ScoreEngine hay state nguồn sự thật.

**AC/T01:** Login→Lobby→Room→Game→Result→Room hoạt động như trước; CSRF/session/socket WS không đổi; static imports/scripts không 404; không tạo message listeners trùng; regression hiện có PASS. Nếu browser smoke không chạy được, ghi chưa xác minh.

## T02 — GLOBAL DESIGN SYSTEM & REUSABLE UI

**Lệnh:** Hãy thực hiện T02; triển khai dark UI tokens, base styles và UI primitives dùng chung. Không sửa nghiệp vụ/API.

Công việc:
- Các CSS tokens/base/layout/components theo phong cách Modern Gaming Dashboard trong T02, phù hợp HTML structure hiện có.
- Button variants primary/secondary/danger; focus/disabled/loading states; card, toast, confirm, input validation; skeleton/empty/error.
- Responsive + keyboard navigation, reduced motion. UI primitives tránh innerHTML từ dữ liệu User hoặc escape/sanitize có kiểm chứng.
- Chọn icon nhất quán không phụ thuộc CDN; icon tải trong project nếu cần, không thêm tài nguyên có bản quyền không được phép.

**AC/T02:** Màu/chữ/khoảng cách đồng nhất ở màn đã áp dụng; accessibility cơ bản; không tràn ngang từ 360px; animation không can thiệp timers; không CSS override vô tội vạ hay inline style rải rác.

## T03 — REDESIGN AUTH + LOBBY

**Lệnh:** Hãy thực hiện T03; thiết kế lại Login/Register/Lobby theo Modern Gaming Dashboard trên kiến trúc T01/T02.

- Dùng **chung một trang đăng nhập**. Nếu role ADMIN chưa có trong source ở thời điểm task, **không giả role và không tự thêm login riêng**; redirect ADMIN hoàn thiện tại T07.
- Form trạng thái loading/invalid credentials/network error, không lộ có tài khoản hay không ngoài policy hiện tại.
- Lobby: room create/join/list theo API thật, thẻ phòng, status, action và empty state; không tạo room/mock fake.
- Responsive keyboard/touch; giữ chức năng public/private question pack chỉ khi API hiện có hỗ trợ; không thay policy ngân hàng câu hỏi.

**AC/T03:** Các luồng đăng nhập/đăng ký/lobby giữ nguyên và không ảnh hưởng session/CSRF, role guard ở Server; màn loading/lỗi/empty rõ ràng; LAN static assets tải được.

## T04 — REDESIGN ROOM WAITING / HOST / READY

**Lệnh:** Hãy thực hiện T04; thiết kế màn Waiting Room, Host controls, roster, chế độ và cấu hình trận.

- UI khác nhau cho Host/Player/Spectator do Server kiểm tra; ẩn nút không thay thế auth guard.
- Mã Room dễ copy, danh sách người chơi, connection/ready statuses, stage/mode selection rõ ràng; giới hạn cấu hình từ backend.
- Trạng thái người chơi khi rời/chuyển role/disconnect; không tự coi disconnect là leave/cancel.
- Giữ API START/cancel, command idempotency, room WAITING và test thay đổi question pack theo luật tác giả.

**AC/T04:** 2–3 browser có cùng room state, host permissions đúng, reconnect không tạo người trùng, START đúng Game hiện có, layout desktop/mobile hợp lý.

## T05 — REDESIGN GAMEPLAY FOR 7 MODES (KHÔNG SỬA GAMEPLAY)

**Lệnh:** Hãy thực hiện T05; chỉ nâng cấp Frontend hiển thị/UX gameplay các mode đã có.

- Shell chung: mode header/progress/timer/status/scoreboard; renderer riêng nếu khác nhau theo question/media type.
- Quiz: hiển thị Spin, Star, effect, DECISION, Answer, timer theo **state và tài nguyên Server đã cấp**; không thêm/đổi luật.
- Guess Song: video + âm thanh, trạng thái tải/phát, autoplay restrictions, hành động bật âm thanh theo khả năng trình duyệt; không đếm giờ ngoài deadline Server.
- Vietnamese King, Trick Question, Sequential Clues, Rebus, Ordering: UI phù hợp input/ordering/hints; giữ nguyên payload/normalize answers/chọn đáp án theo contract.
- INTRO/Continue/READY, submited-state, reconnect snapshot, duplicate Answer ACK/error, late Answer; không gửi lại Answer mù khi reconnect.
- Timer client chỉ là hiển thị dựa trên server deadline, **không tự đóng/chấm câu** khi đồng hồ client về 0.
- Mỗi mode có empty/error/timeout/media unavailable states; không lộ đáp án/hint chưa được mở.

**AC/T05:** Gameplay 7 mode hoạt động trên LAN; regression ScoreEngine/RankingEngine, reconnect, Early Close, WS FIFO; interaction state không phản ánh kết quả trước khi Server cho công bố; nhiều trận liên tiếp không leak listeners/timers.

## T06 — RESULT & HISTORY REDESIGN (CHỈ DỮ LIỆU CÓ THẬT)

**Lệnh:** Hãy thực hiện T06; làm đẹp bảng kết quả/History theo API hiện có. Nếu chưa có Award API, **không hiển thị “đã nhận cúp” giả**.

- Top 3/result list, đồng hạng đúng theo ranking Server, own stats, mode breakdown nếu response thật có.
- Nút `Quay lại phòng`/`Chơi tiếp` theo Room status hiện hành, không tạo Room/Game mới ngoài API.
- Dự trù UI state `AWARDS_PENDING`, `AWARDS_NOT_QUALIFIED`, `AWARDS_DECIDED` khi T11 có contract, nhưng không thay đổi business logic ở T06.

**AC/T06:** Game COMPLETED/CANCELLED/SERVER_INTERRUPTED hiển thị lý do thật, không gọi trận bị hủy là thắng; History V1 giữ nguyên; không cấp thưởng do người dùng mở trang result.

## T07 — ADMIN ROLE + SHARED LOGIN + SECURE DASHBOARD SHELL (H1)

**Lệnh:** Hãy thực hiện T07; bổ sung role ADMIN và các API GET admin an toàn, cùng login hiện có.

- `AuthPrincipal` và account model đọc role từ DB/authority Server; login chung, routing ADMIN `/admin`, USER/PLAYER lobby.
- Provision một tài khoản ADMIN bằng cách an toàn/idempotent; hash password, không hardcode plaintext/password yếu/secret trong repo, public registration không chọn ADMIN.
- Server guards `GET /api/admin/**` và route/WS admin; 403 khi PLAYER/Host trái quyền, 401 khi chưa login; không dựa trên hide UI.
- ADMIN UI read-only: đang chơi, users, orphan rooms, audit placeholders **chỉ khi API thật có dữ liệu**. Không cho thao tác kick/ban khi endpoint chưa có.

**AC/T07:** Server xác thực role thật; USER không truy cập data/API ADMIN bằng cách sửa JS/cURL; CSRF/session/logout/WS guard không lùi chuẩn; không có `/admin/login` riêng.

## T08 — ADMIN KICK GAME / QUESTION BOUNDARY / OUTBOUND GUARD (H2)

**Lệnh:** Hãy thực hiện T08 theo tài liệu V3.4 mục 18.4, 18.10–18.15, AC-40, và gate T00.

- ADMIN kick một non-Host Player của **Game ACTIVE**, restriction bền vững theo Game+User, không dùng `ELIMINATED` giả; Game khác tiếp tục.
- Game queue authoritative ordering với Answer/Close; **QUESTION_CLOSED ≠ SCORING_COMMITTED**; giữ `ACCEPTED_UNSCORED` câu đã OPEN và `NO_ANSWER` hợp lệ, không tạo `NO_ANSWER` cho câu chưa OPEN.
- Tách `questionParticipation/scoringSet` khỏi `earlyCloseWaitSet`; offline AFK bình thường vẫn nhận `NO_ANSWER` theo V2 khi có quyền trả lời câu đã mở.
- Restriction check tại REST command, WS command, reconnect, DB snapshot, recipient selection, outbound writer gần send; giữ FIFO, không block Game worker.
- CAS `user_active_game = targetGame`; detach Room membership **theo đúng generation** và giữ Answer/PlayerSession/History; nếu release và marker không cùng transaction cần durable reconciliation.
- UI admin confirm Kick, xử lý ACK/pending và lỗi; UI bị kick hiển thị thông báo phù hợp, về lobby chứ không xem gameplay Game đó.

**AC/T08:** tài liệu V3.4 tests 4–11, 26–29, 37–38 và AC-40; Kick X rồi chơi Y hợp lệ sau release; socket cũ không phát data X mới sau fence; Game worker không chờ network; ScoreEngine/RankingEngine không đổi.

## T09 — TEMP_BAN / INDEFINITE_BAN / UNBAN + EFFECT INTENT (H3)

**Lệnh:** Hãy thực hiện T09 theo tài liệu V3.4 mục 18.5 và 18.7, AC-41/AC-42 và test 31 setup ở ghi chú test 31/AC-42 trong T09.

- BAN commit theo User lock + version/idempotency/audit; **không giữ User lock khi chờ Room/Game worker**.
- Ban có hiệu lực ngay ở login/API/WS/OPEN/outbound. Snapshot Game targets, session cutoff, membership generation và reservation IDs (nếu feature đã có). Không quét lại current targets khi retry intent cũ.
- **Ban commit trước QUESTION_OPEN commit**: OPEN kiểm tra target intent cả khi ejection hoãn, loại khỏi scoring set mới; đảo thứ tự OPEN trước BAN thì vẫn phải chấm câu đã OPEN.
- Revoke all **pre-ban** sessions/sockets theo generation/cutoff, không hồi sinh sau UNBAN; retry ban cũ không kill session mới.
- TEMP/INDEFINITE/UNBAN conflict/version/replay; orphan Room WAITING admin close có audit, không hủy ACTIVE Game/không logout user khác.
- Cleanup membership `generation` và reservation `reservationId` bằng CAS. Nếu FRIENDLY chưa triển khai, thiết kế extension point/schema tối thiểu đúng dependency; **không tạo hệ thống giao hữu sớm chỉ để test reservation**, ghi integration test pending vào T15.
- UI admin user ban control + duration/confirm/expiry server time; UI banned user trạng thái tương ứng.

**Điều kiện dựng test ban → unban → Game Y và AC-42:** Trước khi START Y/JOIN generation mới/ACCEPT reservation mới, phải release hoặc reconcile **hợp lệ** occupancy, membership và reservation cũ; có thể hoãn các side-effect khác để test retry. `membershipGeneration` và `reservationId` chỉ giúp cleanup cũ không phá lượt mới, **không tự giải phóng slot**. Nếu occupancy X chưa giải phóng thì `USER_ACTIVE_GAME` là đúng, không đánh dấu FAIL.

**AC/T09:** AC-41/42, tests ban-unban-OPEN, revocation multiple sessions, stale old request, Host orphan WAITING, restart reconciliation, không xóa Game Y/membership g2/reservation r2; phần r2 được xác minh khi T15 hoàn thiện.

## T10 — AWARD POLICY SIGNOFF & SCHEMA/TRANSACTION DESIGN GATE

**Lệnh:** Hãy thực hiện T10, KHÔNG áp đặt ngưỡng chưa duyệt và không cấp thưởng production.

- Đọc V3.4 §5–7, §10–11, §18.6/18.10 và các feature admin đã có.
- Thiết kế mapping result/award decision/eligibility với source + Game V1/V2 + endReason; transaction `gameResultCommit` khác `awardDecisionCommit`.
- Chuẩn bị migration/API plan, unique keys, idempotent reconciliation, account ban & Game target restriction fence; dữ liệu History không bị sửa điểm.
- Bảng policy người dùng cần chốt: câu/người/correct eligible, danh hiệu, thưởng sau Kick, đồng hạng, tie, ranking, officialGamesCount.
- Nếu chưa được phê duyệt chính sách, dừng phần policy-dependent; **không code 10 câu/3 người/5 thắng thành hằng số production vì đoán người dùng đồng ý**.

**AC/T10:** Có API/DB/transaction/test design cụ thể, nguồn dữ liệu chứng minh từ DB, gate phê duyệt rõ; không thay gameplay.

## T11 — CUP & BASIC ACHIEVEMENTS SERVER + CEREMONY UI

**Lệnh:** Chỉ thực hiện T11 khi policy có liên quan đã được duyệt. Theo V3.4 §4–6, §10, §18.6.

- Award `NORMAL + V2 + FINISHED + COMPLETED` theo ngưỡng **đã được duyệt**; V1/FRIENDLY/CANCELLED/INTERRUPTED không được cúp.
- Durable award decision immutable `PENDING → DECIDED/NOT_QUALIFIED` (hoặc tương đương), idempotent unique, crash/rollback/retry/restart safe. Đọc restricted/ban eligible trong transaction với User-row fence; `CORRECT` phải thuộc **eligible group**.
- Award ranking có thể khác final history rank nếu người đứng đầu bị Kick; **không sửa final score/rank**. Danh hiệu cạnh tranh tính lại trong eligible group.
- Danh hiệu basic Tay nhanh, Bách phát nếu ngưỡng đã duyệt; source of truth là Answer/outcome/timing do Server commit.
- UI ceremony đẹp: Top3, được trao cúp/badge thật, `AWARDS_PENDING`, `NOT_QUALIFIED` và lý do, back to room/leaderboard/profile. Animation không ảnh hưởng quyền nhận thưởng.

**AC/T11:** Award chỉ +1 đúng người và đúng lần; anti-duplicate, retry, concurrent BAN vs award decision commit và crash tests; UI không nói “+1 cúp” trước commit; V1/FRIENDLY không nhận cúp chính thức.

## T12 — PROFILE, GLOBAL LEADERBOARD & UI

**Lệnh:** Hãy thực hiện T12 theo V3.4 §7; cần T11 dữ liệu thành tích đã commit.

- API GET profile public-safe / own profile / leaderboard với pagination và chỉ số đã commit; tuyệt đối không xuất password, session, CSRF, đáp án chưa công bố.
- Nếu lưu projection materialized thì update nhất quán; tránh duplicate source of truth.
- Leaderboard order/tie theo chính sách đã duyệt; Win rate không chia 0; kết quả của trận bị xử lý admin theo award eligibility đã chốt.
- Frontend `profile` và `leaderboard` riêng feature, tab/empty/loading/error, responsive table và highlight vị trí bản thân.

**AC/T12:** Sau logout/login, Room close, new Game vẫn còn đúng thành tích; leaderboard không cộng FRIENDLY; không rò secrets; DB committed snapshot nhất quán.

## T13 — ADVANCED ACHIEVEMENTS (OPTIONAL/SIGNOFF)

**Lệnh:** Thực hiện T13 chỉ với danh hiệu/phạm vi đã được duyệt; theo V3.4 §6.

- Chuyên gia đa tài là **milestone cho MỌI Player đạt ngưỡng**; không award riêng người đầu bảng.
- Lội ngược dòng phải có dữ liệu ranking theo stage đủ chứng minh; nếu thiếu, báo BLOCKED thay vì chế snapshot giả.
- Mọi badge idempotent, không đổi score, dùng eligible group khi phù hợp.

**AC/T13:** đúng numerator/denominator theo `questionParticipationSet`, kể cả AFK `NO_ANSWER`; không tặng badge từ Game không qualified; không mất award khi đổi UI.

## T14 — SAME-ROOM REPLAY UX & REGRESSION

**Lệnh:** Hãy thực hiện T14; đây là nâng cấp UX + regression, không viết lại Game lifecycle.

- Xác minh Room về WAITING sau Game kết thúc, giữ membership; Host cấu hình/Start trận mới, GameSession/Answer/score/tài nguyên độc lập.
- Nút `Quay lại phòng`/`Chơi tiếp` rõ state, Host vs Player; room summary chỉ thêm khi data/API thật có và policy duyệt.
- Kiểm tra 2–3 trận liên tiếp, disconnect/reconnect, thay pack, người rời trước trận mới, START trùng.

**AC/T14:** Room giữ được sau Game 1, START Game 2 không mang tài nguyên/Answer/score cũ; History/Awards Game 1 bền vững; không làm đôi Game.

## T15 — FRIENDLY 1VS1 BACKEND, ATOMIC RESERVATION, SHARED ENGINE

**Lệnh:** Chỉ thực hiện T15 sau khi các ngưỡng và policy cần thiết đã được duyệt; theo V3.4 §9, §10–12, §14.3, §18.5 nếu có ban.

- FRIENDLY_1V1 là **gameType riêng**, đúng 2 PLAYER, không nới START NORMAL xuống 2.
- Invitation/reject/expiry/accept; **ACCEPT reserve nguyên tử cả hai user** và chống race với START normal/ban. Reservation có ID riêng, cleanup theo ID + expected version, không xóa nhầm lần mới.
- A chọn mode 1, B chọn mode 2 khác, Server chọn mode 3 khác cả hai từ nguồn hợp lệ. Không dùng question pack do A/B làm tác giả.
- V2 shared Game/Score/Ranking; **tổng điểm chung qua ba màn**, tie-break y như normal; không `best-of-three`, không reset points/streak/Spin/Star.
- Có reconnect/deadline/NO_ANSWER/ACK đúng contract cũ; WIN/DRAW/LOSS lưu riêng, không cộng chính thức. FORFEIT chỉ triển khai nếu được phê duyệt.
- Ban khi preparing: giải phóng chính xác cả hai reservation; game ACTIVE bị can thiệp không được thành `COMPLETED WIN` để farm badge.

**AC/T15:** Tất cả tests V3.4 §14.3 + ban/cleanup r1→r2 (AC-42); Quiz 5 câu → Spin 0 nếu cấu hình đã duyệt 5; Quiz 10 → Spin theo luật; không thay game NORMAL.

## T16 — FRIENDLY 1VS1 MODERN UI

**Lệnh:** Hãy thực hiện T16 sau khi T15 có API/WS contract thật.

- Profile/lobby challenge button theo availability Server; invitation modal ACCEPT/REJECT/expired.
- Selection two modes + server-selected third; READY, cancellation/preparation timeout và loading/empty/error.
- Versus screen hai avatar, điểm chung/total score/ranking, kết quả giao hữu độc lập khỏi cúp chính thức; rematch có đồng ý cả hai.
- Mobile/keyboard, reconnect view, chỉ nhận thông tin hợp quyền.

**AC/T16:** Race stale invite/status được xử lý từ snapshot Server; không tạo reserve theo local UI; UI không cộng điểm giữa màn; mọi thống kê FRIENDLY tách chính thức.

## T17 — ÔNG VUA THÁCH ĐẤU BADGE + PROFILE UI

**Lệnh:** Chỉ thực hiện T17 khi số trận thắng/đối thủ và ngoại lệ FORFEIT đã được người dùng phê duyệt.

- Badge cosmetic riêng giao hữu; xét chỉ WIN `COMPLETED` đủ đối thủ khác nhau, không dùng FORFEIT, idempotent one-time `earnedAt`.
- Tiến độ lấy từ Server; không cộng cúp, officialGamesCount, officialWinsCount; hiển thị trong `friendly` + `profile`, không ảnh hưởng gameplay.

**AC/T17:** Không unlock sớm, không unlock trùng khi reconnect/replay, logout/login vẫn còn; tránh dựa nickname làm đối thủ duy nhất.

## T18 — FINAL INTEGRATION / POLISH / SECURITY / LAN DEMO

**Lệnh:** Hãy thực hiện T18 sau khi các task được chọn đã hoàn thành; không tự triển khai feature chưa được giao.

- Full test compile/unit/integration/MySQL smoke thực có; đảm bảo no FAIL/skip không lý do, ghi số đúng.
- Multi-browser/mobile layout; a11y/keyboard/reduced motion; test login/room/game 7 modes/awards/profile/leaderboard/friendly/admin (nếu đã triển khai).
- Security negative tests ADMIN API, cross-user data, CSRF, WS subscription/outbound after ban/kick, static deep link; test reconnect/replay.
- Race tests deterministic with barriers cho AC-40/41/42, Answer↔Ban, BAN↔OPEN, Kick↔CLOSE/SCORING, Award↔Ban, stale membership/reservation cleanup. Tránh `Thread.sleep` làm bằng chứng duy nhất.
- Demo LAN 2–3 thiết bị thật nếu môi trường có; nếu chỉ localhost, không tuyên bố đã demo LAN. Tách kết quả runtime thật khỏi review.
- Tối ưu ảnh/svg/fonts/tải page, console errors, code cleanup/references; chuẩn hóa docs theo code thật.

**AC/T18:** Báo cáo cuối các feature hoàn thành/chưa, regression PASS/FAIL/SKIP, các ngưỡng policy đã phê duyệt, hạn chế còn lại, flow demo LAN. Không commit/push.

---
