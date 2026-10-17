# T05 — Gameplay Frontend/UX bảy mode

Hoàn tất ngày 2026-10-16 trên T01–T04. Chỉ thay frontend, test harness và tài liệu. Không sửa Java, ScoreEngine/RankingEngine, API/events, migrations, cấu hình production hoặc luật/tài nguyên gameplay.

## Giao diện và ownership

| File | Trách nhiệm cuối |
| --- | --- |
| `static/js/features/game/page.js` | Controller/snapshot/drafts/pending command/reconnect và interval hiển thị duy nhất; compose shell/renderers; feedback lỗi là alert; giữ focus control hợp lệ |
| `static/js/features/game/view.js` — mới | Mode header/icon, progress, phase timer, interaction status, hướng dẫn mode, loading ảnh và gate kết quả công bố |
| `static/js/features/game/answers.js` — mới | Quiz options và text answer renderers nhận state/callbacks; không gọi transport/API, không giữ nguồn state riêng |
| `static/js/features/game/arrangement.js` | Giữ stable IDs/payload, drag/touch/keyboard; bổ sung số ô/mục đã chọn và empty/error state |
| `static/js/features/game/clues.js` | Chỉ render hint prefix đã nhận; số gợi ý đã mở và từng mốc rõ hơn; không tự mở hint theo clock |
| `static/js/features/game/video.js` | Một video DOM/question, loading/buffering/blocked/playing/paused/ended/error; bật/tắt âm thanh, giữ playhead theo Server, dọn handlers/source và bỏ qua promise phát đến muộn |
| `static/styles/features/game.css` | Shell/header/progress/timer/scoreboard/Quiz/text layout, responsive và result presentation |
| `static/styles/features/game-modes.css` — mới | Arrangement, clues và media; chuyển rules tương ứng ra khỏi `multimode.css` |
| `static/styles/features/multimode.css`, `static/styles.css` | Giữ authoring layouts, cập nhật imports; bỏ rules gameplay đã chuyển thay vì đè cascade |
| `static/README.md` | Ownership, timer/media lifetime, contracts và cách chạy test |

Không di chuyển file hoặc tạo folder source mới; thêm hai JS renderer modules và một stylesheet trong owners đã có. Icons là SVG nội bộ T02, không CDN/font/build step/framework mới. Các giá trị từ tài khoản/câu hỏi/hint vẫn đi qua DOM text nodes; không thêm innerHTML hoặc HTML từ dữ liệu.

Shell dùng mode/stage/question index và kết quả đã công bố từ snapshot. Progress không suy ra câu đúng/sai. Scoreboard tiếp tục dùng rank/score/time của Server; bảng có vùng cuộn riêng khi cần. Kết quả cuối vẫn dùng Final/History hiện có, không fake award hoặc tính lại điểm.

## Hợp đồng và hành vi được giữ

- Quiz: giữ DECISION, Spin/Star/effect/remaining resources từ `player` do Server cấp; pool hiển thị đúng các hiệu ứng còn trong tài nguyên cá nhân. Không tính xác suất, không tạo Spin/Star mới, không dự đoán score/outcome từ lựa chọn.
- Song: giữ URL media được cấp theo source stage + gameSessionId, openedAtMs, playhead và deadline. Phát muted nếu cần; thao tác bật tiếng/phát xử lý hạn chế autoplay. Tải chậm/video hết hoặc lỗi không gia hạn câu và không khóa input text hợp lệ. Không tạo timer media thứ hai.
- Vua Tiếng Việt/Ordering: giữ nguyên `itemIds`, dấu/khoảng trắng/nội dung từng ô và stable IDs; không tự normalize hoặc diễn giải đáp án. Guard thao tác Vua Tiếng Việt ở deadline đã có từ trước được giữ.
- Riddle/Clues/Rebus/Song: giữ nguyên payload `{text: ...}`; `.trim()` chỉ quyết định nút gửi có rỗng, không rewrite payload. Hints chỉ lấy từ prefix Server đã gửi; không tải nguồn câu hỏi hoặc đáp án tương lai.
- `CONTINUE`, INTRO/readyPlayers, `ANSWER`, `USE_SPIN`, `USE_STAR`, `CANCEL_GAME`, `RECONNECT` và ACK/error/event contracts không đổi. HTTP snapshot, session/CSRF, App dispatch và core transport không đổi.
- Chưa có ACK thì chỉ hiển thị đang gửi/chưa rõ kết quả; ACK accepted thì hiển thị đã nhận và khóa gửi theo Server state. Màu đúng/sai và đáp án chỉ hiện ở RESULT hoặc FINISHED **có results đã công bố**, không hiện trong SCORING dù một fixture cố đưa dữ liệu kết quả vào sớm.
- Reconnect chỉ lấy snapshot. Không tự gửi lại Answer đang pending; nút retry do người dùng bấm giữ nguyên frame/requestId/questionIndex/payload. Receipt cũ không rollback phase/revision mới. Late Answer/error không tự tạo accepted state hoặc điểm.
- Interval 100ms tiếp tục dùng `deadlineEpochMs`, `serverTimeMs` và monotonic elapsed. Về 0 chỉ hiển thị chờ Server chuyển pha; không gửi CLOSE/SCORING, không tự chuyển QUESTION_OPEN thành CLOSED/RESULT. Dừng video khi ngoài thời gian phát cũng không đổi Game state. Result window hiện có vẫn dựa deadline đã được Server cấp.

Dispose giữ một game interval và tháo cả sáu media handlers khi rời màn/đổi question; pause/release video source. `play()` resolve/reject sau dispose hoặc sau media error không ghi đè UI mới/lỗi hiện có. Trạng thái pause/ended không bị ghi lại vào live region mỗi tick. Không thêm listener window/document/socket theo mode.

## Kết quả thực chạy

| Suite | Kết quả và bằng chứng |
| --- | --- |
| Static import build | PASS — 31 modules / 48 source files; `python scripts/build-client.py` |
| Java unit/web-only | 315 tests PASS, 0 failure/error/skip; `target/t05-unit.log` |
| SystemWebTest sau sửa production cuối | 4 PASS; anonymous static paths/body; `target/t05-web-final.log` |
| Package | BUILD SUCCESS; `target/t05-package-final.log` |
| Toàn bộ MySQL IT | 170 PASS, 0 failure/error/skip; `target/t05-mysql-regression.log` |
| Bảy mode qua LAN origin | 93 assertions / 7 network smoke PASS; `target/t05-lan-seven-client-tests.json` |
| Quiz v1 | 39 assertions / 10 network smoke PASS; `target/t05-quiz-v1-client-tests.json` |
| Song, H.264/AAC và autoplay thật | 83 assertions / 7 network smoke PASS; `target/t05-song-client-tests.json` |
| Rebus/image thật | 78 assertions / 6 network smoke PASS; `target/t05-image-word-client-tests.json` |
| Sequential Clues thật | 93 assertions / 5 network smoke PASS; `target/t05-clues-client-tests.json` |
| Vietnamese + Ordering thật | 74 assertions / 6 network smoke PASS; `target/t05-arrangement-client-tests.json` |
| T05 clock/media/UX fixtures | 19 checks / 28 mode×viewport cases PASS; `target/t05-gameplay-ux-tests.json` |
| Vietnamese draft/touch/deadline fixtures | 10 PASS; `target/t05-vietnamese-draft-tests.json` |
| Lifecycle fixtures | 19 PASS; `target/t05-lifecycle-client-tests.json` |
| Design system/layout | 14 nhóm / 40 layouts PASS, 360–1440px; `target/t05-design-system-tests.json` |
| LAN static assets bản cuối | 47 JS/CSS/HTML trả HTTP 200 và body khớp source; `target/t05-lan-assets.json` |
| Whitespace/encoding/scope | `git diff --check`, UTF-8 và protected-source hashes PASS |

Counts browser có phần assertions dùng chung giữa suite, không cộng lại thành số test độc lập. Clock/media mocks chỉ xuất hiện trong focused fixtures; không dùng chúng làm bằng chứng autoplay, LAN hoặc multiplayer.

315 unit bao gồm ScoreEngine 113, MultimodeScoreEngine 56, RankingEngine 6, SessionQueue 22 và SocketOutbound 6. Full IT bao gồm GameLifecycle 18, MultimodeLifecycle 13, GameSlowSocket 2, SongLifecycle 3, CluesLifecycle 7 cùng các suite protocol/authorization/history/arrangement/image/stage hiện có. Early Close/CloseGate, wait-set/offline, accepted-at-deadline, replay, ordering qua queue và slow outbound được kiểm tra runtime; không suy diễn PASS từ review source.

Network suites kiểm tra bốn session độc lập, INTRO/Continue, Spin/Star, mất ACK thật, reconnect và retry cùng UUID sau đóng câu/FINISHED, một Answer/score, nguồn PRIVATE, media/hint chưa mở bị từ chối, thay socket, nhiều game liên tiếp, Final/History và Room WAITING. Clues chứng minh Early Close không mở future hint. Song chứng minh native browser chặn audible autoplay rồi phát sau thao tác có user activation, HTTP 206 Range, reconnect 15s không cộng thêm thời gian. Song/Rebus tạo lỗi 404 bằng cách tạm di chuyển **đúng file upload thuộc fixture test**, luôn phục hồi trong finally; đã kiểm tra không còn backup fixture chưa khôi phục.

## Sửa test harness cần thiết

`test-game-client.py` là regression Quiz **v1**, nhưng màn tạo phòng hiện dùng stage plan **v2**. Fixture nay tạo phòng legacy bằng API thật (`quizId`, `questionDurationMs`) và assert configVersion=1, rồi tiếp tục UI open/join/Start/game như cũ. Không bỏ assertions v1 hoặc đổi expected score sang v2.

`test-song-client.py` và `test-image-word-client.py` nhận thêm `--video-directory` / `--image-directory` để kiểm tra media unavailable trong storage test thay cho giả định đường dẫn production mặc định. Giữ assertions, kiểm tra resolved path nằm trong thư mục quiz fixture và khôi phục file. Chạy `python -X utf8` trên Windows để output Unicode không làm suite dừng vì console encoding.

Ví dụ các lệnh chính đã chạy:

```powershell
python scripts/build-client.py
mvn -o -B --no-transfer-progress test '-DargLine=-Djava.io.tmpdir=D:/BTL_LTM/target' '-Dsurefire.reportNameSuffix=T05'
mvn -o -B --no-transfer-progress -Pmysql-smoke '-DargLine=-Djava.io.tmpdir=D:/BTL_LTM/target' failsafe:integration-test failsafe:verify
python -X utf8 scripts/test-seven-modes-client.py --origin http://192.168.1.234:8088 --port 9227 --smoke --task 5 --output-prefix t05-lan-seven
python -X utf8 scripts/test-game-client.py --origin http://127.0.0.1:8088 --port 9227 --smoke
python -X utf8 scripts/test-song-client.py --origin http://127.0.0.1:8088 --port 9227 --smoke --video-directory target/t05-media/videos
python -X utf8 scripts/test-image-word-client.py --origin http://127.0.0.1:8088 --port 9227 --smoke --image-directory target/t05-media/images
```

## Phạm vi xác minh và Visual QA

Đối chiếu `target/t05-before/manifest.json`: Java, resources ngoài static và pom không đổi từ đầu T05; `game/state.js` (permissions/receipt/countdown), core API/transport/router và App không đổi. Không sửa DB/migration/config production. Test ghi vào `quizz_task2_test`, storage `target/t05-media` hoặc schema tạm do integration suites hiện có tạo. Giữ nguyên các thay đổi T00–T04 và `1.txt`, không commit/push.

Chrome headless 155 dùng profile test riêng; plugin Browser vẫn gặp lỗi bootstrap đã khảo sát ở phiên trước, nên tiếp tục dùng helper CDP trong repo. LAN smoke thực sự dùng HTTP/WS origin `192.168.1.234:8088`, server test bind `0.0.0.0` và thêm đúng origin đó qua tham số launch test. **Các browser contexts vẫn chạy trên chính máy phát triển**; chưa xác minh nhiều máy LAN vật lý/firewall ngoài máy, browser engines khác, thiết bị touch hoặc screen reader thật. Không thay allowed-origins production. Server/Chrome test được dừng sau kiểm tra.

Đã mở xem ảnh QA; ảnh dưới đây là fixtures UI có kiểm soát, không thay bằng chứng network:

- [Quiz desktop](../assets/gameplay-2026-10-16/quiz-1440.png)
- [Sequential Clues 360px](../assets/gameplay-2026-10-16/clues-360.png)
- [Vua Tiếng Việt desktop](../assets/gameplay-2026-10-16/vietnamese_puzzle-1440.png)
- [Ordering desktop](../assets/gameplay-2026-10-16/ordering-1440.png)
- [Song unavailable 360px](../assets/gameplay-2026-10-16/song-unavailable-fixture-360.png)
- [Rebus unavailable 360px](../assets/gameplay-2026-10-16/image_word-unavailable-fixture-360.png)

[Quiz Decision thật](../assets/gameplay-2026-10-16/quiz-decision-real.png) được chụp từ network smoke v1 với tài nguyên Server thật. Không có policy gate mới được tự duyệt. T06 trở đi chưa triển khai trong task này.
