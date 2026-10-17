# Checklist triển khai MultiGame V3.4

Ngày: 16/10/2026. Tham chiếu [kế hoạch T00](MULTIGAME_IMPLEMENTATION_PLAN.md). `[x]` dưới đây ghi bằng chứng audit T00; trạng thái triển khai T01 trở đi được cập nhật riêng trong bảng task order và báo cáo từng task.

## T00 — đã khảo sát

- [x] Git ban đầu sạch, HEAD/branch/toolchain/profiles ghi theo kết quả thật.
- [x] Đọc Java/static/migrations/tests/build; không templates/server-rendered pages.
- [x] Mapping frontend 13 existing module moves dự kiến; Java 0 moves; không tạo source folder rỗng.
- [x] Inventory REST/WS/ACK/events/static/media và ownership thật.
- [x] Review 4 blocker ADMIN §18.14, AC-40/41/42, test31 occupancy setup.
- [x] Phân biệt ĐÃ CHỐT/KHUYẾN NGHỊ/safety invariant/SAU MVP và policy gates.
- [x] Dependency map T01–T18, migrations/API/events/tests dự kiến, task order.
- [x] Baseline Java: 315 PASS / 0 failure / 0 error / 0 skipped; web-only không DataSource.
- [x] IT/MySQL/browser/LAN ghi NOT RUN; không lấy review tĩnh làm runtime PASS.
- [x] Chỉ thêm hai file kế hoạch; không sửa code/production config/migration/DB, không commit/push.

## Approval gates — trạng thái theo từng task

- [x] P-KICK: lệnh T08 duyệt non-Host PLAYER, release occupancy/detach và frozen question/phase obligations; đã kiểm chứng trên DB test, xem [báo cáo T08](T08_KICK_REPORT.md).
- [x] P-ADMIN-OPS phần T07: lệnh T07 cho phép role/provision idempotent/login chung/read-only; đã kiểm chứng trên DB test, hướng dẫn nhập secret ngoài repo.
- [x] P-BAN/P-HOST/P-ADMIN-OPS phần T09: lệnh T09 duyệt ban/version/cutoff/intents và orphan close hẹp; đã triển khai, kiểm chứng DB test và browser/restart thật, xem [báo cáo T09](T09_ACCOUNT_SANCTIONS_REPORT.md). Không mở scope Award/Friendly.
- [x] P-AWARD-BASE/TIE/ADMIN-AWARD: người dùng duyệt [T10 A01–A10](T10_AWARD_POLICY_SIGNOFF.md) bằng lệnh signoff riêng, policy AWARD_V1; không suy approval từ T08/T09.
- [x] P-FAST/P-PERFECT: A11/A12 bật theo phiếu, A13 duyệt; FAST ≥5 đúng/mode và ≥60%, PERFECT 100% participation và ≥10 câu.
- [x] P-LEADERBOARD: A14–A15 được duyệt riêng tại T12: cups→win rate→aggregate accuracy, đồng hạng/ID paging; public allowlist, ẩn Ban/soft-delete, giữ ledger/history và own account guard.
- [x] P-AWARD-ACTIVATION/TIME: A16/A17 đã duyệt, new-games-only, policy AWARD_V1, Server evaluation sau sorted User locks và kiểm tra lại biên TEMP trước commit.
- [x] P-MULTITALENT/P-COMEBACK: T13-P01–P04 được duyệt riêng; ≥3 mode/≥2 correct/≥50% participation cho mọi eligible đạt; Comeback unique last→first cùng E, evidence gate; AWARD_V2 new-games-only.
- **CANCELLED / OUT_OF_SCOPE:** P-FRIENDLY/P-RESERVATION/P-FORFEIT/P-KING theo quyết định người dùng; không xin signoff T15-P01–P08, không triển khai T15–T17.
- [ ] P-ROOM-SUMMARY/P-PROFILE-EXTRA: chọn hoặc hoãn sau MVP, không tự triển khai.

## Task order — chỉ thực hiện khi được giao riêng

| Task | Trạng thái | Điều kiện trước khi đóng |
|---|---|---|
| T00 | Hoàn tất audit/plan | Baseline và giới hạn bằng chứng rõ |
| T01 | Hoàn tất — [báo cáo](T01_FRONTEND_REFACTOR_REPORT.md) | 26 JS modules, CSS giữ cascade; 315 unit + 170 IT PASS; browser 93 assertions/7 smoke, auth/lobby 15/22, lifecycle 19; chưa LAN |
| T02 | Hoàn tất — [báo cáo](T02_DESIGN_SYSTEM_REPORT.md) | Dark tokens/layout/primitives; 315 Java PASS, UI 14 nhóm/40 layouts PASS, browser 7-mode + auth/lobby/lifecycle/touch PASS; chưa LAN/thiết bị thật |
| T03 | Hoàn tất — [báo cáo](T03_AUTH_LOBBY_REPORT.md) | Login/register/lobby API thật; 315 Java + 7 AuthNetworkIT PASS, browser smoke/UI/lifecycle PASS; static HTTP qua LAN interface PASS, chưa thiết bị LAN khác |
| T04 | Hoàn tất — [báo cáo](T04_WAITING_ROOM_REPORT.md) | Waiting/editor/roster; 4 phiên API/WS thật PASS, author-pack/Host guards/reconnect/START replay/cancel; 315 unit + 26 IT và 7-mode regression PASS; chưa presence/ready protocol riêng cho Waiting |
| T05 | Hoàn tất — [báo cáo](T05_GAMEPLAY_UX_REPORT.md) | Shell/renderers/media 7 mode; 315 unit + 170 MySQL IT PASS; LAN-origin 93 assertions/7 smoke và các suite v1/media/clues/arrangement PASS; chưa nhiều máy LAN vật lý |
| T06 | Hoàn tất — [báo cáo](T06_RESULTS_HISTORY_REPORT.md) | Result/History/rank ties/own stats + Room GET; 315 unit/32 IT, LAN seven-mode + Quiz v1 + restart SERVER_INTERRUPTED thật PASS; Award seam chưa nối, không fake cups |
| T07 | Hoàn tất — [báo cáo](T07_ADMIN_ROLE_REPORT.md) | V8 role/bootstrap an toàn/login chung/read-only ADMIN; 320 unit + 176 MySQL IT PASS; browser ADMIN 10 checks, auth/lobby 15/22, lifecycle 19, LAN-origin seven-mode 93/7 PASS; chưa áp dụng DB vận hành |
| T08 | Hoàn tất phạm vi Kick — [báo cáo](T08_KICK_REPORT.md) | V9, atomic restriction/release/generation detach, OPEN participation, scoped send-start fence; 327 unit + 189 MySQL IT PASS, AFK 14/14 chạy lại PASS; browser Kick/restart 10 nhóm và hồi quy PASS. Test 37 Award vẫn PENDING T11 theo gate T00 |
| T09 | Hoàn tất phạm vi hiện có — [báo cáo](T09_ACCOUNT_SANCTIONS_REPORT.md) | V10/V11, ban/version/audit, durable intent/session epoch/reconcile/orphan close; 335 unit + 208 IT phân biệt PASS qua full và focused rerun; browser ban/restart 10 nhóm, UI cuối 9 nhóm, 7-mode 93/7, auth/lobby 15/22, lifecycle 19 PASS. AC-42 membership/occupancy/session đã kiểm chứng; reservation/mixed Friendly OUT_OF_SCOPE, chưa áp dụng DB vận hành |
| T10 | Hoàn tất thiết kế và signoff AWARD_V1 — [thiết kế](T10_AWARD_POLICY_AND_TRANSACTION_DESIGN.md), [phiếu duyệt](T10_AWARD_POLICY_SIGNOFF.md) | A01–A13,A16–A17 đã duyệt; A14/A15 hoãn T12; T13/T17 ngoài T11 |
| T11 | Hoàn tất AWARD_V1 — [báo cáo](T11_AWARDS_REPORT.md) | V12, immutable decision/ledger, User fence, GET/policy và ceremony; 345 unit + 223 IT phân biệt PASS qua full/focused, browser award/restart và 7 mode PASS; không T12/T13/T17, không DB vận hành/commit/push |
| T12 | Hoàn tất — [báo cáo](T12_PROFILE_LEADERBOARD_REPORT.md) | A14/A15 đã duyệt; GET committed profile/receipts/BXH, privacy/paging/ties; full 345 unit + 232 IT PASS, browser profile 6+4, 7 mode 93+7, auth 15+22, lifecycle 19 PASS; không migration/projection, không T13/commit/push |
| T13 | Hoàn tất — [signoff](T13_ADVANCED_ACHIEVEMENTS_SIGNOFF.md), [bằng chứng](T13_ADVANCED_ACHIEVEMENTS_REPORT.md) | Hai badge/P03/P04 đã duyệt; stage proof 2/2 trước Comeback; full 354 unit + 242 IT PASS; browser T13 5+1, 7 mode 93+7, auth 15+22, lifecycle 19 PASS; genuine crash/restart PASS; AWARD_V2 new-games-only, không T14/commit/push |
| T14 | Hoàn tất | Host cấu hình/Player quay lại phòng theo state; lookup hủy khi rời History. 3 Game cùng Room, đổi pack/roster, reconnect, START trùng, History/Awards bền: PASS. Full 354 unit + 244 IT; browser same-room 8, UI 25 + 20 layouts, 7 mode 93+7, auth 15+22, lifecycle 19 PASS. [Bằng chứng](../../target/t14-verification.json); giữ backend lifecycle/API/DB/policy, không room summary/T15/commit/push |
| T15 | CANCELLED / OUT_OF_SCOPE | Người dùng loại Friendly 1v1; không invitation/matchmaking/reservation/FORFEIT; không signoff, không tính PASS/COMPLETED |
| T16 | CANCELLED / OUT_OF_SCOPE | Không UI Friendly/invitation/versus/rematch; không tính PASS/COMPLETED |
| T17 | CANCELLED / OUT_OF_SCOPE | Không King/counter/profile chỉ phục vụ Friendly; giữ danh hiệu T11/T13; không tính PASS/COMPLETED |
| T18 | Hoàn tất kiểm chứng trong môi trường hiện có | T00–T14 đủ bằng chứng; T15–T17 CANCELLED/OUT_OF_SCOPE. Full 354 unit + 244 IT PASS (0 failure/error/skipped); browser 93 nhóm HTTP/WS/CLI + 206 JS/UI/lifecycle, 88 layouts PASS; crash/restart award, ADMIN/CSRF/cross-user REST/WS, Kick/Ban/AC-40/41/42 membership/occupancy và 3 Game cùng Room đã kiểm chứng. [Bằng chứng](../../target/t18-verification.json). Chrome nhiều phiên trên một máy qua LAN origin; LAN 2–3 thiết bị thật/Firefox/Safari chưa xác minh; không áp dụng DB production/commit/push |

## Gate kỹ thuật bắt buộc trước demo ADMIN

- [x] B1 Kick/Ban: Kick atomic TX; Ban commit durable captured intents rồi reconcile/retry, CAS release X+generation detach+completion trong owner TX; rollback/restart/replay và old finish X không xóa Y PASS. Không giữ User lock để chờ Room/Game worker.
- [x] B2 Kick/Ban: OPEN participation bền; AFK ngoài waitSet vẫn NO_ANSWER; restricted trước OPEN không outcome; câu đã OPEN vẫn score accepted/NO_ANSWER đúng một lần; AccountBanIT cả hai commit order và pending ejection tới sau SCORING PASS.
- [x] B3 runtime T11: separate commits, eligible-only CORRECT, Room→Game→decision→sorted Users; immutable terminal receipt/ledger, rollback/retry/concurrent BAN và TEMP expiry PASS. Browser Kick rank 1 và crash sau result commit/restart recovery PASS; bằng chứng tại báo cáo T11.
- [x] B4: Host PLAYER/SPECTATOR bị ban, ACTIVE Game tiếp tục; close orphan chỉ WAITING/Host thực sự bị ban hoặc không còn quyền; audit/replay, không logout/xóa data user khác. IT và browser thật PASS.
- [x] AC-40 cho Kick/AFK: scoringSet độc lập gate, frozen current phase, next phase loại restricted, no auto-ready. SQL assertion participation/captured marker và outcomes PASS trong 14 GameAfkV2IT; boundary Kick PASS trong GameKickIT.
- [x] AC-41: BAN/OPEN và BAN/Answer **cả hai commit order**, pending intent trước ejection, lock-read order không stale; barrier cả implicit FK lock, rollback và SCORING trước ejection PASS trong AccountBanIT.
- [ ] AC-42: same-row g1→g2, conditional no-op cleanup; r1→r2 hai slot không bị đụng, restart replay không retarget.
- [x] AC-42 phần membership/occupancy/session T08/T09: release Y trước rejoin cùng row g2 khi Room WAITING; detach g1 là no-op, late finish/replay X giữ Y; intent X còn sau UNBAN/restart; epoch mới không bị old effect revoke. Reservation/mixed dành riêng Friendly: OUT_OF_SCOPE, không tính PASS.
- [x] Test31 phần T09: release/reconcile occupancy X và detach g1 hợp lệ **trước** START Y; release Y trước JOIN g2; giữ X chưa release thì USER_ACTIVE_GAME là expected. Reservation r1/r2 dành riêng Friendly: OUT_OF_SCOPE.
- [x] Fixture rejoin cùng R chỉ khi R WAITING hợp lệ; DB assertions và browser/restart thật giữ cùng row g2; không bypass room lifecycle.
- [x] T09 không dựng friendly sớm; giữ nguyên typed extension port + nullable reservation identity trong intent. Reservation r2/hai slot/mixed tests Friendly: OUT_OF_SCOPE; không xóa migration/code dùng chung, không báo PASS giả.
- [x] Session cutoff bền: mọi pre-ban session mất quyền, login mới qua User fence; old effect không revoke S2. Nhiều HTTP sessions/current WS, delayed close, UNBAN/expiry/restart đã kiểm chứng.
- [x] Outbound cho Kick: recipient + scope + send-start fence; queued X drop, Y FIFO giữ; slow socket không chặn Game worker; permit CAS trước fence được phân biệt bằng unit/IT và mô tả trong báo cáo.
- [x] Guard Kick/Ban tại account API, WS handshake/command, OPEN, media, DB snapshot fallback, RECONNECT ACK/replay, recipient và writer send-start; ban không đợi slow socket. Full baseline IT + AccountBanIT và browser thật PASS.
- [x] Restart giữ SERVER_INTERRUPTED policy, không tự resume; pending ban reconcile đúng X/g1, giữ g2 và ban INDEFINITE. T11 bổ sung award PENDING sau result commit → crash/restart recovery PASS, không cấp lặp ledger cũ.

## Gate T10 — thiết kế và signoff đã duyệt

- [x] Đọc V3.4 §5–7/§10–11/§18.6/18.10 và đối chiếu source result, ranking, History, T08/T09 fences/queues.
- [x] Audit DB test SELECT-only: V1–V11, 21 bảng, outcome/participation/timing/intent evidence thật; không khởi động app, không DML/DDL.
- [x] Mapping V1/V2/endReason và finalRank khác awardRank; legacy policy NULL không tự backfill.
- [x] Schema/API/transaction/unique/reconcile design cụ thể, 3 bảng + Game metadata, migration dự kiến sau V11; không tạo file Flyway.
- [x] Test matrix/chỉ rõ NOT RUN, baseline kế thừa T09, B3 runtime vẫn mở; không sửa gameplay.
- [x] Phiếu A01–A17 và optional T13/T17 ghi rõ đề xuất/chưa duyệt; không hardcode 10 câu/3 người/5 thắng.
- [x] Người dùng duyệt A01–A13,A16–A17, AWARD_V1 và giao T11; approval record nằm trong phiếu T10.
- [x] T11 implementation/runtime acceptance theo lệnh phê duyệt riêng của người dùng; A14/A15 vẫn hoãn T12, T13/T17 không triển khai.

## Verification sau mỗi task

T18 chỉ cập nhật checklist/signoff hiện có; không tạo báo cáo Markdown mới. [Kiểm kê Friendly](../../target/t18-friendly-inventory.json): giữ nguyên sáu file extension port/intent và constraint V10/V12; không có triển khai Friendly riêng được phát hiện. Không coi các test Friendly/reservation/King đã loại phạm vi là PASS hoặc blocker. Các gate đã duyệt vẫn theo T10/T12/T13; không phát sinh policy mới.

- [ ] Ghi files thực sửa, gate đã duyệt, test command/output/count PASS/FAIL/SKIP và phần chưa kiểm chứng.
- [ ] Không đổi ScoreEngine/RankingEngine/7 mode/author policy/CSRF/contract v1 nếu task không cho phép.
- [ ] Migration mới additive sau version hiện tại, giữ V1–V7/checksum/history; DB test chỉ trong môi trường được phép.
- [ ] `git diff --check`, kiểm tra imports/resources và regression đúng risk; không coi source test tồn tại là đã chạy.
- [ ] Dừng cuối task; không commit/push, không tự chuyển task hoặc chạy production migration.
