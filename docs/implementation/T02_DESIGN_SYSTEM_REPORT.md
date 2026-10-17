# T02 — Dark UI tokens và primitives dùng chung

Hoàn thành ngày **16/10/2026 (Asia/Bangkok)**. Chỉ T02; chưa thực hiện redesign từng feature của T03–T06. Working tree chứa thay đổi T01 chưa commit từ đầu task: dùng snapshot `target/t02-before/manifest.json` để phân biệt thay đổi T02, không lấy toàn bộ diff với HEAD làm phần sửa lần này.

## Kết quả

- Palette xanh đen, surface phân cấp, accent xanh ngọc tiết chế; chữ/success/error/warning/focus có semantic tokens. Không CDN, external font, thư viện UI hoặc build step mới.
- Typography, spacing, card, button primary/secondary/danger, focus/disabled/busy, form/error, toast, inline confirmation, skeleton và empty/error states dùng styles/helpers chung.
- Responsive grids có `minmax(0,...)`, min-width phù hợp, wrap nội dung dài, bảng/arrangement chỉ cuộn trong container. Kiểm tra từ **360px**; không che overflow trang bằng `overflow-x:hidden`.
- Global reduced-motion dừng decorative animations/transitions. Loading/skeleton chỉ CSS; game toast tiếp tục dùng lifetime/deadline hiện có. Không thêm timer hoặc thay gameplay clock.
- Data/text qua DOM text nodes. `h()` từ chối `innerHTML`/`outerHTML`; payload dạng `<img ... onerror=...>` trong feedback primitives hiển thị nguyên văn, không tạo element thực thi.

## File T02 thay đổi

Prefix frontend: `src/main/resources/static/`.

| File | Thay đổi |
|---|---|
| `styles/tokens.css` | Palette semantic, typography, spacing/radius/shadow/motion/sidebar tokens, color-scheme dark. |
| `styles/base.css` | Reset, body/typography/link/selection/focus/hidden và reduced-motion toàn app. |
| `styles/layout.css` (mới) | Main, heading, cards/stat/two-column grids, action/pagination và breakpoints. |
| `styles/components/primitives.css` | Button variants, disabled/busy spinner, card/badge/notice/toast/confirm/empty/error/skeleton/icon styles. |
| `styles/components/forms.css` (mới) | Fields/input/select/textarea/file input, help/error/native invalid presentation, readonly/disabled và form grids. |
| `styles/features/shell.css` | Sidebar/nav/account/connection dùng tokens, responsive shell. |
| `styles/features/pages.css` | Bỏ definitions form/layout đã chuyển về owner chung; auth/room/questionbank presentation dùng tokens. |
| `styles/features/game.css` | Chuyển hardcoded light colors sang tokens, giữ selectors/layout/phase presentation và existing result animation. |
| `styles/features/multimode.css` | Token hóa feedback text answer, arrangement/drag preview/media/clues. |
| `styles/features/catalogue.css` | Token hóa tabs/dropdown/trash, wrap tabs và hit target trash 44px. |
| `styles.css` | Import tokens → base → layout → components → features. Không thêm stylesheet override cuối app. |
| `index.html` | Metadata color-scheme light → dark; không đổi entry/IDs/scripts. |
| `js/shared/icons.js` (mới) | 12 stroke SVG icons tự vẽ, geometry whitelist, decorative `aria-hidden` hoặc named `role=img`. Không tài nguyên tải ngoài. |
| `js/shared/ui.js` (mới) | loadingState/emptyState/errorState/toast/confirmPanel/setConfirmation, không network hoặc timer. |
| `js/shared/dom.js` | Safe text-only API guard; notice có icon + status/alert; field nối help/error bằng aria-describedby, hiển thị validation native và clear khi hợp lệ. Không đổi validation rules hoặc input normalization. |
| `app.js` | Áp dụng shared skeleton/error page cho load/failure, giữ router/session/event flow. |
| `js/features/lobby/rooms.js` | Empty state, shared trash icon và inline confirmation; giữ callback/requestId/revision/delete policy. |
| `js/features/room/page.js` | Inline close confirmation chung, giữ REST close/Host permission/room flow. |
| `js/features/questionbank/page.js` | Empty state, mode icons cùng hệ SVG, inline delete confirmation; giữ API/editor/author policy. |
| `js/features/results/history.js` | Shared empty state cho danh sách History. |
| `js/features/game/page.js` | Chỉ import/call toast presentation helper; giữ state, input renderer, actions, deadlines và toast expiry hiện có. |
| `README.md` | Cập nhật owners/tokens/primitives/accessibility và test guidance. |

Ngoài frontend: thêm `scripts/test-design-system-client.py`, báo cáo này, ảnh QA trong `docs/assets/design-system-2026-10-16/`, cập nhật dòng T02 trong implementation checklist.

Các selector cũ như `.secondary`, `.danger`, `.card`, `.notice` tiếp tục hoạt động. Các màu cũ được thay **tại khai báo gốc**; không còn literal hex trong feature CSS. `!important` chỉ ở `[hidden]` và reduced-motion global để bảo đảm accessibility, không dùng để giải quyết xung đột theme. Hai inline style động có trước (result animation delay và drag-ghost pointer coordinates) giữ nguyên; không thêm inline style mới.

## Keyboard/accessibility

- Hit targets button/input/select tối thiểu 44px; focus-visible outline 3px, không bỏ outline khi dùng bàn phím.
- Native invalid event hiển thị lỗi gần input, `aria-invalid` và unique described IDs; sửa input hợp lệ sẽ clear. Không đánh đỏ toàn bộ fields chưa tương tác, không dùng custom rules thay Server.
- Confirm là **panel nội tuyến**, không khai là modal: mở focus nút giữ/cancel trước, có aria-controls/expanded; Escape/cancel đóng và trả focus về nút mở. Listener nằm trên panel, không tạo document/window listener hoặc focus trap.
- Error có chữ/icon/role alert; info/success/loading/empty có status phù hợp; icon trang trí không đọc lặp. Tên accessible của nút trash giữ nguyên.
- Skeleton không đọc từng line; loading có nhãn và aria-busy. Toast không giữ focus hoặc tạo thời gian/game state mới.

## Kết quả kiểm tra thực chạy

| Kiểm tra | Kết quả / bằng chứng |
|---|---|
| Existing client build/import validator | **PASS**, 28 JS modules / 41 source files; `target/client-build.json`. |
| Java unit + web-only HTTP baseline | **315 tests PASS**, 0 failure/error/skipped; `target/t02-unit.log`, `target/surefire-reports/*-T02.xml`. |
| Final static HTTP check | `SystemWebTest` chạy lại riêng, **4 PASS**, kiểm tra mọi JS/CSS hiện có response 200/nội dung đúng và guard profile; `target/t02-static-http.log`, `*-T02-static.xml`. Không cộng lần rerun này vào 315 như test độc lập. |
| JAR package | **PASS**, `target/t02-package.log`; không tuyên bố clean build. |
| Design system browser fixtures | **14 nhóm PASS, 40 layouts PASS**; `target/t02-design-system-tests.json`. Có kiểm tra hover nút icon giữ danger surface, không bị primary hover ghi đè. |
| Contrast thực đo Chrome | Chữ enabled trong component samples tối thiểu **7.56:1**, vượt gate 4.5:1; disabled controls không tính vào gate. Không suy diễn thành chứng nhận WCAG toàn ứng dụng. |
| Keyboard/native validation | Tab focus ring, confirm safe focus/Escape/focus return, field error linkage/reset **PASS** trong 14 nhóm trên. |
| Responsive | Gallery/login/register/home/rooms/editor/waiting/catalogue/game/history tại **360/390/768/1440px**, có shell/long text trong DTO fixtures; không horizontal page overflow. Inline confirm mở ở 360px cũng kiểm tra không overflow. |
| Reduced motion/text injection | Spinner/skeleton animation disabled theo emulated media; các feedback helpers không tạo img/script từ chuỗi dữ liệu; **PASS**. |
| Real 7-mode network smoke | **93 component assertions + 7 nhóm smoke PASS**, `target/t02-seven-client-tests.json`, `target/t02-seven-browser.log`. |
| Real auth/lobby/room smoke | **15 assertions + 22 nhóm smoke PASS**, `target/t02-auth-lobby-client-tests.json`, `target/t02-auth-lobby-browser.log`. |
| Lifecycle fixtures regression | **19/19 PASS**, `target/t02-lifecycle-client-tests.json`; không leak timer, subscription generation/stale ACK guards giữ nguyên. |
| Vietnamese draft/touch fixture | **10/10 PASS**, `target/t02-vietnamese-draft-tests.json`; có touch drag, keyboard, 320px row scrolling, deadline/snapshot/ACK cases trong simulated transport fixture. |
| Encoding/imports/diff | UTF-8/new-file whitespace, Python syntax, CSS import references và `git diff --check` PASS. |

93 component assertions của suite 7-mode đã chứa các legacy component tests; **không cộng thêm 15 của auth/lobby để gọi là tổng test độc lập**. 40 layout fixtures và 19 lifecycle/10 touch fixtures mô phỏng DTO/transport, không phải 40/19/10 network integration tests. Smoke REST/WebSocket/MySQL được báo riêng.

Không chạy lại toàn bộ 170 MySQL IT ở T02: chúng đã chạy tại T01; Java/server/security/schema không đổi lần này. T02 dùng baseline Java, static HTTP, component/keyboard/contrast/responsive và network smoke phù hợp thay đổi UI. Không lấy số PASS T01 làm bằng chứng runtime T02.

Lệnh chính:

```powershell
python scripts/build-client.py
mvn -o -B --no-transfer-progress test '-DargLine=-Djava.io.tmpdir=D:/BTL_LTM/target' '-Dsurefire.reportNameSuffix=T02'
mvn -o -B --no-transfer-progress test '-Dtest=SystemWebTest' '-DargLine=-Djava.io.tmpdir=D:/BTL_LTM/target' '-Dsurefire.reportNameSuffix=T02-static'
mvn -o -B --no-transfer-progress -DskipTests package
python -X utf8 scripts/test-design-system-client.py --origin http://127.0.0.1:8088 --port 9227
python -X utf8 scripts/test-seven-modes-client.py --origin http://127.0.0.1:8088 --port 9227 --smoke --task 2 --output-prefix t02-seven
python -X utf8 scripts/test-client.py --origin http://127.0.0.1:8088 --port 9227 --smoke
python -X utf8 scripts/test-lifecycle-client.py --origin http://127.0.0.1:8088 --port 9227
python -X utf8 scripts/test-vietnamese-draft-client.py --origin http://127.0.0.1:8088 --port 9227
```

Các runner legacy ghi report tên cũ; đã copy sang report `t02-*` ngay sau lượt chạy để tránh nhầm bằng chứng. UI/lifecycle không ghi DB. Network smoke chạy Server riêng localhost:8088, DB **quizz_task2_test**, Flyway disabled, media ở `target/t02-media`, Chrome headless profile riêng port 9227. Browser plugin gặp lỗi trusted-RPC bootstrap đã biết từ T01, dùng Chrome/CDP helper sẵn có của repo; không thêm browser dependency vào app.

Rà cuối đã giảm specificity của hover mặc định bằng `:where()` để feature button styles có thể áp dụng đúng owner. Lượt kiểm tra hover đầu đọc computed color khi CSS transition chưa kết thúc nên assertion tức thì FAIL; fixture đổi sang chờ đúng computed state rồi kiểm tra (không sleep cố định), rerun **14/40 PASS**. Bản ghi lượt đọc sớm ở `target/t02-ui-hover-initial.json`. Sau chỉnh hover chỉ rerun UI/static checks; không cần chạy lại smoke gameplay dài vì không đổi JS/luồng trò chơi.

Fixture seven-mode `ui2_8f5f626d`: Game IDs `[2856,2858,2859,2860]`, Room IDs `[3226,3230,3231,3232]`. Auth/lobby `ui11a_0a3e7fe1`: Game `[2857]`, Rooms `[3227,3228,3229]`. Không reset/xóa dữ liệu ngoài fixtures, không ghi DB production. Server/Chrome riêng của task được dọn sau verification.

## Ảnh QA đã xem

Các ảnh là **UI fixtures** để kiểm tra tokens/layout, không là bằng chứng thắng/thưởng/gameplay production:

- [Components desktop](../assets/design-system-2026-10-16/components-desktop.png)
- [Login 360px](../assets/design-system-2026-10-16/login-360.png)
- [Confirmation 360px](../assets/design-system-2026-10-16/confirm-360.png)

Ảnh network History thật của browser smoke nằm ở `target/t02-seven-seven-history-mobile.png`; dữ liệu thuộc tài khoản fixture.

## Giới hạn và điểm dừng

Chưa kiểm tra thiết bị vật lý, LAN nhiều máy, Safari/Firefox hoặc screen reader thực; chưa audit WCAG đầy đủ. Layout hẹp dùng Chrome viewport emulation, không gọi là mobile/LAN demo thực.

Hash/snapshot xác nhận **Java, migrations, pom, HTTP/CSRF/WS/protocol/router và game/state không đổi so với đầu T02**. Không thêm API/event/luật game, không đổi policy awards/admin/friendly. Không commit/push; giữ file `1.txt` và các thay đổi T01 đã có. **Dừng tại T02**.
