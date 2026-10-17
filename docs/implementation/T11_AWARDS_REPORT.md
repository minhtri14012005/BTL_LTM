# T11 — AWARD_V1: decision, ledger và ceremony

Ngày 17/10/2026. **T11 đã hoàn thành và kiểm chứng AWARD_V1.** Không triển khai T12/T13/T17, không commit/push, không áp dụng DB vận hành.

## Signoff và scope

[Phiếu T10](T10_AWARD_POLICY_SIGNOFF.md) đã cập nhật lệnh duyệt trực tiếp của người dùng: A01–A10/A13 theo phiếu, A11/A12 bật, A16/A17 new-games-only và Server evaluation time; policy ID **AWARD_V1**. A14/A15 hoãn tới T12, advanced badges/King không thuộc T11.

Các ngưỡng production trong `ApprovedAwardPolicy.approved()` nay là giá trị **được duyệt**, không phải đoán: NORMAL V2/rulesVersion 2, FINISHED+COMPLETED; ≥3 PLAYER ban đầu, ≥10 configured và scored, ≥2 eligible, ≥1 CORRECT trong eligible. Game COMPLETED còn phải có toàn bộ snapshot questions OPEN/scored và outcome/participation/rank hợp lệ.

Eligible loại Game-targeted marker/intent PENDING **hoặc DONE**, account ban hiệu lực lúc xét, soft-deleted account; không loại vì AFK/disconnect/đã rời Room/đang ở Game Y. Host PLAYER xét như người chơi; SPECTATOR không có participant award. ADMIN thật sự là PLAYER không tự bị loại chỉ do role. Đồng thắng nhận mỗi người 1 cúp. Không thêm điều kiện người thắng phải tự có CORRECT ngoài điều kiện CORRECT chung trong E được ký.

FAST: mỗi mode, ≥5 correct, ≥60% trên participation, mean CORRECT time nhỏ nhất, exact rational ties cùng nhận. PERFECT: ≥10 participation và tất cả CORRECT, mọi người đạt đều nhận. NO_ANSWER vào denominator; chưa OPEN không tự sinh denominator/Answer. Không suy đúng từ score/Spin/Star/floor0.

## DB và new-games-only

Migration additive **V12__award_decision_foundation.sql**; giữ nguyên checksum V1–V11. Chỉ áp dụng `quizz_task2_test` và private `quizz_t11_migration_*` trong các test; không chạy với DB vận hành.

- `game_session.game_type` mặc định NORMAL, có giá trị extension FRIENDLY_1V1 nhưng không có hệ thống giao hữu mới. `award_policy_snapshot` NULL cho mọi Game cũ; chỉ START NORMAL V2 mới chụp approved AWARD_V1. Không backfill cúp hay policy từ FINISHED/winners của lịch sử.
- `game_award_decision`: PK Game, policy/version/hash/result revision, PENDING→DECIDED/NOT_QUALIFIED, frozen counts/reasons/evaluation time và retry diagnostics. Terminal update bị DB trigger từ chối.
- `game_award_participant`: PK(Game,User), FK PlayerSession, eligible/evidence, historyRank khác awardRank, metrics D/C/T và official game/win delta. Không FK current membership/occupancy.
- `achievement_award`: unique(Game,User,type,**mode_key NOT NULL**); ALL cho CUP/PERFECT, mode thật cho FAST; value=1. Child insert chỉ khi decision PENDING; child UPDATE bị từ chối. Không cascade xóa History khi đóng Room.
- Không có bảng total stats thứ hai: T12 có thể tổng hợp officialGamesCount từ participant delta, wins từ win delta và cups từ ledger đã commit. Không đếm mọi FINISHED hoặc số lượng badge thành số trận.

Trigger Game immutable giữ nguyên kiểm tra snapshot cũ và thêm game type/policy. Không sửa JSON rules V1/V2 hay nội dung câu. Game cũ/policy NULL trả NO_POLICY_SNAPSHOT, V1 UNSUPPORTED_SCHEMA, FRIENDLY EXCLUDED_GAME_TYPE; không giả DECIDED/NOT_QUALIFIED cho những Game chưa từng có decision.

## Hai commit và recovery

```text
Game queue → GameTransactions.score/finish
  Room → Game → sorted Users
  score/finalRank/FINISHED/endReason + decision seed PENDING
  gameResultCommit → publish GAME_END

AwardReconciler bounded 4 workers, discovery từ DB (không cần event/Client)
  RoomBoundary trước TX
  [missing receipt: Room → Game → seed PENDING commit riêng]
  AwardTransactions: Room → Game → decision → original PLAYER Users sorted
  fresh DB sanctions + all GAME intents/markers → E + committed outcome metrics
  participant evidence/deltas + unique ledger + terminal decision
  awardDecisionCommit
```

Normal result commit và PENDING seed atomic; result emit không là cúp. Missing seed chỉ được repair cho FINISHED có snapshot, không quét old FINISHED để retro-award. Repair seed commit riêng giúp lỗi policy/data sau đó vẫn có pending receipt/retry diagnostics. Pending receipt phải trùng policy ID/hash/**result revision** của Game; lệch thì giữ pending, không cấp thưởng theo default hiện hành.

Finalizer không dùng GameRuntime terminal actor, không đợi network hoặc Game future; Room CLOSED hay Y đã ACTIVE không cản finalize X. Không set Room WAITING lại, detach g2, revoke session, clear occupancy Y hoặc gọi finish(X). Reconciler chạy sau GameStartupCleanup.ready(), giữ policy SERVER_INTERRUPTED cũ, không resume gameplay.

Scoring/History vẫn dùng score và finalRank gốc. AwardEvaluator dùng lại RankingEngine với eligible entries, score DESC/correctTime ASC/competition ranks; ID chỉ ổn định display. Trường hợp người final rank 1 bị Kick, người còn eligible có thể award rank 1 nhưng finalRank trong DB không bị sửa.

BAN chỉ giữ target User lock, không lấy Room/Game lock hoặc chờ worker. Award sau sorted User locks mới đọc state; ban trước award được xét, award giữ User trước thì ban phải chờ và không hồi tố decision đã commit. ACTIVE X removal intent vẫn loại X dù UNBAN/effect DONE. BAN sau FINISHED không tự tạo intent X nhưng account ban đang hiệu lực vẫn được xét; UNBAN trước award và không X-target thì xét current account.

A17: eligibilityEvaluatedAtMs là Server time sau đủ User locks, không là physical COMMIT timestamp. Before-commit recheck nếu đi qua biên TEMP expiry (kể cả đồng hồ lùi làm đổi banned state) thì rollback toàn decision/ledger và retry. Row lock không được mô tả như đóng băng đồng hồ.

## API và UI

| Endpoint thật | Authority / hành vi |
|---|---|
| GET `/api/awards/policy` | Session/account guard, no-store, chỉ public approved config AWARD_V1; dùng preview trước START |
| GET `/api/games/{id}/awards` | Session/epoch/account + retained GameMember như History, repeatable-read/no-store; ACTIVE trả RESULT_NOT_READY; pending không lộ provisional eligibility; terminal đọc frozen decision/ledger |

Không có POST/PUT/DELETE finalize/cupDelta/reset decision. ADMIN qua endpoint người chơi vẫn cần quyền GameMember. Người bị Kick nhưng account còn quyền có thể đọc retained History/awards; live snapshot/WS X tiếp tục bị chặn. DTO không chứa private moderation reason, admin actor, banVersion/epoch/cutoff, intent IDs, raw session/password/đề chưa công bố.

`results/awards.js` nối Game result và History: PENDING, NOT_QUALIFIED có reason, DECIDED Top 3 theo award rank, ledger CUP/FAST/PERFECT và own deltas. Tên người chơi dùng text DOM; icon trophy SVG gốc trong project, không CDN. “+1 Cúp vô địch” chỉ xuất hiện với CUP value=1 từ DECIDED; không dùng snapshot.winners hay animation để quyết định.

Panel sở hữu một GET có AbortController/poll bounded 60 lần, dừng terminal/dispose, không có WS listener/reconnect handler thứ hai. Core HTTP chỉ thêm signal tùy chọn; không DOM. Preview Room/editor đọc threshold Server, cảnh báo trận ngắn nhưng không disable START hoặc đổi author-pack policy. Styles dùng tokens sẵn, không animation can thiệp timer.

Nút về Room/Chơi tiếp vẫn từ Room GET hiện hành của T06; History/Home hiện có được giữ. Profile thành tích/BXH chưa có API và A14/A15 đã hoãn T12, nên **không dựng link/nút giả hoặc tự triển khai T12**. T13/T17 không có code/counter/ngưỡng trong T11.

## File ownership và refactor

| File/owner | Thay đổi |
|---|---|
| `achievements/model/ApprovedAwardPolicy`, `AwardEvaluator` | Policy được ký và pure evaluator, dùng RankingEngine hiện có |
| `achievements/repository/AwardRepository` | Committed metrics/completeness, durable seed/discovery và identity check |
| `achievements/service/AwardTransactions`, `AwardReconciler`, `AwardReadService`, `AwardClock` | Transaction/fence, bounded recovery, read DTO, injectable Server clock |
| `achievements/controller/*`, `dto/GameAwardsView` | Hai GET và safe error mapping, không mutation API |
| `GameSession`, `GameTransactions` | Hai immutable metadata fields, chụp policy ở V2 START và seed trong finish; không nhét logic trao thưởng vào GameTransactions |
| `SecurityConfiguration`, architecture test | Allowlist GET cụ thể, thêm owner achievements khi có file thật |
| `results/awards.js`, Game/History/App dispose, `core/api.js` | Ceremony/read lifecycle và optional abort signal |
| `room/award-policy.js`, editor/page, scoped awards CSS, icons | Preview và presentation, không business mutation |
| `AwardEvaluatorTest`, `AwardDecisionIT`, `AwardMigrationIT` | Policy, real MySQL/fences/rollback/recovery và V11 upgrade |
| Shared GameLifecycleFixture, MultimodeRoomStartIT | Cleanup các child mới trên DB test, migration latest=12; giữ assertions gameplay cũ |
| `test-award-client.py`, `AwardRestartFixture.java` | Browser thật và crash barrier SQL đúng owned Game/User, không dùng DB/app production |

0 file di chuyển, không folder feature trống, không dependency/framework/build step mới. Hash 22 file bảo vệ theo mốc trước T11 (V1–V11, configs/pom, engines/CloseGate và engine tests) không đổi.

## Verification

| Lượt chạy / evidence `target/` | Kết quả thực tế đã có |
|---|---|
| `t11-unit.log` | 345 unit PASS, 0 failure/error/skipped |
| `t11-award-it.log` | 12 Account award IT đầu PASS; lượt compile trước đó sai boxing Long trong fixture đã sửa, không phải PASS |
| `t11-full.log` | 345 unit PASS; 221 IT, **2 lỗi cleanup FK**, không có assertion failure. Worker từ Spring context khác xử lý pending khi fixture xóa Game; không ghi lượt này là xanh |
| `t11-final.log`, suffix T11-final | Full 345 unit + 221 IT PASS, 0 failure/error/skipped, BUILD SUCCESS. Initial delay worker đặt 86400000ms cho mọi cached context trong JVM test để cô lập fixture; manual dispatch vẫn test production finalizer |
| `t11-award-final.log`, suffix T11-award-final | Sau thêm pending evidence identity check và exclusion cases: 14/14 AwardDecisionIT PASS, BUILD SUCCESS |
| `AwardMigrationIT` trong full | Private V11→V12, giữ user/history/config, old policy NULL/no backfill, immutable metadata, mode NOT NULL/unique, rerun 0 migration |
| `t11-package-ui-final.log` | Package thành công; fixed `t11-runtime.jar` dùng cho browser cuối; ceremony đặt trước final summary |

**223 integration tests phân biệt đã đạt qua full + focused rerun**: baseline 208 + 14 AwardDecisionIT + 1 AwardMigrationIT. Không cộng 12/14 test chạy lại vào tổng. Lượt focused cuối kiểm chứng các thay đổi backend sau full; không mô tả full verify cuối đã bao gồm hai case thêm sau đó.

```powershell
mvn -o -B --no-transfer-progress -Pmysql-smoke verify '-DargLine=-Djava.io.tmpdir=D:/BTL_LTM/target -Dquiz.awards.initial-delay-ms=86400000' '-Dsurefire.reportNameSuffix=T11-final'
mvn -o -B --no-transfer-progress -Pmysql-smoke test-compile '-Dit.test=AwardDecisionIT' '-DargLine=-Djava.io.tmpdir=D:/BTL_LTM/target -Dquiz.awards.initial-delay-ms=86400000' '-Dsurefire.reportNameSuffix=T11-award-final' failsafe:integration-test failsafe:verify
python -X utf8 scripts/build-client.py
python -X utf8 scripts/test-award-client.py --origin http://192.168.1.234:8088 --port 9227 --restart-check
```

Worker runtime mặc định khởi động sau 1000ms và chạy mỗi giây. Tham số initial-delay chỉ điều chỉnh thời điểm khởi động scheduler, không đổi policy/new-games-only hoặc tự cấp thưởng qua GET. Browser chạy **không có delay test này** để kiểm chứng tự động finalization và recovery thật. Không chạy test Server trên cùng DB trong lúc Maven IT chạy.

Các IT mới chứng minh: result trước award, duplicate/concurrent finalizer, rollback không có child/delta một phần, delayed delivery retry trả ledger cũ, eligible-only CORRECT khi người đứng đầu bị loại, BAN sau FINISHED, ACTIVE intent qua UNBAN, cả hai User lock orders, TEMP biên expiry, cancel/interrupted giữ accepted-unscored, short Game NOT_QUALIFIED immutable, seed repair không từ GET, V1 và metadata FRIENDLY/no-policy V2 excluded, mismatch receipt không cấp thưởng.

### Browser và restart thật

Browser Chrome 155 chạy qua LAN origin `http://192.168.1.234:8088`, nhiều context trên cùng máy. Account, question pack, Room, Game và receipt đều được tạo qua Server thật trên DB test; không mock kết quả/cúp. Chrome test riêng chạy headless với sandbox bị tắt do hạn chế môi trường; không đổi cấu hình Chrome người dùng.

| Evidence `target/` | Kết quả |
|---|---|
| `t11-award-restart-client-tests.json`, `t11-award-restart-browser.log` | PASS 9 nhóm + 4 component checks; bao gồm crash/restart thật |
| `t11-award-client-tests.json`, `t11-award-browser-ui-final.log` | PASS 8 nhóm + 4 component checks trên jar/UI cuối, sau khi đặt ceremony trước final summary |
| `t11-lan-seven-client-tests.json`, `t11-seven-browser.log` | PASS 93 checks + 7 smoke groups: đủ 7 mode, media thật, INTRO/reconnect, ACK mất/retry, History và cancel |
| `t11-auth-lobby-client-tests.json`, `t11-auth-browser.log` | PASS 15 checks + 22 smoke groups; Login/Register/Lobby/Room, session/CSRF/logout và socket replacement |
| `t11-lifecycle-client-tests.json`, `t11-lifecycle-browser.log` | PASS 19 component lifecycle checks |

Ceremony xác minh PENDING không nói +1 cúp dù dữ liệu fixture cố đưa CUP; terminal đọc ledger thật, reload giữ nguyên ledger IDs; tên `<b>Player A</b>` hiển thị văn bản, không tạo HTML. Game ngắn hiển thị NOT_QUALIFIED, không delta; cảnh báo trước START không chặn gameplay. Người bị Kick vẫn đọc retained History nhưng không đọc live snapshot X. Các panel dispose hủy request/poll, không thêm WS listener. Layout 360px không tràn ngang toàn trang; bảng lịch sử có vùng cuộn riêng.

Crash/restart dùng `AwardRestartFixture.java` chỉ với owned User/Game test. Helper **đợi Game FINISHED đã commit**, giữ User row lock và xác nhận decision PENDING để award worker chưa commit; không sửa dữ liệu. Driver đọc PENDING thật, dừng JVM Server đang sở hữu, rồi mới nhả lock. Server mới dùng scheduler mặc định 1 giây tự recovery Game 4719 thành DECIDED; đọc lại giữ ledger IDs, ledger Game 4716 trước crash không thay đổi. `t11-restart-hold.log` ghi `GENUINE_PENDING_READY game=4719` và release không DML. Đây là recovery award sau result commit, không phải resume Game ACTIVE.

SELECT-only `t11-db-audit.txt` xác nhận:

| Game | Decision / bằng chứng DB |
|---|---|
| 4716 | DECIDED, 3 eligible / 30 correct, 1 CUP, 3 official-game delta / 1 win delta |
| 4717 | Người bị Kick vẫn score 110, finalRank 1; eligible=false, awardRank NULL, cả hai delta=0. Người score 20/finalRank 2 nhận awardRank 1 và CUP; E có 2 correct |
| 4718 | NOT_QUALIFIED; 0 CUP / 0 official-game delta / 0 win delta |
| 4719 | Sau restart DECIDED, 1 CUP, 3 official-game delta / 1 win delta |
| Legacy id ≤4271 (mốc trước T11) | 0 Game có policy snapshot, 0 ledger award; không backfill |

Ảnh đã kiểm tra trực quan: [ceremony desktop](../assets/awards-2026-10-17/decided-1440.png), [eligible ranking sau Kick](../assets/awards-2026-10-17/eligible-ranking-1440.png), [mobile 360px](../assets/awards-2026-10-17/ceremony-360.png). Điểm/hạng History vẫn hiển thị độc lập với award rank.

### Kiểm tra phạm vi cuối

`scripts/build-client.py`: 38 module / 58 files, không thêm build step bắt buộc cho app. Cả 58 static files trong jar test cuối khớp byte source. `git diff --check` trả 0; trạng thái Git thực tế lưu tại `target/t11-final-git-status.txt`, vẫn gồm thay đổi từ các task trước, không coi tất cả là T11. `target/t11-change-inventory.json` so hash với mốc T10, phân biệt file mới/sửa/xóa; không di chuyển file. V1–V11, application configs, pom, ba engine, QuestionCloseGate và ba engine tests (22 file) giữ nguyên hash; `1.txt` giữ nguyên. Server và Chrome test riêng được dừng sau kiểm chứng; không thay DB service hay Chrome người dùng.

## Approval / pending

- AWARD_V1 đã duyệt và được triển khai; không xin lại các policy A01–A13/A16–A17.
- A14/A15, profile/global leaderboard: PENDING T12, không scope T11.
- Advanced titles/King: ngoài T11, không suy 5 wins/3 opponents thành production constants.
- Friendly reservation r1/r2/AC-42b/c: PENDING T15/T18; chỉ có game_type exclusion metadata, không Friendly system giả.
- LAN nhiều thiết bị vật lý chưa xác minh; browser contexts trên một Chrome/một máy qua LAN origin không được gọi là nhiều máy.
- Không commit/push, giữ thay đổi T00–T10 và `1.txt`; không migrate hay cấp thưởng DB vận hành.
