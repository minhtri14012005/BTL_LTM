# T04 — Waiting Room và cấu hình trận

Hoàn tất ngày 2026-10-16 trên T01–T03. T04 chỉ thay frontend và bổ sung test/tài liệu; không sửa Java, migration, API/event schema, cấu hình production hoặc luật Start/cancel.

## Kết quả giao diện

- Waiting Room có mã mời nổi bật, copy kèm phản hồi và fallback chọn text trên HTTP LAN khi Clipboard API không dùng được.
- Roster hiển thị Host/Player/Spectator, bản thân, số PLAYER trên sức chứa và tổng Spectator từ snapshot Server. Host có edit/open/close và remove ở WAITING như trước; thành viên có leave theo trạng thái hiện có. Vai trò được đổi bằng lệnh JOIN_ROOM hiện có.
- Kế hoạch trận trình bày theo thứ tự màn, tên chế độ, nguồn câu hỏi, số câu, thời gian và cảnh báo nguồn đã xóa. Giữ Quiz v1 và kế hoạch v2; số câu v2 khi START bằng tổng kế hoạch.
- Khu Start nêu điều kiện hiện có và tách trạng thái chờ của thành viên khỏi thao tác Host. Các nút ẩn/disabled chỉ hỗ trợ UI; Server vẫn quyết định quyền và điều kiện.
- Editor có phần thông tin phòng và kế hoạch nhiều màn, hướng dẫn giới hạn 3–100 PLAYER, tối đa 7 chế độ không trùng, tối đa 50 câu, thời gian >0 và author policy. Giữ nguyên options/API/validation thực tế, không tự thêm giới hạn thời gian tối đa. Nút Lên/Xuống dùng được với bàn phím/touch.
- Giữ focus trên control có ID còn hợp lệ khi roster render lại. Không thêm global listener, timer, socket hoặc reconnect handler.

**Giới hạn contract hiện tại:** `RoomResponse.Member` chỉ có membershipId/userId/displayName/host/participation/joinedAtMs; không có presence/ready của từng người. Giao diện chỉ hiển thị kết nối/đồng bộ của trình duyệt hiện tại và điều kiện tối thiểu để Start. Không đánh dấu người khác online/offline/ready bằng dữ liệu suy diễn. Disconnect giữ nguyên membership; không gửi LEAVE/CANCEL. Ready trong INTRO của Game tiếp tục dùng flow Game hiện có, không tạo một ready protocol mới cho Waiting Room. Nếu cần presence/ready riêng từng người ở Waiting Room, cần chốt contract backend ở task riêng.

## Mapping và phạm vi

| File | Thay đổi |
| --- | --- |
| `static/js/features/room/page.js` | Giữ controller/generation/coalescing SUBSCRIBE_ROOM và command flow; compose views mới, giữ focus; khi đổi legacy pack trong lúc fetch cũ pending, tải metadata pack mới sau khi request cũ kết thúc |
| `static/js/features/room/view.js` — mới | Invite/roster/plan renderers thuần DOM từ committed DTO; không là nguồn state khác |
| `static/js/features/room/editor.js` | Bố cục, copy và hướng dẫn cấu hình; giữ form IDs/field names, payload, author rule và save revision |
| `static/styles/features/room.css` — mới | Styles Waiting Room/editor, responsive desktop/mobile; chuyển rules room/member/stage từ file cũ về đúng owner |
| `static/styles/features/pages.css`, `multimode.css`, `static/styles.css` | Bỏ rules đã chuyển và thêm import; không xếp thêm overrides để che CSS cũ |
| `static/README.md` | Ownership, trạng thái thật, lifecycle và test |
| `scripts/test-waiting-room-client.py` — mới | HTTP/WS/MySQL smoke với bốn browser contexts độc lập |
| `scripts/test-waiting-room-ui.py` — mới | Fixtures kiểm soát metadata race/focus/retry/offline/editor |

Không di chuyển file hoặc tạo folder source mới. `target/t04-before/manifest.json` xác nhận toàn bộ Java/resources ngoài static và pom không đổi so với đầu T04; các thay đổi T00–T03 và `1.txt` được giữ nguyên. Core transport/API/router, App dispatch và Game/cancel implementation không đổi.

Contracts giữ nguyên: GET/PUT `/api/rooms/{id}`, POST `/api/rooms`, `/open`, `/close`; WS `SUBSCRIBE_ROOM`, `JOIN_ROOM`, `LEAVE_ROOM`, `REMOVE_MEMBER`, `START_GAME`; `ROOM_UPDATED`, `ROOM_ACCESS_REVOKED`, `GAME_STARTED`. Mỗi retry action giữ frame/requestId/payload đã tạo; REST revision và START revision vẫn do Server kiểm tra. Không tự retry bằng ID mới khi kết quả chưa rõ. Đổi participation làm tăng revision: editor đang mở cần tải lại snapshot trước khi lưu nếu gặp REVISION_CONFLICT.

## Bằng chứng chạy

| Suite | Kết quả |
| --- | --- |
| `python scripts/build-client.py` | PASS — 29 modules / 45 static source files |
| Maven unit/web-only | 315 tests PASS, 0 failure/error/skip; `target/t04-unit.log` |
| SystemWebTest sau thay đổi cuối | 4 tests PASS; kiểm tra anonymous static paths/body, `target/t04-web-final.log` |
| Maven package bản cuối | BUILD SUCCESS; `target/t04-package-final.log` |
| RoomNetworkIT + MultimodeRoomStartIT | 26 tests PASS (16 + 10), 0 failure/error/skip; `target/t04-room-network.log` |
| Auth/lobby regression | 15 browser unit + 22 network smoke PASS; `target/t04-auth-lobby-client-tests.json` |
| Bảy-mode regression | 93 assertions + 7 network smoke PASS; `target/t04-seven-client-tests.json` |
| T04 network smoke, chạy lại trên package cuối | 10 nhóm PASS; `target/t04-waiting-room-tests.json` |
| T04 UI timing fixtures trên package cuối | 7 checks PASS; `target/t04-waiting-room-ui-tests.json` |
| Design system trên package cuối | 14 nhóm / 40 layouts PASS, 360/390/768/1440px; `target/t04-design-system-tests.json` |
| Lifecycle fixtures | 19 checks PASS; `target/t04-lifecycle-client-tests.json` |
| Whitespace/imports/encoding | `git diff --check`, build imports và UTF-8 checks PASS |

T04 network smoke kiểm tra:

1. Bốn session độc lập cùng snapshot và roster thật; member không có Host controls.
2. Member giả Edit/Close HTTP và START_GAME WS bị Server từ chối FORBIDDEN.
3. Đổi pack sang bộ của một PLAYER tác giả bị từ chối nguyên tử, revision/plan không đổi.
4. Tác giả chuyển Spectator thì sửa pack thành công và broadcast tới cả bốn phiên.
5. Giả JOIN thành PLAYER của tác giả bị từ chối; Host không phải tác giả được chuyển Player.
6. Đóng socket không đổi membership/revision; reconnect SUBSCRIBE đúng một lần, không thêm người.
7. Copy fallback chọn đúng mã phòng thật khi không có Clipboard API.
8. Host/Player/Spectator render hợp lý tại desktop 1440px, mobile/touch emulation 360/390px.
9. START vào Game hiện có cho cả bốn phiên; phát lại cùng frame nhận đúng receipt/gameId cũ.
10. CANCEL_GAME hiện có kết thúc Game, đưa Room về WAITING và giữ roster.

Fixtures bổ sung kiểm tra request metadata cũ không chặn pack mới, số câu lấy từ pack mới, focus không mất sau roster update, retry role giữ nguyên frame, offline khóa command nhưng giữ roster, unique modes/giới hạn form và nút đổi thứ tự màn. Các fixtures không được dùng thay bằng chứng multiplayer.

Bảy-mode regression còn kiểm tra private media, ready INTRO, thay socket, reconnect/replay, Final/History, cancel và tái sử dụng Room. Các test trên dùng Chrome 155 với profile test riêng, localhost port 8088, DB `quizz_task2_test` hoặc schema test riêng do integration suite tạo. Không chạy DB production; không sửa migrations. Không chạy lại toàn bộ 170 MySQL IT. Browser plugin không bootstrap được từ phiên trước, nên tiếp tục dùng helper CDP có sẵn trong project với Chrome test riêng.

Chưa xác minh browser engines khác, nhiều máy LAN vật lý, thiết bị touch thật hoặc screen reader thật. Bốn phiên là browser contexts độc lập trên cùng máy; không phải bốn thiết bị vật lý. Static serving mới được kiểm tra HTTP runtime bằng SystemWebTest. Server và Chrome test được dừng sau kiểm tra.

## Visual QA và trạng thái task

Đã mở xem ảnh chụp **API/WS thật** từ T04 network smoke (tài khoản/phòng test):

- [Host desktop](../assets/waiting-room-2026-10-16/host-1440.png)
- [Spectator 360px](../assets/waiting-room-2026-10-16/spectator-360.png)
- [Kế hoạch và chờ Host trên mobile](../assets/waiting-room-2026-10-16/spectator-plan-360.png)
- [Player 390px](../assets/waiting-room-2026-10-16/player-390.png)

Không có policy gate mới được tự duyệt; ADMIN kick/ban và award/leaderboard không thuộc T04. T05 trở đi chưa triển khai trong task này.
