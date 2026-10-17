# T13 — Gate và phiếu duyệt danh hiệu nâng cao

Ngày 17/10/2026. **APPROVED — cả hai danh hiệu và T13-P03/P04.** Người dùng duyệt trực tiếp “Duyệt cả hai và T13-P03/P04 theo phiếu”, sau đó xác nhận lại đầy đủ ngưỡng, eligible group, evidence gate và new-games-only. Nội dung xác nhận khớp phiếu, không phát hiện mâu thuẫn. Signoff T11/T12 giữ nguyên; T14 không thuộc phạm vi, không commit/push.

Lượt khảo sát trước chỉ SELECT DB test và cập nhật tài liệu. Lượt sau phê duyệt được phép triển khai migration/code/tests T13, chỉ migrate và chạy fixture trên DB test/private schema; không áp dụng DB vận hành. Không tạo snapshot ranking giả hoặc diễn giải điểm thành CORRECT.

## Phiếu cần duyệt

| ID | Đề xuất cụ thể | Trạng thái |
|---|---|---|
| T13-P01 / P-MULTITALENT | Bật **Chuyên gia đa tài**: trong một Game qualified, mọi eligible Player đạt ≥3 mode, mỗi mode ≥2 CORRECT và ≥50% số câu thuộc participation đã chụp OPEN đều nhận một MULTITALENT/ALL. Không chọn người nhất, không giới hạn số người nhận | APPROVED T13 |
| T13-P02 / P-COMEBACK | Bật **Lội ngược dòng**: ≥2 stage; cùng tập eligible dùng cho cả hai mốc; cuối duy nhất sau stage đầu → nhất duy nhất cuối Game, comparator score DESC/cumulative CORRECT time ASC; tie ở mốc cuối hoặc đầu đều không đạt. Một COMEBACK/ALL, không tăng cúp | APPROVED T13 |
| T13-P03 / activation | Chỉ Game START sau kích hoạt mới chụp **AWARD_V2**, ghi rõ các badge đã bật/ngưỡng/hash. Game có AWARD_V1, kể cả còn ACTIVE/PENDING, tiếp tục chính sách cũ; không backfill hoặc mở lại decision terminal. Base thresholds/cúp/FAST/PERFECT/BXH giữ nguyên signoff T10/T12 | APPROVED T13 |
| T13-P04 / evidence gate | COMEBACK chỉ bật sau khi có test tái dựng ranking stage từ DB thật. Nếu dữ liệu một Game thiếu/không nhất quán thì không suy đoán, không tạo snapshot giả; finalizer giữ PENDING với lỗi dữ liệu để reconcile, theo nguyên tắc dữ liệu incomplete của T11. Báo rõ BLOCKED nếu chưa đáp ứng gate bằng chứng | APPROVED T13 |

Có thể duyệt cả hai, hoặc chỉ MULTITALENT + P03 và hoãn COMEBACK. Nếu chỉ chọn một badge, snapshot AWARD_V2 chỉ chứa phạm vi đó; bật thêm sau này phải có version/signoff mới, không âm thầm đổi AWARD_V2 đã chụp.

Approval record: T13-P01–P04 đều được người dùng duyệt trực tiếp ngày 17/10/2026 sau khi đọc phiếu. Đây là signoff riêng T13, không suy từ A01–A17 hay lời KHUYẾN NGHỊ trong V3.4. Các section khảo sát/test matrix bên dưới lưu bằng chứng trước triển khai; kết quả triển khai ghi trong báo cáo T13.

## Đối chiếu source và DB thật

| Nguồn thật | Có gì / giới hạn |
|---|---|
| achievements/model/ApprovedAwardPolicy.java | Chỉ AWARD_V1 với hash/ngưỡng chính xác; không có advanced threshold đã duyệt |
| achievements/model/AwardEvaluator.java | CUP, FAST, PERFECT; metric theo mode gồm participated/correct/correctTime. Chưa có evaluator MULTITALENT/COMEBACK |
| achievements/repository/AwardRepository.java: metrics | Mẫu số từ game_question_participation JOIN player_session/game_question; CORRECT từ outcome Answer. AFK NO_ANSWER vẫn có participation, không dùng earlyCloseWaitSet |
| game/entity/GameStage.java | order_index, first_question_index, question_count, mode/config snapshot; xác định ranh stage 1 |
| game/service/GameTransactions.java: score | Server commit Answer.score_after và result_snapshot.totalCorrectAnswerTimeMs ở từng câu V2, kể cả NO_ANSWER hợp lệ; không cần tạo bảng stage score mới |
| game/engine/RankingEngine.java | Comparator score DESC, time ASC, competition ranks; userId chỉ ổn định display tie |
| V12__award_decision_foundation.sql | Unique(Game,User,type,mode_key); type CHECK chỉ CUP/PERFECT/FAST. Decision terminal/child immutable, child insert sau terminal bị chặn |
| T12 profile/leaderboard | Tổng hợp committed ledger/participant. T13 không được tăng official-game/win/cup delta hoặc sửa ranking toàn Server |

Audit SELECT-only trên **quizz_task2_test**, connection readOnly + REPEATABLE_READ và rollback; không khởi động app hoặc chạy migration. Helper/evidence cục bộ: `target/T13EvidenceAudit.java`, `target/t13-evidence-audit.log`. Không in credential, username hoặc private moderation evidence.

Kết quả thực tế:

- 2 Game đã DECIDED, NORMAL V2/COMPLETED và có ≥2 stage.
- 467 participation có outcome NO_ANSWER đã scoring trong DB test; đây là số dòng nguồn, không phải test mới PASS hay số người đạt badge.
- Game **4962**: stage 1 QUIZ, question 1–10; sáu stage tiếp theo kết thúc question 16. Đủ Answer boundary question 10 cho ba eligible Player:

| User | score_after ở q10 | Snapshot cumulative correct time | SUM thời gian Answer CORRECT stage 1 | Final eligible rank đã commit |
|---|---:|---:|---:|---:|
| 21003 | 123 | 618 | 618 | 1 |
| 21004 | 0 | 103 | 103 | 3 |
| 21005 | 89 | 644 | 644 | 2 |

Ví dụ này chứng minh **có nguồn dữ liệu thật** để tái dựng, không chứng minh đã kiểm thử mọi trường hợp Comeback. Không có người từ cuối lên đầu trong mẫu này; không tạo/thêm award. Không báo “BLOCKED vì không có dữ liệu stage” khi dữ liệu đã tồn tại. Gate hiện tại là **chưa duyệt policy** và **chưa có bộ test T13 chứng minh đầy đủ**.

## Thiết kế tối thiểu sau signoff

1. Version policy snapshot: giữ deserialization/hash semantics của AWARD_V1; chỉ START mới chụp AWARD_V2 với advanced config được duyệt. Unknown policy/hash tiếp tục fail closed; GET policy preview đúng version đang kích hoạt.
2. MULTITALENT: dùng lại metrics participation T11, kiểm tra `100 * correct >= 50 * participated` bằng số nguyên; đếm mode khác nhau đạt ≥2 đúng. Tính từng eligible Player độc lập, không phụ thuộc award rank. Chưa OPEN/SPECTATOR/câu khác Game không vào mẫu số.
3. COMEBACK: kiểm tra cấu trúc stage/ranh global question, đầy đủ committed participation/outcome cho E; lấy **score_after tại ranh stage 1**, không SUM scoreDelta hoặc rescore. Đối chiếu cumulative correct time snapshot với SUM CORRECT answer_time_ms ở stage 1. Dùng RankingEngine hiện có cho E ở stage 1 và final; kiểm tra kích thước nhóm tie, không dùng ID phá hòa. Thiếu/mâu thuẫn evidence → lỗi dữ liệu, không default 0 hoặc bịa snapshot.
4. Migration additive **V13 dự kiến**, chưa tạo: mở rộng ck_achievement_shape cho đúng types được duyệt với mode_key=ALL; giữ nguyên unique và terminal/insert triggers. Có thể thêm nullable JSON evidence riêng trên ledger để lưu thresholds/modes đạt hoặc stage boundary/rank/time dùng xét badge; không tạo bảng copy scoreboard theo stage.
5. Advanced awards cùng awardDecisionCommit với ledger/participant và User-row ban fence của T11; rollback/retry/restart/duplicate dùng unique cũ. Không append badge vào terminal Game cũ, không mở lại quyết định, không sửa score/finalRank/History hoặc counter delta.
6. GET awards/own profile đọc badge đã commit; chỉ bổ sung nhãn/renderer/evidence được phép. Public profile tiếp tục đúng A15, không lộ mode/recent History. Ceremony/UI không tự cấp, không giữ award trong localStorage. Không thêm socket manager, worker hoặc kênh reconnect mới.

## Test matrix phải chạy nếu được duyệt

| Nhóm | Kiểm chứng bắt buộc |
|---|---|
| MULTITALENT boundaries | Hai/nhiều Player cùng đạt đều nhận, kể cả người không đứng đầu; 2 mode không đạt; đủ đúng nhưng <50% không đạt; đúng 50% đạt; ≥2 correct từng mode |
| Participation | AFK NO_ANSWER tăng denominator; late/reconnect/restricted không tự tạo Answer cho câu chưa OPEN; Spectator không nhận; kiểm tra SQL numerator/denominator |
| Qualification/eligibility | V1/FRIENDLY/no-policy/NOT_QUALIFIED/cancel/interrupted không nhận; correct của người bị Kick/Ban không giúp E; chính người restricted không nhận |
| COMEBACK history proof | Tái dựng từ committed Answer qua các mode, stage bonus/Spin/Star/floor; so với state thật ở ranh stage; cùng E sau loại người; đầu/cuối tie không nhận; <2 stage không nhận; dữ liệu thiếu/mismatch không nhận/không giả snapshot |
| Transaction/version | AWARD_V1 pending vẫn xử lý cũ; chỉ Game mới AWARD_V2; không backfill; duplicate/concurrent/retry/rollback/crash/restart chỉ một badge; BAN vs award cả hai lock order |
| UI/API/regression | Receipt/profile đổi route/logout/Room close vẫn còn; chỉ nhãn đã commit; public A15 không rò data; full Java regression và browser award/profile/7 mode liên quan |

## Lịch sử xác minh — lượt khảo sát trước signoff

**Đã làm:** review source/signoff/§6, SELECT-only evidence audit và tài liệu cụ thể cho người dùng duyệt. **Chưa làm:** implementation/migration, test T13, browser smoke T13, cấp badge. Không gọi audit tĩnh hoặc SELECT evidence là runtime acceptance PASS.

Baseline kế thừa T12: full 345 unit + 232 IT PASS và browser profile/7 mode/auth/lifecycle trong [báo cáo T12](T12_PROFILE_LEADERBOARD_REPORT.md). Không chạy lại các baseline đó chỉ vì thêm tài liệu, không ghi thành test T13 đã chạy.
