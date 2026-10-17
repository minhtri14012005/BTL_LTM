# T13 — Đa tài và Lội ngược dòng, AWARD_V2

Ngày 17/10/2026. Hoàn tất hai badge; full regression, migration và browser/restart PASS. Không T14, không commit/push; không migrate hoặc cấp thưởng DB production.

## Signoff và activation

[T13-P01–P04](T13_ADVANCED_ACHIEVEMENTS_SIGNOFF.md) đã được người dùng duyệt cả hai danh hiệu, sau đó xác nhận lại đầy đủ. Không phát hiện khác biệt giữa P03/P04 và lệnh xác nhận. [Phiếu T10](T10_AWARD_POLICY_SIGNOFF.md) bổ sung approval record riêng, không sửa signoff AWARD_V1.

- **MULTITALENT/ALL:** mỗi eligible Player đạt ≥3 mode khác nhau; từng mode ≥2 CORRECT, correct/participation ≥50%. Mọi người đạt đều nhận, không so ai đứng nhất.
- **COMEBACK/ALL:** ≥2 stage; cùng eligible group ở hai mốc; cuối duy nhất sau stage 1 và nhất duy nhất cuối Game. Tie ở một trong hai mốc không nhận. Comparator vẫn score DESC/cumulative CORRECT time ASC; ID chỉ ổn định display.
- Chỉ NORMAL V2/rules 2, FINISHED/COMPLETED, qualified theo base thresholds đã ký. Không cộng cúp/game/win delta do badge mới; không ảnh hưởng BXH A14.
- `ApprovedAwardPolicy.approved()` giữ nguyên AWARD_V1/hash. `active()` là AWARD_V2 với advanced thresholds và hash mới. START V2 mới dùng active; GET policy phản ánh AWARD_V2. Schema/rules gameplay vẫn 2.
- AWARD_V1 ACTIVE/PENDING/terminal tiếp tục chính sách cũ; JSON cũ không có advanced deserializes null. Không backfill, đổi snapshot hay mở lại terminal. Policy/hash/config không khớp một version đã ký thì fail closed.

## Gate evidence thực hiện trước Comeback

Audit SELECT-only trước signoff đã thấy Game test 4962 có score_after/time snapshot tại q10 khớp SUM CORRECT times; xem phiếu. Sau signoff, **StageRankEvidenceIT chạy và PASS trước khi thêm AdvancedAwardEvaluator/khởi động activation**:

| Evidence target/ | Thực tế |
|---|---|
| t13-stage-gate.log | Fixture gọi Spin sau QUESTION_OPEN → Server từ chối INVALID_STATE; không PASS |
| t13-stage-proof.log | Đã sửa phase nhưng fixture chỉ 4 Quiz nên không có Spin → SPIN_NOT_AVAILABLE; không cấp resource giả |
| t13-stage-verified.log / suffix T13-stage-verified | Đổi fixture thành 10 Quiz hợp lệ + 6 Riddle, gọi Spin/Star trong DECISION; **2/2 MySQL IT PASS**, BUILD SUCCESS trước triển khai xét Comeback |

Gate so với state thật do GameTransactions.score commit ở ranh màn: score, cumulative CORRECT time và rank đều trùng; gồm Spin/Star, stage final, điểm sàn và AFK NO_ANSWER. Chơi tiếp stage sau không thay evidence stage 1; subset E được rank lại và giữ tie. Thêm test first-stage RIDDLE và thiếu snapshot bị từ chối. Không sửa ScoreEngine/RankingEngine để làm test xanh.

## Nguồn dữ liệu và thuật toán

StageRankEvidenceRepository chỉ đọc lịch sử, không rescore. Nó kiểm tra stage order/range liên tục, mode/stage_question_index khớp question snapshot, đủ participation/outcome/scored cho từng eligible Player ở stage 1. Score lấy **Answer.score_after đúng câu cuối stage 1**; không SUM delta (sai khi floor/effect). Time lấy result_snapshot.totalCorrectAnswerTimeMs và đối chiếu SUM answer_time_ms của Answer CORRECT thuộc participation stage 1.

Không default 0 cho thiếu row/snapshot/time; thiếu/mismatch → `AWARD_STAGE_DATA_INCOMPLETE`. Finalizer chưa ghi participant/ledger nên rollback giữ receipt PENDING; worker retry theo reconciliation T11. Không tạo scoreboard snapshot bù hoặc công bố badge dự kiến. Game không qualified không cần xét Comeback; vẫn NOT_QUALIFIED, không badge.

Base AwardEvaluator tính CUP/FAST/PERFECT không đổi; AdvancedAwardEvaluator nhận base qualified result + E + historical stage evidence, trả thêm hai type. MULTITALENT dùng mode metrics từ game_question_participation: AFK NO_ANSWER tăng denominator, CORRECT mới tăng numerator. Câu chưa OPEN/ngoài Game/SPECTATOR không tự xuất hiện. So sánh accuracy bằng tích số nguyên, không làm tròn.

COMEBACK rank lại E ở boundary bằng RankingEngine hiện có, so với final award rank đã tính trên cùng E. Không dùng finalRank lịch sử khi admin loại người; History score/rank vẫn giữ nguyên. Đã kiểm thử Ban trước decision và Kick ACTIVE khiến E thay đổi, không yêu cầu snapshot của người bị loại cho các câu sau Kick.

## Schema, transaction và contract

V13__advanced_achievement_evidence.sql additive, không sửa V1–V12:

- Thêm nullable `achievement_award.evidence_snapshot` JSON. Basic rows cũ giữ NULL. Advanced type phải mode_key ALL và evidence là JSON object.
- Mở rộng CHECK cho MULTITALENT/COMEBACK; unique(Game,User,type,mode_key) giữ nguyên.
- Insert trigger giữ guard PENDING; advanced types còn yêu cầu parent policy AWARD_V2. Không append vào terminal V1/V2. UPDATE immutable triggers/foreign keys cũ giữ nguyên.
- Không bảng stage scoreboard, không projection/counter hoặc worker thứ hai, không cập nhật score/Answer/History từ finalizer.

Sau sorted original PLAYER User locks của T11, finalizer đọc sanctions/intents/markers, tính E, kiểm tra base completeness rồi đọc stage evidence. Toàn bộ basic+advanced ledger, participant/deltas và terminal receipt trong **cùng awardDecisionCommit**. Existing BAN row fence, TEMP expiry before-commit recheck, rollback/retry/restart vẫn bao trùm các badge mới. Game worker không chờ browser/network.

Evidence MULTITALENT lưu version/ngưỡng/mode metrics đạt. Evidence COMEBACK lưu version/stage count/end question/first-stage score+correct time+rank/finalEligibleRank/E count. Đây là bằng chứng quyết định bất biến, không nguồn score thứ hai.

GET `/api/games/{id}/awards` thêm `awards[].evidence` ({} cho basic/legacy), giữ quyền retained GameMember và chỉ FINISHED; không trả private moderation/session/password hoặc câu chưa công bố. Own profile/badge receipts T12 tự aggregate ledger mới; public profile/BXH giữ A15, không mode/History/badge evidence công khai. Không thêm endpoint mutation hoặc WS event.

Ceremony và own profile dùng nhãn chung “Chuyên gia đa tài”/“Lội ngược dòng”; chỉ từ receipt đã commit. PENDING không dựng badge dù payload fixture cố đưa badge. Không timer/listener/transport mới, không localStorage hoặc UI tự cấp thưởng.

## File ownership và phạm vi

| Owner/file | Thay đổi |
|---|---|
| ApprovedAwardPolicy | Hai signed version, legacy JSON/hash tương thích |
| StageRankEvidence + repository | Historical facts, completeness và boundary ranking từ DB |
| AdvancedAwardEvaluator | Pure milestone/unique last→first; evidence frozen |
| AwardEvaluator.Award | Thêm evidence; overload cũ giữ basic evaluator behavior |
| AwardTransactions | Nối advanced sau base qualification/trước writes; cùng transaction/fence |
| V13 migration | Type/evidence/check/insert guard, không backfill |
| GameTransactions + GameAwardsController | Chỉ đổi factory activation tại START/GET policy |
| GameAwardsView + AwardReadService | Optional frozen evidence, không đổi History |
| shared/achievement-ui.js + results/awards.js | Nhãn badge thực từ Server |
| AdvancedAwardFixture/StageRankEvidenceIT/AdvancedAchievementsIT | Source-proof, three-mode/AFK/eligibility/transaction/compatibility |
| AdvancedAwardMigrationIT | V12→V13 trên private test schema |
| test-advanced-award-client.py | Browser 3 mode thật, profile và genuine pending/restart handshake |

Không di chuyển file, không tạo feature rỗng, không dependency/build step mới. Giữ code/dirty work T00–T12 và 1.txt. Các engine, QuestionCloseGate, History, realtime transports và security/session/CSRF guards không đổi.

## Verification

| Evidence target/ | Kết quả |
|---|---|
| t13-focused.log | Compile fixture sai int→Long expectedVersion; đã sửa 0L; không gọi PASS |
| t13-focused-run.log, suffix T13-focused-run | **354 unit + 32 IT PASS**, 0 failure/error/skipped; 7 AdvancedAchievementsIT + 2 stage proof + 14 AwardDecisionIT + 9 ProfileLeaderboardIT |
| t13-full.log | Lượt full đầu có lỗi fixture migration: PlayerSession mặc định schemaVersion 1 trỏ Game V2; đã sửa seed thành 2. Không ghi lượt này xanh |
| t13-migration-final.log | V12→V13 migration IT **1/1 PASS** sau sửa fixture; không backfill |
| t13-verified.log, suffix T13-verified | Full **354 unit + 242 IT PASS**, 0 failure/error/skipped; XML reports xác nhận |
| t13-advanced-client-tests.json | **5 smoke + 1 component check PASS**; Game 5461/5462, AFK denominator, hai người nhận Đa tài, unique Comeback, profile/History/Room close/logout/login |
| t13-runtime-verification.log + t13-restart-hold.log | **Genuine FINISHED/PENDING → crash → restart PASS**; Game 5462, không fake snapshot, không duplicate và giữ ledger IDs trận trước |
| t13-lan-seven-client-tests.json | **93 component checks + 7 smoke PASS**, bảy mode thật, reconnect/ACK replay/media/cancel |
| t13-auth-lobby-client-tests.json / t13-lifecycle-client-tests.json | **15 component checks + 22 smoke / 19 lifecycle checks PASS** |
| client-build.json / t13-static-resources.json | **41 module/62 file** kiểm tra import/copy; **61 JS/CSS/HTML HTTP 200** qua LAN origin, bytes khớp source; jar khớp 62 static files |

Các lượt browser trước lỗi Chrome/helper compilation/đợi logout được giữ trong `target/t13-*-no-browser.log`, `t13-*-helper-*.log`, `t13-*-logout-timing.log`; không ghi những lượt đó PASS. Helper được biên dịch trước khi chơi, không đổi timer/luật production. Ảnh desktop/360px đã xem tại [advanced-awards](../assets/advanced-awards-2026-10-17/); không tràn ngang, tên chứa HTML hiển thị dạng text.

354 unit gồm baseline 345 + 9 pure advanced policy/evaluator tests. Không cộng các lượt chạy lại vào số test phân biệt.

Các IT mới kiểm tra hai milestone và một Comeback với real AFK denominators, cup/game/win delta không tăng thêm, evidence hỏng giữ PENDING không child rows rồi repair/retry, rollback/concurrent retry, Ban/Kick, old V1 active/pending sau activation, short/cancel không badge. Các race BAN/award hai thứ tự/TEMP expiry và V1/FRIENDLY exclusions vẫn chạy trong regression T11/T12.

```powershell
mvn -o -B --no-transfer-progress -Pmysql-smoke verify '-DargLine=-Djava.io.tmpdir=D:/BTL_LTM/target -Dquiz.awards.initial-delay-ms=86400000' '-Dsurefire.reportNameSuffix=T13-verified'
python -X utf8 scripts/build-client.py
python -X utf8 scripts/test-advanced-award-client.py --origin http://192.168.1.234:8088 --port 9227 --restart-check
```

Initial delay 86400000ms chỉ isolate fixture trong cached Spring contexts như T11/T12; runtime browser dùng scheduler mặc định 1 giây. Không chạy Server cùng DB khi Maven IT đang chạy. Chỉ DB quizz_task2_test/private quizz_t13_migration_*; không DB production.

## Giới hạn

Không T14, Friendly/T15 hoặc King/T17. Gameplay Friendly chưa triển khai nên không tuyên bố smoke Friendly; schema exclusion vẫn được regression kiểm chứng. LAN nhiều thiết bị vật lý và tải lớn chưa xác minh; nhiều Chrome context trên cùng máy qua LAN origin không tương đương nhiều máy.
