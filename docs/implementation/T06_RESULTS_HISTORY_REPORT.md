# T06 — Result/History theo API hiện có

Hoàn tất ngày 2026-10-16 trên T01–T05. Chỉ thay frontend, tests và tài liệu; không sửa Java, migration, API/event schema, quyền Server, ranking/scoring hoặc cấu hình production.

## Kết quả

- Result có nhóm hạng 1–3 từ rank Server, hiển thị **tất cả** đồng hạng trong nhóm; không cắt ba người đầu và không xếp hạng lại theo score ở client. Không dùng hình cúp/medal để ngụ ý đã nhận thưởng.
- Stats cá nhân dùng member rank/score/time đã lưu. V1 tiếp tục dùng `totalAnswerTimeMs`, v2 dùng `totalCorrectAnswerTimeMs`; Spectator có thông báo vai trò thay vì số liệu giả.
- COMPLETED/ONE_SURVIVOR/ALL_ELIMINATED giữ lý do thật và Winner IDs Server. CANCELLED/SERVER_INTERRUPTED có lý do thật, không podium/Winner, kể cả fixture cố gửi flag Winner mâu thuẫn. Bảng điểm lúc kết thúc vẫn là dữ liệu đã lưu, không bị đổi thành trận thắng.
- History list có cards, lý do kết thúc/schema/time thật và pagination. Không hiển thị điểm/rank cá nhân ở list vì API list không cung cấp các trường đó.
- History detail có own highlight, kết quả từng câu, Answer đã nhận nhưng chưa chấm và media/hint đã lưu. Chỉ công bố đáp án đúng khi `scoredAtMs` tồn tại; không biến ACCEPTED_UNSCORED thành WRONG/NO_ANSWER hoặc tự cộng thời gian vào ranking.
- Breakdown v2 tổng hợp **các result câu có thật trong response**, theo `question.mode`, `answerStatus`, `result.scoreDelta`. UI ghi rõ đây là điểm từ câu đã lưu, không thay thế điểm cuối/rank Server; không tính lại ScoreEngine. Nếu không có mode/results/PLAYER tương ứng thì không tạo số liệu. V1 không bị diễn giải lại theo v2.
- GET Room hiện hành trước khi dựng hành động quay lại/chơi tiếp. WAITING Host có “Chơi tiếp cùng phòng”, member có “Quay lại phòng”; cả hai chỉ dẫn tới cùng Room để chuẩn bị. ACTIVE/DRAFT/CLOSED có nhãn tương ứng; không có quyền/phòng đã xóa thì không dựng link giả hoặc tạo phòng thay thế. Có nút đọc lại status; route Room tiếp tục GET lại khi vào. Không polling hoặc đăng ký socket riêng.

## Mapping source

| File | Trách nhiệm |
| --- | --- |
| `static/js/features/results/view.js` | Rank/score/time formatting, standings, top ranks/co-winners, own stats, final reason; giữ helpers result/media hiện có |
| `static/js/features/results/history.js` | GET list/detail, stale route/user guard, compose các view |
| `static/js/features/results/timeline.js` — mới | History question/Answer/media và breakdown từ result records đã lưu |
| `static/js/features/results/navigation.js` — mới | Read-only Room lookup/retry; trạng thái và hành động navigation, dispose/stale callback guard |
| `static/js/features/results/awards.js` — mới | Seam UI thuần trình bày cho ba trạng thái T11; chưa nối vào production Result/History |
| `static/js/features/game/page.js` | Truyền selfId vào final view, sở hữu/coalesce một Room lookup và dispose cùng game controller; không đổi command/gameplay flow |
| `static/styles/features/results.css` — mới | Styles Result/History/timeline/own stats/breakdown, responsive |
| `static/styles/features/game.css`, `static/styles.css` | Chuyển rules kết quả cũ về đúng owner và import stylesheet; không thêm overrides để che rules cũ |
| `static/README.md` | Ownership/contracts/điều kiện chạy restart smoke |
| `scripts/test-results-ui.py`, `scripts/test-results-client.py` — mới | Fixtures read-only/tie/V1/navigation và smoke bốn session/restart thật |

Không di chuyển file hoặc tạo folder source mới. Build hiện có 34 JS modules / 52 static source files. Không thêm framework/bundler/build step/CDN/asset ngoài. Dữ liệu người dùng/câu hỏi dùng DOM text nodes; fixture tên dạng HTML được escape, không thực thi.

## Contract inventory và Award seam

| Contract hiện có | Cách sử dụng |
| --- | --- |
| GET `/api/games/history?page=N&size=12` | List items gồm game/room IDs, title, start/end time, reason, hasOfficialWinner, schemaVersion; không tự thêm stats/awards |
| GET `/api/games/history/{id}` | `finalSnapshot`, started/finished time và opened questions/answers/results; giữ private membership guard của Server |
| GET `/api/games/{id}/snapshot` | Luồng Game đã có, dùng final view sau cửa sổ trình bày Server hiện có |
| GET `/api/rooms/{id}` | Room status/Host hiện hành; read-only, không gọi START/create/open khi mở kết quả |
| Award API | **Chưa có**. Không gọi endpoint giả, không suy ra eligibility/decision/ledger từ việc mở trang |

`awards.js` định nghĩa và render riêng `AWARDS_PENDING`, `AWARDS_NOT_QUALIFIED`, `AWARDS_DECIDED` khi caller cung cấp state hợp lệ. Không state thì trả null. T06 **không import/call renderer này trong Result/History**, không dựng trạng thái PENDING giả, không hiển thị “đã nhận cúp”, không chốt policy qualification hoặc quyết định award. T11 cần contract đã được duyệt trước khi nối state/decision thật vào seam này.

`GameHistoryService`/Controller hiện vẫn chỉ đọc committed history. T06 không thêm mutation, finalizer hoặc side effect khi GET Result/History. Các request tạo quiz/room, Answer, START/cancel trong smoke chỉ dùng dựng fixture và chơi các trận test có chủ đích, được tách khỏi audit read-only lúc mở Result/History.

## Kết quả thực chạy

| Kiểm tra | Kết quả và bằng chứng |
| --- | --- |
| Static import build | PASS, 34 modules / 52 files; `python scripts/build-client.py` |
| Maven unit/web-only | 315 PASS, 0 failure/error/skip; bao gồm SystemWebTest kiểm tra HTTP static serving; `target/t06-unit.log` |
| Maven package | BUILD SUCCESS; `target/t06-package.log` |
| History/Cancel/Presentation/v2 lifecycle IT | 32 PASS: GameHistoryCancelIT 13, GamePresentationIT 6, MultimodeLifecycleIT 13; `target/t06-history-it.log` |
| Seven-mode regression qua LAN origin | 93 assertions / 7 network smoke PASS; `target/t06-lan-seven-client-tests.json` |
| Quiz v1 regression | 39 assertions / 10 network smoke PASS; `target/t06-quiz-v1-client-tests.json` |
| T06 DTO/readonly fixtures | 21 checks / 20 layouts PASS; `target/t06-results-ui-tests.json` |
| T06 real Result/History + restart | 7 nhóm PASS; `target/t06-results-network-tests.json` |
| Design system regression | 14 nhóm / 40 layouts PASS; `target/t06-design-system-tests.json` |
| Lifecycle fixtures | 19 PASS; `target/t06-lifecycle-client-tests.json` |
| LAN static HTTP | 51 JS/CSS/HTML HTTP 200 và body khớp source; `target/t06-lan-assets.json` |
| Scope/encoding/whitespace | Protected source hashes, UTF-8 và `git diff --check` PASS |

Không cộng các assertions browser dùng chung thành số test độc lập. Không chạy lại toàn bộ 170 MySQL IT ở T06; 170 PASS đã được ghi riêng trong báo cáo T05. T06 chạy 32 IT liên quan trực tiếp và browser regressions nêu trên.

Fixtures T06 kiểm tra bốn người cùng rank 1 đều được giữ, cancelled/interrupted không Winner/podium, V1/V2 time fields, GET-only/no award, dữ liệu unscored không lộ đáp án, status WAITING/ACTIVE/DRAFT/CLOSED/forbidden, stale/disposed response, empty History, một Room GET qua nhiều final renders và release game timer. Responsive có 20 tổ hợp COMPLETED/CANCELLED/SERVER_INTERRUPTED/V1/list × 360/390/768/1440px; không tràn ngang trang.

Smoke T06 thật kiểm tra:

1. COMPLETED khớp rank/score Server ở ba Player và vai trò Spectator.
2. Mở History/chơi tiếp chỉ GET và navigation về **cùng Room**, không phát START/create/award.
3. CANCELLED không Winner/podium; chưa mở câu thì không bịa kết quả câu.
4. History cũ đọc Room đang ACTIVE, không quảng bá chơi tiếp.
5. Dừng/chạy lại **đúng server test do task tạo** khi Game còn ACTIVE, đăng nhập lại bốn session: History lưu SERVER_INTERRUPTED thật, không false Winner.
6. Room CLOSED bỏ hành động chơi tiếp, History vẫn đọc được.
7. Xóa Room test rồi mở History: không link phòng giả, không tạo replacement; questions/members history giữ nguyên.

Thử restart dùng handshake `target/t06-restart-result_82b7ac78.request.json` và signal trong cùng `target`; không lưu/in mật khẩu fixture. Chỉ restart process server test port 8088, không tác động MySQL hoặc server khác. Game IDs của smoke thành công: 3086/3087/3088, Room 3502. Không coi snapshot/fixtures là bằng chứng runtime gián đoạn; mục 5 dùng restart và GET API thật.

Browser Chrome headless 155/profile riêng, DB `quizz_task2_test`, storage test `target/t05-media` dùng lại từ T05 để các suite media có cùng cấu hình test; integration suites còn dùng schema tạm đã có. Không đọc/ghi DB hoặc storage production. Plugin Browser vẫn gặp lỗi bootstrap đã khảo sát trước đó, nên tiếp tục dùng helper CDP trong repo.

## Visual QA, giới hạn và trạng thái

Đã mở xem các ảnh fixtures sau; tên HTML dài là fixture kiểm tra escape/wrap, không phải dữ liệu production:

- [Đồng hạng desktop](../assets/results-2026-10-16/completed-1440.png)
- [CANCELLED 360px](../assets/results-2026-10-16/cancelled-360.png)
- [SERVER_INTERRUPTED 390px](../assets/results-2026-10-16/interrupted-390.png)
- [History v1 mobile](../assets/results-2026-10-16/legacy-360.png)
- [History list](../assets/results-2026-10-16/list-1440.png)

[Final Quiz v1 thật](../assets/results-2026-10-16/quiz-v1-final-real.png) và [History mobile thật](../assets/results-2026-10-16/quiz-v1-history-real-mobile.png) lấy từ network regression, không phải DTO fixtures.

LAN dùng HTTP/WS origin `192.168.1.234:8088` nhưng các browser contexts vẫn trên cùng máy; chưa xác minh nhiều thiết bị LAN vật lý, browser engines khác hoặc screen reader/touch thật. Room status là kết quả GET gần nhất; có nút cập nhật và route Room đọc lại, không thêm realtime/polling cho trang History. Server và Chrome test được dừng sau kiểm tra.

Đối chiếu `target/t06-before/manifest.json` xác nhận toàn bộ Java/resources ngoài static/pom không đổi; core API/transport/router, Game state/engines và các mode renderers T05 giữ nguyên. Giữ các thay đổi T00–T05 và `1.txt`, không commit/push. Không có policy gate mới được tự duyệt; T07/T11 chưa triển khai trong T06.
