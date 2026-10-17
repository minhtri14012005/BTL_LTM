# Frontend modules

T13: ceremony và own profile dùng nhãn MULTITALENT/COMEBACK từ ledger đã commit; `shared/achievement-ui.js` là nơi đặt nhãn chung. Client không tính ngưỡng hoặc ranking stage. Game START mới nhận policy AWARD_V2 từ Server; receipt AWARD_V1 cũ giữ nguyên. API awards thêm evidence đã đóng băng để kiểm chứng, public profile vẫn chỉ fields A15. `scripts/test-advanced-award-client.py --restart-check` chơi ba mode thật, AFK theo deadline Server và hỗ trợ handshake crash/restart với helper test T11; không thêm listener/timer/socket transport.

T12: `shared/achievement-ui.js` chỉ trình bày stats/tabs/paging dùng bởi profile và leaderboard; `styles/features/profile.css` sở hữu layout hai trang. Mỗi feature sở hữu AbortController và lifecycle qua `App.disposePage`; GET coalesce khi refresh/reconnect, không interval/poll hay socket handler mới. Public chỉ tên hiển thị và stats; own profile mới có badge/receipt và link History. Mọi số liệu/rank do GET Server cung cấp từ T11 đã commit, không localStorage/counter Client. `scripts/test-profile-client.py` chạy smoke với Server/Chrome riêng và DB test.

T09: `features/admin/sanctions.js` sở hữu form TEMP/INDEFINITE/UNBAN và đóng orphan WAITING, dùng confirm/busy/validation chung. Mỗi form giữ UUID/body/expectedVersion qua pending/retry, expiry/trạng thái từ Server; không có timer tự UNBAN. `core/api.js` chuyển lỗi account/epoch về App; transport hiện có chuyển lý do đóng phiên, không thêm reconnect handler. `app.js` dispose trang trước khi về login; mọi UI notice tạo bằng DOM text. Server vẫn là nơi guard ban, epoch và target intent. Xem `docs/implementation/T09_ACCOUNT_SANCTIONS_REPORT.md` về lock/fence/reconciliation và phần reservation pending T15.

T08: `features/admin/kick.js` sở hữu form xác nhận Kick, reason validation và một requestId cố định qua retry/pending; `admin/page.js` chỉ ghép danh sách và panel theo Game được chọn. Gửi qua HTTP/CSRF hiện có, không thêm socket/reconnect handler. `app.js` xử lý control event `GAME_ACCESS_REVOKED`, xóa cache X và dispose Game controller/media/timer trước khi về lobby; lỗi REST/WS `GAME_KICKED` dùng cùng đường xử lý. Event X cũ không làm rời Game Y. `results/history.js` chỉ hiển thị decisions/restrictions khi API FINISHED có dữ liệu thật. Xem `docs/implementation/T08_KICK_REPORT.md` về queue/transaction/fence và giới hạn kiểm chứng.

T07: `features/admin/page.js` sở hữu ba màn chỉ đọc (trận ACTIVE, users, phòng WAITING thiếu Host); CSS ở `styles/features/admin.css`. `app.js` dùng role trả về từ login/me để chuyển ADMIN đến entry `/admin`, cùng `index.html` và form login hiện có. USER tiếp tục lobby. Admin UI chỉ GET qua `core/api.js`, không timer/subscription/socket manager riêng. Server kiểm tra authority, DB role và session trước khi trả dữ liệu; sửa role trong JS không cấp quyền. Hướng dẫn provision nằm trong `docs/implementation/T07_ADMIN_ROLE_REPORT.md`.

Native ES modules, không framework/bundler hoặc build step mới. Spring phục vụ thư mục này từ `/`, không phải `/static`. `index.html` tải `/app.js` và `/styles.css`; app là composition root duy nhất, giữ user/room/game maps và gắn page vào `#view`.

| Thư mục | Owner và trách nhiệm |
|---|---|
| `js/core` | `api.js`: HTTP/CSRF/token epoch; `transport.js`: socket duy nhất, pending ACK và connection generation; `protocol.js`: UUID/envelope/error; `router.js`: một hash listener và parse/navigation. Không DOM hoặc luật game. |
| `js/shared` | `dom.js` tạo DOM an toàn, form validation và action/busy; `ui.js` loading/empty/error/toast/inline confirm; `icons.js` SVG tự vẽ trong repo; nhãn mode dùng nhiều feature. |
| `js/features/auth` | Login/register và validation input. |
| `js/features/lobby` | Home dashboard và danh sách phòng. |
| `js/features/profile` | Own/public profile, summary/badge receipts tabs; own History links chỉ từ ledger của chính mình. |
| `js/features/leaderboard` | Bảng chính thức phân trang, own rank/highlight; không tính rank hay award trên Client. |
| `js/features/room` | Create/edit stage plan, join/waiting controller, validation và revision state. |
| `js/features/game` | Live controller/page, snapshot/receipt/permissions/countdown, arrangement/clues/video renderers đã có. Server quyết định gameplay/deadline. |
| `js/features/results` | Result/standings/image helpers và History. History không import live game controller. |
| `js/features/questionbank` | List/detail/editor, validation và editors arrangement/clues; giữ feature thật của repo. |
| `styles` | Dark tokens ở `tokens.css`, reset/typography/focus/reduced-motion ở `base.css`, responsive grid ở `layout.css`; `components/{primitives,forms}.css` dùng chung; `features/*` chỉ giữ đặc thù màn. |

Không tạo profile/leaderboard/friendly/admin khi chưa triển khai. Backend giữ nguyên base package và feature owners hiện có; T01 chỉ cập nhật allowlist static GET trong SecurityConfiguration cho paths mới.

## Lifecycle và event ownership

- `App` tạo **một Api, một Transport, một HashRouter**. Router start/dispose idempotent. App chuyển connection/event vào controller hiện tại, không thêm message listener theo từng page.
- Mỗi render gọi `disposePage()` trước khi thay DOM: dispose game/room controller, bỏ active room route, pause/release video/audio thuộc page cũ. Response page cũ bị loại bởi renderVersion; action async kiểm tra cả user và route version.
- Room controller chỉ xử lý update/ACK khi còn current; coalesce SUBSCRIBE_ROOM đang pending, mỗi connection generation subscribe một lần. Khi rời màn, dispose hủy consumer cục bộ và bỏ callback reference. **Không gửi LEAVE_ROOM khi navigate**.
- Wire hiện không có UNSUBSCRIBE_ROOM. Subscription Server vẫn theo registry hiện có (Set giới hạn 32, loại khi mất quyền/đóng Room/socket); T01 không thêm command, không tạo socket thứ hai để reset subscription. Revisit cùng Room không tạo entry subscription trùng trên Server.
- Game controller sở hữu timer hiển thị 100ms, media và async receive/reconnect; dispose idempotent clear timer và video handlers. Không resend Answer mù hoặc tự close/score ở client.
- DOM listeners của auth/editor/result nằm trên node của page; node bị tháo sẽ được thu gom, không đăng ký window/document listener riêng. Playback History/editor được App dọn khi rời page.

## CSS và kiểm tra

`styles.css` import theo thứ tự tokens → base → layout → components → features. T01 giữ nguyên cascade cũ; T02 thay palette tại tokens và khai báo gốc, không thêm một stylesheet cuối để đè màu toàn app. Màu phải qua biến semantic, không rải hex trong feature CSS. `[hidden]` và reduced-motion là hai quy tắc global có chủ ý dùng `!important` cho accessibility.

Button mặc định/`.primary`, `.secondary`, `.danger`, `.danger.secondary` dùng cùng sizing/focus/disabled states. `busy()` giữ nguyên trạng thái disabled có trước và đặt `aria-busy`; CSS hiển thị spinner ở submit. `field()` nối help/error bằng `aria-describedby` và trình bày lỗi native khi invalid; không đổi rules/normalize input.

UI helpers nhận text hoặc DOM nodes, không chèn chuỗi HTML. `h()` từ chối innerHTML/outerHTML. Icons là path SVG gốc tự vẽ trong `icons.js`, không cần CDN/font/license tải ngoài. `confirmPanel()` + `setConfirmation()` là panel nội tuyến: focus nút an toàn trước, Escape/cancel trả focus, không focus trap hoặc global listener. Toast/loading skeleton chỉ trình bày; owner màn giữ timer/deadline hiện có.

```powershell
python scripts/build-client.py
mvn -o -B --no-transfer-progress test '-DargLine=-Djava.io.tmpdir=D:/BTL_LTM/target'
```

`build-client.py` có từ trước: kiểm tra JS/CSS imports và copy output vào `target/client`; serving runtime vẫn là Spring resources. SystemWebTest/AuthNetworkIT kiểm tra mọi JS/CSS qua HTTP với profile web-only/mysql. Browser scripts import theo owner mới; các namespace ghép trong **test fixture** chỉ để giữ nguyên assertions, không phải facade production.

`scripts/test-lifecycle-client.py` kiểm tra route/subscription/dispose bằng fixture trong Chrome riêng. `scripts/test-seven-modes-client.py --smoke` và các smoke suite ghi fixture vào DB của Server được chọn: chỉ chạy với DB test, không trỏ vào production. Báo cáo T01 trong `docs/implementation/T01_FRONTEND_REFACTOR_REPORT.md` ghi kết quả thực tế và giới hạn.

`scripts/test-design-system-client.py` kiểm tra contrast, keyboard, validation, text injection, reduced motion và 40 layout fixtures (có shell) tại 360/390/768/1440px. Đây là fixtures UI, không thay smoke network thật. Xem báo cáo T02 tại `docs/implementation/T02_DESIGN_SYSTEM_REPORT.md`.

T03: `features/auth/page.js` sở hữu form chung login/register, loading/error, password visibility và chặn submit trùng theo từng form. Không có listener global hoặc timer. `features/lobby/home.js` tổng hợp đúng ba API hiện có; `features/lobby/rooms.js` sở hữu renderer thẻ phòng dùng chung cho home và danh sách (chỉ danh sách có xóa/rời phòng). Styles tương ứng ở `styles/features/auth.css` và `lobby.css`; không đổi transport/session/CSRF. Chưa có ADMIN trong source nên không suy diễn role từ response. `scripts/test-auth-lobby-client.py` kiểm tra lỗi/pending/stale response và DTO phòng bằng fixture; chạy `scripts/test-client.py --smoke` riêng với DB test để kiểm tra network thật.

T04: `features/room/page.js` tiếp tục sở hữu controller/subscription/commands của Waiting Room. `view.js` chỉ dựng invite/roster/stage plan từ committed DTO, không giữ state hoặc socket riêng; `editor.js` sở hữu cấu hình, `styles/features/room.css` sở hữu styles. Room DTO chưa có online/ready từng người: chỉ trạng thái kết nối của trình duyệt hiện tại được hiển thị, danh sách vẫn là membership khi disconnect. Copy dùng Clipboard API và fallback chọn text cho HTTP LAN. Focus có ID còn hợp lệ được giữ qua render roster. `scripts/test-waiting-room-client.py` chạy bốn phiên độc lập với API/WS thật; chỉ dùng DB test.

T05: `features/game/page.js` giữ snapshot/draft/pending command, một interval hiển thị 100ms và vòng đời reconnect. `view.js` dựng shell/mode/progress/timer/status; `answers.js` dựng Quiz/text và nhận callbacks, không gọi API hoặc giữ nguồn state riêng. Arrangement/clues/video giữ renderer chuyên biệt. `publishedResult()` chỉ cho hiện đáp án đúng/outcome ở RESULT hoặc FINISHED có results; không dự đoán từ input/ACK. Progress dựa trên vị trí câu và kết quả đã công bố. Timer dùng server deadline + monotonic elapsed, về 0 chỉ hiển thị chờ chuyển pha; guard khóa ô Vua Tiếng Việt ở deadline đã có từ trước được giữ, không đóng/chấm câu.

Song giữ một video DOM mỗi question, seek theo openedAtMs và thời gian trận; không tạo timer media riêng. Owner tháo cả sáu media event handlers, dừng/release nguồn khi đổi câu/rời route; promise play đến muộn không cập nhật player đã dispose hoặc đã lỗi. Bật/tắt tiếng là lựa chọn trình bày trong memory của game. Retry Answer chỉ do người dùng bấm và giữ nguyên frame; reconnect chỉ lấy snapshot. Gameplay CSS ở `game.css` + `game-modes.css`; `multimode.css` chỉ còn layouts soạn câu hỏi.

T05 kiểm tra: `test-gameplay-ux-client.py` là fixtures clock/media/UI (không thay bằng chứng network); `test-seven-modes-client.py --smoke` kiểm tra network thật và có thể chạy qua LAN origin được cấu hình trên server test. Suite `test-game-client.py --smoke` tạo phòng v1 bằng API thật để giữ đúng regression Quiz legacy vì UI tạo mới dùng kế hoạch v2. Suite Song/Rebus nhận `--video-directory`/`--image-directory` để test thiếu media chỉ tác động file upload thuộc fixture trong storage test tương ứng. Chạy Python với `-X utf8` trên Windows khi cần in Unicode. Chỉ trỏ các smoke vào DB/storage test.

T06: `features/results/view.js` trình bày rank/score/time và nhóm hạng 1–3, giữ tất cả đồng hạng; `history.js` tải list/detail và compose; `timeline.js` dựng câu/answer đã lưu, chỉ công bố đáp án khi scoredAtMs có giá trị. Breakdown v2 chỉ cộng scoreDelta/count của các result câu đã có trong response, không tính lại điểm cuối/rank. V1 tiếp tục dùng totalAnswerTimeMs; v2 dùng totalCorrectAnswerTimeMs.

`results/navigation.js` đọc GET Room hiện hành trước khi hiển thị hành động. WAITING Host có “Chơi tiếp cùng phòng” dẫn về cùng route Room, không tự START; ACTIVE/DRAFT/CLOSED có nhãn tương ứng, không có quyền thì không dựng link. Có nút cập nhật status; route Room kiểm tra lại bằng GET khi vào. Game owner coalesce lookup qua render và dispose cùng controller; History bỏ response cũ theo renderVersion/user. Không thêm polling, timer hoặc socket listener. CSS ở `styles/features/results.css`, không đổi gameplay rules.

T11: `results/awards.js` sở hữu ceremony view và một GET/poll có AbortController; Game dispose cùng controller, History dispose qua `app.awardController`. Poll dừng khi terminal/rời route, bounded 60 lần rồi đọc lại thủ công. API chỉ đọc decision/ledger đã commit; không phát thưởng do mở trang, event hoặc animation. `core/api.js` nhận signal tùy chọn, không có DOM hay reconnect handler mới. Room omits internal policy banners; award eligibility remains enforced by Server. Styles ceremony tại `styles/features/awards.css`, icon trophy SVG tự vẽ trong project. Profile/global leaderboard và advanced badges chưa triển khai; không dựng link giả đến tính năng chưa có API.

`scripts/test-award-client.py` dùng Game/HTTP/WS/award thật trên DB test; `--restart-check` phối hợp orchestrator dừng/chạy lại **JVM test do runner tạo**, helper giữ User lock chỉ sau result commit để có PENDING thật. Không dùng server/DB vận hành hoặc Chrome cá nhân. `test-results-ui.py` và `test-results-client.py` tiếp tục kiểm tra Result/History/Room lifecycle cũ.
