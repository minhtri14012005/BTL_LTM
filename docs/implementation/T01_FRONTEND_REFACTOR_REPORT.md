# T01 — Frontend feature modules, giữ giao diện và hành vi

Hoàn thành ngày **16/10/2026 (Asia/Bangkok)** trên base HEAD `85a03e2`. Chỉ thực hiện T01, không tự chuyển T02/commit/push. Kế hoạch T00 giữ làm snapshot khảo sát; báo cáo này ghi cấu trúc và kết quả triển khai thực tế.

## Thay đổi và ownership

Đã chuyển **13 module cũ** dưới `static/client` vào owners cụ thể, extract thêm các ranh giới đã có; hiện **26 JS modules gồm app entry**, không còn facade `/client`. Không thêm framework, npm dependency hoặc build step.

Prefix các path frontend sau là `src/main/resources/static/`:

| File cũ | File mới / phần extract |
|---|---|
| `client/api.js` | `js/core/api.js` |
| `client/transport.js` | `js/core/transport.js` |
| `client/core.js` | `js/core/protocol.js`, `js/shared/mode-labels.js`, `js/features/auth/validation.js`, `js/features/room/{validation,state}.js`, `js/features/questionbank/validation.js` |
| `client/dom.js` | `js/shared/dom.js` |
| `client/account.js` | `js/features/auth/page.js`, `js/features/lobby/home.js` |
| `client/rooms.js` | `js/features/lobby/rooms.js`, `js/features/room/{page,editor}.js` |
| `client/game-state.js` | `js/features/game/state.js` |
| `client/game.js` | `js/features/game/page.js`, `js/features/results/view.js` |
| `client/history.js` | `js/features/results/history.js` |
| `client/arrangement.js` | `js/features/game/arrangement.js`, `js/features/questionbank/arrangement-editor.js` |
| `client/clues.js` | `js/features/game/clues.js`, `js/features/questionbank/clue-editor.js` |
| `client/video.js` | `js/features/game/video.js` |
| `client/question-banks.js` | `js/features/questionbank/page.js` |
| `app.js` | Giữ entry/composition/shell DOM; dùng `js/core/router.js` mới |
| `styles.css` | Giữ URL entry, import 8 CSS files theo thứ tự cũ |

`js/core` chỉ transport/protocol/router, không DOM hoặc luật game. `App` vẫn giữ một nguồn user/rooms/games state và một Api/Transport. Không tạo folder feature tương lai rỗng; `questionbank` được giữ vì source thật đã có feature này. Backend không chuyển package/file.

CSS được extract thành `styles/tokens.css`, `styles/base.css`, `styles/components/primitives.css`, `styles/features/{shell,pages,game,multimode,catalogue}.css`. Dùng **8 phần liên tiếp**, thay vì 5 phần ước tính ở T00, để giữ chính xác thứ tự cascade/responsive hiện có. Kiểm tra nối các phần và loại whitespace cho kết quả trùng nguyên bản về declarations/selectors/order. Không đổi theme, text giao diện, selector, IDs hoặc animation.

`README.md` ngay trong static mô tả entrypoint, folder owners, lifecycle, CSS order và lệnh kiểm tra. Không đổi `index.html` hoặc các engine/server game modules.

## Lifecycle và contract

- `HashRouter.start()/dispose()` idempotent, giữ một hashchange listener. Parse/hash/query và cùng-route rerender giữ semantics cũ; App vẫn render DOM.
- App dispose page trước khi mount page mới; clear controller references/active route và pause/release video/audio của History/editor. Page async cũ bị loại theo renderVersion; actions không điều hướng/ghi UI sau khi rời route.
- Room controller có dispose guard, bỏ qua ACK/quiz response đến muộn; một subscription đang pending và một successful subscription mỗi connection generation. Remount hoặc connection generation mới vẫn subscribe lấy snapshot chính thức.
- Game controller dispose idempotent clear timer 100ms, bỏ qua event/snapshot muộn; song player gỡ đúng media listeners và giải phóng source.
- **Một HTTP client và một WebSocket transport**; bodies `Api`/`Transport` so với trước T01 trùng nhau sau bỏ dòng import. Không tạo reconnect handler, socket manager, Answer resend hoặc timer authoritative mới.
- Event dispatch vẫn qua Transport → App → controller hiện tại. Không đổi v1 envelope/command/type/payload/ACK/error/deadline/CSRF/session replacement.
- Wire hiện không có `UNSUBSCRIBE_ROOM`. Khi rời route, consumer cục bộ được dispose; subscription Server vẫn theo lifecycle Set/permission/socket của registry hiện có, tối đa 32 Room. Không gửi LEAVE_ROOM khi navigate, không mở socket mới để xóa subscription. Giới hạn Server này không được thay đổi trong T01.

## Các file hỗ trợ đã sửa

| File | Nội dung |
|---|---|
| `src/main/java/vn/edu/multigame/auth/security/SecurityConfiguration.java` | Thay public static GET `/client/*.js` bằng allowlist `/js/core/*.js`, `/js/shared/*.js`, `/js/features/*/*.js`, `/styles/*.css`, `/styles/components/*.css`, `/styles/features/*.css`. Giữ các guard API/WS/CSRF hiện có. Đây là file Java production duy nhất thay đổi. |
| `src/test/java/vn/edu/multigame/system/controller/SystemWebTest.java` | Kiểm tra tất cả JS/CSS source qua HTTP và đúng nội dung ở web-only; legacy client URL bị deny. |
| `src/test/java/vn/edu/multigame/auth/controller/AuthNetworkIT.java` | Thêm test mọi static JS/CSS tải anonymous với mysql nhưng protected API vẫn 401; không mở nhầm JSON dưới path assets. |
| `scripts/build-client.py` | Bổ sung validate CSS imports vào script build/copy đã có. |
| `scripts/test-client.py` | Imports mới; sửa owner PRIVATE catalogue fixture dùng `quizzes?scope=MINE` đúng source trước T01. |
| `scripts/test-game-client.py` | Imports mới theo game/state, game/page, results/view. |
| `scripts/test-multimode-client.py` | Imports mới và room/editor. |
| `scripts/test-arrangement-client.py` | Imports game renderer, questionbank editor, results/history và room/editor. |
| `scripts/test-image-word-client.py` | Imports mới cho questionbank validation/result view. |
| `scripts/test-song-client.py` | Imports mới video/result view. |
| `scripts/test-clues-client.py` | Imports mới editor/released clues/validation. |
| `scripts/test-vietnamese-draft-client.py` | Import game/page theo owner mới. |
| `scripts/test-lifecycle-client.py` (mới) | Browser component fixtures cho router, subscription generation, stale async response/action, ba lần mount/dispose timer và media cleanup. |
| `docs/implementation/MULTIGAME_IMPLEMENTATION_CHECKLIST.md` | Đánh dấu T01 hoàn tất, không duyệt policy hoặc task sau. |

Các namespace ghép trong browser **tests** giữ nguyên assertion bodies khi một module cũ được split; không thêm module aggregate/facade vào production.

## Kiểm chứng thực chạy

| Kiểm tra | Kết quả / bằng chứng |
|---|---|
| Client build/imports | **PASS**, 26 JS modules / 37 source files; `target/client-build.json` |
| CSS equivalence | **PASS**, thứ tự/declarations/selectors của 8 imports trùng CSS cũ; không phải chỉ nhìn screenshot |
| Api/Transport equivalence | **PASS**, implementation bodies không đổi, chỉ paths import |
| Java unit + web-only HTTP | **315 PASS**, 0 failure/error/skipped, 23 classes; `target/t01-unit.log`, `target/surefire-reports/*-T01.xml` |
| Toàn bộ MySQL integration suite | **170 PASS**, 0 failure/error/skipped, 24 classes; `target/t01-integration.log`, `target/failsafe-reports/TEST-*.xml` (lượt này không có suffix dù command đã truyền property) |
| JAR package | **PASS**, `target/t01-package.log`; không phải clean build |
| Browser lifecycle fixtures | **19/19 PASS**, `target/t01-lifecycle-client-tests.json` |
| Browser 7-mode suite | **93 assertions + 7 nhóm smoke PASS**, `target/t01-seven-client-tests.json`; HTTP/WS/MySQL thật cho smoke |
| Browser auth/lobby/room suite | **15 assertions + 22 nhóm smoke PASS** sau sửa route fixture; `target/task11a-client-tests.json`, `target/t01-auth-lobby-browser.log` |
| Static resource HTTP | Tất cả 26 JS + 9 CSS files source được kiểm tra response 200 và đúng nội dung ở **cả web-only và mysql**; browser modules/CSS đã tải để thực hiện smoke |
| Diff/encoding | `git diff --check` PASS; kiểm tra UTF-8/whitespace/fences riêng trên files mới; Python scripts parse thành công |

Các assertion component của suite 7-mode đã gồm legacy component checks, vì vậy **không cộng 93+15 thành số test độc lập**. Lifecycle fixtures mô phỏng transport/clock; không mô tả 19 checks đó là network integration. MySQL IT và smoke là runtime thật.

Smoke đã xác minh đăng ký/login, HttpOnly session qua reload, CSRF và logout; Host/Spectator/Player create/join/Waiting/Start; bảy mode/typed answers/media; reconnect/session replacement; retry cùng Answer/Spin request; final/history/quay Room WAITING, nhiều Game nối tiếp trong cùng browser, cancellation và giữ ACCEPTED_UNSCORED. Ảnh History 390px đã xem: `target/t01-seven-seven-history-mobile.png`.

### Một lỗi fixture đã sửa, không sửa nghiệp vụ để qua test

Lần đầu `test-client.py --smoke` FAIL sau 15 assertions/6 nhóm smoke vì tìm PRIVATE ở route `#/quizzes` mặc định SHARED. Source trước T01 (`target/t01-before/client/question-banks.js`) đã dùng default SHARED. Fixture được sửa sang owner `#/quizzes?scope=MINE`; rerun toàn suite PASS 15/22. Lưu bằng chứng lần lỗi ở `target/t01-auth-lobby-initial-failure.{json,log}`. Không đổi query/policy ngân hàng câu hỏi production.

### Môi trường và dữ liệu kiểm thử

- Java 21.0.9 / Maven 3.9.12 / Python 3.12.10; Chrome headless 155, profile riêng và CDP port 9227.
- Browser plugin lỗi bootstrap trusted RPC; sau đọc hướng dẫn khôi phục vẫn không khởi tạo được, dùng Chrome/CDP helper **có sẵn trong repo**. Không thêm thư viện/dependency/browser tool vào app.
- Server kiểm thử riêng `127.0.0.1:8088`, Spring mysql, datasource **quizz_task2_test**, Flyway disabled cho browser Server (schema được IT kiểm tra trước đó); media fixture ở `target/t01-media`.
- MySQL IT có tạo/ghi fixtures, dùng migrations hiện hữu trong DB test/schema migration-test riêng theo suite. **Không sửa file migration, schema hoặc dữ liệu production; không reset/xóa dữ liệu ngoài fixtures do tests quản lý.**
- Browser seven-mode prefix `ui1_a685794a`: Games 2851–2854, Rooms 3219–3222. Auth/lobby successful prefix `ui11a_71298d64`: Game 2855, Rooms 3223–3225. Lần fixture lỗi có prefix riêng trong initial-failure report. Không xóa mù dữ liệu các lượt đó; IDs đầy đủ nằm trong reports.
- Server/Chrome kiểm thử do lượt này mở được dọn sau verification; không dừng các process có sẵn của người dùng.

Lệnh chính:

```powershell
python scripts/build-client.py
mvn -o -B --no-transfer-progress test '-DargLine=-Djava.io.tmpdir=D:/BTL_LTM/target' '-Dsurefire.reportNameSuffix=T01'
mvn -o -B --no-transfer-progress -Pmysql-smoke '-DargLine=-Djava.io.tmpdir=D:/BTL_LTM/target' '-Dfailsafe.reportNameSuffix=T01' failsafe:integration-test failsafe:verify
mvn -o -B --no-transfer-progress -DskipTests package
python -X utf8 scripts/test-lifecycle-client.py --origin http://127.0.0.1:8088 --port 9227
python -X utf8 scripts/test-seven-modes-client.py --origin http://127.0.0.1:8088 --port 9227 --smoke --task 1 --output-prefix t01-seven
python -X utf8 scripts/test-client.py --origin http://127.0.0.1:8088 --port 9227 --smoke
```

## Giới hạn và điểm dừng

Đã kiểm tra localhost với các browser contexts độc lập, **chưa demo LAN nhiều thiết bị**, chưa Safari/Firefox/điện thoại vật lý; chưa chạy lại suite touch-draft riêng trong T01. Không tuyên bố redesign/accessibility T02 hoặc ADMIN/award/friendly đã được triển khai.

File untracked `1.txt` và hai artifact T00 đã có khi bắt đầu T01 được giữ nguyên ngoài cập nhật checklist được giao. Chưa commit/push. **Dừng tại T01**; mọi policy gate và T02–T18 tiếp tục chưa được thực hiện/duyệt.
