# T12 — Profile và bảng xếp hạng chính thức

Ngày 17/10/2026. **T12 đã hoàn thành theo A14/A15 được duyệt.** Không T13, không commit/push; chỉ dùng DB/storage test cho runtime verification.

## Policy được duyệt

Người dùng duyệt riêng A14/A15 trong T12; [phiếu signoff](T10_AWARD_POLICY_SIGNOFF.md) ghi approval record. AWARD_V1/eligibility/award timing/History giữ nguyên:

- Cúp DESC → officialWins/officialGames DESC → tổng correct/tổng participation DESC. Cả ba bằng nhau thì competition rank; userId chỉ ổn định thứ tự các tie khi phân trang.
- Zero officialGames trả winRate=null, UI “Chưa xếp hạng”; không nằm trong BXH. Zero participation trả accuracy=null.
- Public chỉ userId/displayName và stats đã commit; không username, mode breakdown hoặc recent History. Own profile theo account/session guard hiện có, có danh hiệu và các lần nhận thưởng của chính mình.
- Ẩn account đang Ban hoặc soft-delete khỏi public profile/BXH. TEMP hết hạn theo Server time hiện lại. Không sửa/xóa/recompute ledger hoặc History; UNBAN không hồi sinh session cũ.

“Public” ở đây là view an toàn để **người dùng khác đã đăng nhập** đọc, theo access model của app; không mở anonymous API mới. Anonymous GET các API T12 trả 401.

## Nguồn dữ liệu và snapshot

Không migration T12, không materialized projection, không event/worker/counter nguồn sự thật thứ hai. ProfileRepository đọc trực tiếp:

| Chỉ số | Nguồn committed |
|---|---|
| officialGames / officialWins | SUM delta trong game_award_participant của người eligible, official_game_delta=1 |
| Cúp | SUM CUP award_value trong achievement_award, join cùng participant và decision đủ điều kiện |
| Correct / participation / accuracy | SUM correct_count / participated_count của chính tập qualified/eligible trên; không lấy trung bình các phần trăm Game |
| Danh hiệu own / số lần / gần nhất | Ledger nhóm theo type+mode_key, count và MAX earned_at_ms |
| Receipt own phân trang | Ledger id/Game/type/mode/time, thứ tự earned_at_ms DESC/id DESC; không trả private_evidence |
| Tên hiển thị | app_user.display_name hiện hành; ID không đổi theo tên |

SQL scope bảo vệ độc lập: DECIDED + NORMAL V2/rules 2 + FINISHED/COMPLETED + policy snapshot không NULL. PENDING/NOT_QUALIFIED/V1/FRIENDLY/no-policy/cancel/interrupted không vào thống kê. Không join current Room membership/occupancy; Room close, sang Game Y hoặc logout không làm mất thành tích X. Kick/Ban trước decision được phản ánh bằng eligibility/deltas T11 đã đóng băng; Ban sau decision chỉ ảnh hưởng visibility, không thu hồi thành tích.

Read services dùng readOnly, REPEATABLE_READ, timeout 5 giây. Một response profile (stats+badges), receipts (total+items) hoặc leaderboard (total+page+own) cùng committed snapshot. Visibility có một Server-time sample cho toàn response; concurrent BAN/award được nhìn theo snapshot đó, không trộn total cũ với rows mới.

Leaderboard so sánh tỷ lệ bằng **tích chéo DECIMAL của các tổng nguyên**, không dùng phần trăm đã làm tròn/epsilon. Rank = 1 + số người public đứng trước theo ba tiêu chí; userId không tham gia công thức rank. Response `own` chứa vị trí người đọc kể cả ngoài page, hoặc null nếu chưa xếp hạng/không public-visible.

Truy vấn rank exact hiện tính so sánh các qualified public users theo yêu cầu GET; không cache. Đây là lựa chọn tối thiểu cho app single Server hiện tại. Chưa benchmark ở quy mô lớn; có thể cần tối ưu query/projection nhất quán khi số người được xếp hạng tăng nhiều. Không tuyên bố tải lớn đã PASS. Pagination giới hạn size 1–50, page 0–1.000.000; query timeout trả lỗi an toàn.

## Contract inventory

Tất cả GET: authenticated account/session guard, Cache-Control no-store; mutation không có endpoint và bị deny. Dùng DTO allowlist, không serialize UserAccount/GameSession/Answer.

| Endpoint | Response / quyền |
|---|---|
| `/api/profiles/me` | userId, displayName, stats, badges(type/mode/count/lastEarnedAtMs); chủ tài khoản đang active |
| `/api/profiles/me/awards?page=0&size=20` | items(awardId/gameSessionId/type/modeKey/earnedAtMs), page/size/totalElements; chỉ chính mình |
| `/api/profiles/{userId}` | userId, displayName, stats; 404 giống nhau cho không tồn tại/Ban/soft-delete; không badges/mode/History |
| `/api/leaderboard?page=0&size=20` | items(rank/userId/displayName/stats), page/size/totalElements, own cùng shape hoặc null, evaluatedAtMs |

Stats fields: officialGames, officialWins, cups, correct, participated, winRate, accuracy. Tỷ lệ là fraction 0–1 hoặc null; UI chỉ format. Invalid page/size/type trả 400; database/transaction failure trả 503/SERVICE_UNAVAILABLE không chứa SQL. Không API chỉnh cúp/rank, không public receipt endpoint cho người khác. Link History từ own receipt tiếp tục chịu quyền GameMember và policy công bố của API History cũ.

## Structure và lifecycle

| Owner/file | Trách nhiệm |
|---|---|
| achievements/dto/PlayerProfileView | Own/public allowlist và stats/receipt DTO |
| achievements/repository/ProfileRepository | Aggregate cùng source T11, không nhân SUM bởi join badge |
| achievements/service/ProfileReadService, controller/ProfileController | Own/public GET, snapshot và visibility |
| leaderboard/dto, repository, service, controller | Rank exact, pagination/own rank và GET |
| SecurityConfiguration, AwardExceptionHandler | Allowlist GET cụ thể, error an toàn; guards T07–T11 không đổi |
| js/features/profile/page.js | Summary/danh hiệu, own/public view và request lifetime |
| js/features/leaderboard/page.js | Server rank, bảng cuộn, own highlight, pagination |
| js/shared/achievement-ui.js | Stats/tabs/paging dùng bởi cả hai feature, không business computation |
| styles/features/profile.css | Tokens/layout cho hai trang, cuộn bảng trong container ở mobile |
| app.js, results/awards.js | Routes/sidebar/ceremony links, dispose và refresh sau reconnect qua transport duy nhất |
| ProfileLeaderboardIT, test-profile-client.py | MySQL/REST snapshots và browser thật |

Không di chuyển code cũ, không folder rỗng, không framework/dependency/build step mới. Không sửa ScoreEngine/RankingEngine/CloseGate, award finalizer, Game queue, migration hoặc gameplay contracts.

Mỗi page có AbortController riêng; request coalesce khi Cập nhật/reconnect, dispose abort khi đổi route/logout. Không poll/timer hoặc WS listener mới. Mọi text người dùng qua DOM text helper, không innerHTML. Own/public stats dùng chung primitives, tab/link/nút keyboard, focus theo styles nền; CSS không có animation. Ở mobile bảng có vùng cuộn riêng, không ép tràn toàn trang.

## Verification

| Evidence target/ | Kết quả |
|---|---|
| t12-compile.log | BUILD SUCCESS, Java 21.0.9 / Maven 3.9.12, offline cache |
| t12-profile-it.log | Lượt đầu 7 tests: 6 PASS, 1 lỗi SQL fixture Kick sai cột; không ghi lượt này là xanh |
| t12-profile-final-it.log | Sau đổi fixture gọi AdminKickTransactions thật và thêm order/exclusion: 9/9 PASS, 0 failure/error/skipped |
| t12-full.log, suffix T12-final | Full **345 unit + 232 integration PASS**, 0 failure/error/skipped, BUILD SUCCESS; bao gồm cả 9 test T12 và 14 AwardDecisionIT |
| t12-package-final.log | Package UI cuối thành công; 62 static files trong t12-runtime.jar khớp byte source |
| t12-static-resources.json | 61 JS/CSS/HTML GET qua LAN đều 200 và byte khớp source; README không phải tài nguyên runtime |
| t12-profile-browser-first.log, t12-profile-client-first.json | Lượt browser đầu dừng ở SyntaxError của selector được escape sai trong script; các bước trước đã PASS, không ghi cả lượt là xanh |
| t12-profile-browser.log, t12-profile-client-tests.json | Jar/UI cuối: **6 smoke groups + 4 component checks PASS**, Chrome 155 qua LAN origin, Server scheduler mặc định |
| t12-seven-browser.log, t12-lan-seven-client-tests.json | **93 checks + 7 smoke groups PASS**, đủ 7 mode/INTRO/Decision/reconnect/ACK mất/History/cancel với Server và media thật |
| t12-auth-browser.log, t12-auth-lobby-client-tests.json | **15 checks + 22 smoke groups PASS**, Login/Register/Lobby/Room/session/CSRF/logout/socket replacement |
| t12-lifecycle-browser.log, t12-lifecycle-client-tests.json | **19 lifecycle component checks PASS** |

9 IT mới: zero denominator/REST allowlist/anonymous/mutation/invalid paging; pending/rollback/replay; stable ties qua pagination và own ngoài page; Ban/deleted/expired TEMP visibility; concurrent award commit không đổi snapshot đang đọc; Room close/logout/new Game giữ data; restriction không góp stats/DTO không secrets; ba tầng comparator; short/interrupted/V1 không official. T11 regression tiếp tục kiểm tra FRIENDLY metadata exclusion, award transaction và ban races; chưa có gameplay Friendly/T15 để chạy end-to-end.

```powershell
mvn -o -B --no-transfer-progress -Pmysql-smoke verify '-DargLine=-Djava.io.tmpdir=D:/BTL_LTM/target -Dquiz.awards.initial-delay-ms=86400000' '-Dsurefire.reportNameSuffix=T12-final'
python -X utf8 scripts/build-client.py
python -X utf8 scripts/test-profile-client.py --origin http://192.168.1.234:8088 --port 9227
```

Initial-delay là isolation fixture cho mọi cached Spring context như T11; browser dùng scheduler mặc định, không delay này. Không khởi động Server dùng cùng DB lúc Maven integration đang chạy.

Browser tạo account/Room/private RIDDLE pack và chơi 10 câu qua HTTP/WS thật để nhận receipt T11. Own stats/badges và receipts có link đúng History; public DTO chỉ đúng ba field userId/displayName/stats. Tên `<b>Profile A</b>` là text, không DOM HTML. BXH hiển thị own rank kể cả ngoài page, highlight đúng userId khi vào page có mình; public profile không hiện mode/badge receipts/History.

Game fixture T12 cuối là **4960** trong DB test; không là dữ liệu/award production. Không cộng số smoke chạy lại trước sửa layout vào số nhóm PASS cuối.

Cùng account tiếp tục START một Game khác, kiểm tra stats không đổi khi Game mới ACTIVE; cancel rồi đóng Room; logout/login vẫn cùng profile. ADMIN BAN thật ẩn target khỏi public profile (404)/BXH và own API mất quyền; UNBAN hiện lại public, yêu cầu login mới, stats và ledger IDs không đổi. Browser contexts trên cùng Chrome/máy, không giả nhiều thiết bị. Chrome test riêng dùng headless/no-sandbox do hạn chế môi trường như T11; không thay setting Chrome cá nhân.

Component fixtures được tách rõ khỏi dữ liệu smoke: chỉ kiểm tra loading/error và abort của mỗi page với API stub, không thay response thành tích trong smoke. Kiểm tra trực quan đã phát hiện CSS nền áp layout nav/dl; đã sửa selector tại feature stylesheet, repack và chạy lại đầy đủ smoke trên UI cuối.

Ảnh đã xem: [own profile desktop](../assets/profile-2026-10-17/own-1440.png), [leaderboard mobile](../assets/profile-2026-10-17/leaderboard-360.png). Không tràn ngang toàn trang 360px; bảng có vùng cuộn riêng. Ảnh [public mobile](../assets/profile-2026-10-17/public-360.png), [leaderboard desktop](../assets/profile-2026-10-17/leaderboard-1440.png) và [empty profile](../assets/profile-2026-10-17/empty-1440.png) do cùng smoke ghi lại.

Inventory theo hash mốc trước T12: **20 file mới / 9 file sửa / 0 xóa / 0 di chuyển**, gồm 5 ảnh evidence mới. Chi tiết `target/t12-change-inventory.json`, baseline `target/t12-before/manifest.json`; Git status thực tế lưu riêng `target/t12-git-status.txt` để không gộp thay đổi T00–T11 thành T12. Toàn bộ V1–V12, configs/pom, engines/QuestionCloseGate, GameTransactions, AwardTransactions/AwardReconciler và `1.txt` giữ nguyên hash (24 file). `git diff --check` trả 0.

Ngoài 9 file T12 sửa, workspace có `AwardReadService.java` được format lại lúc 15:48:22, sau khi package; không phải chỉnh sửa T12 của agent và được giữ nguyên. Source hiện tại được compile riêng bằng javac 21 cùng dependencies jar đã test: `javap -c -p` của cả 3 class khớp chính xác instructions/signatures trong jar đã chạy. `target/t12-format-bytecode-check.json` lưu PASS; chỉ debug line metadata không so sánh. Inventory phân riêng thay đổi này, không nhận là refactor T12 hoặc hoàn tác thay đổi của người dùng.

Server test và Chrome headless riêng được dừng sau kiểm chứng, cổng 8088/9227 đóng. Không dừng MySQL/IDE/Chrome cá nhân. Không cần migration/deploy trong task này.

## Phạm vi còn lại

- T13 và Friendly/T15/King/T17 không triển khai. Không hiện tab Friendly/danh hiệu chưa có dữ liệu thật.
- Public mode details/recent History không được duyệt, không xuất. Own History dùng feature có sẵn; không mở private packs qua profile.
- LAN nhiều thiết bị vật lý và tải lớn chưa xác minh; nhiều browser context trên một máy qua LAN origin không tương đương nhiều thiết bị.
- Giữ thay đổi T00–T11/1.txt; không commit/push hoặc đọc/ghi DB production.
