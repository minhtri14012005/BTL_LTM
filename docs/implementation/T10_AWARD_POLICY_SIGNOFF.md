# T10 — Phiếu chốt policy thưởng

Ngày 17/10/2026. **ĐÃ ĐƯỢC NGƯỜI DÙNG PHÊ DUYỆT cho T11 — policy ID `AWARD_V1`.** Lệnh signoff duyệt A01–A13, A16–A17 theo phiếu; A11/A12 bật. A14–A15 đã hoãn ở T11 và nay được duyệt riêng trong T12; T13/T17 không triển khai trong T11/T12. Không commit/push Git hoặc chạy migration DB vận hành. Xem [thiết kế DB/API/transaction](T10_AWARD_POLICY_AND_TRANSACTION_DESIGN.md).

Nguồn: [V3.4](../../THIET_KE_NANG_CAP_MULTIGAME_V3_4_ADMIN_ACCEPTANCE_FINAL_REVIEW.md) §5–7, §10–11, §16, §18.6/18.10. Giữ History, transaction/idempotency và security là bất biến thiết kế. Ngưỡng và cách phân phối A01–A13/A16–A17 đã được duyệt bằng lệnh signoff riêng sau T10, không suy ra từ lệnh T08/T09.

## 1. Bất biến sẽ giữ

- Không sửa ScoreEngine, MultimodeScoreEngine, RankingEngine, score/finalRank/Answer hoặc History V1 để trao thưởng.
- `gameResultCommit` khác `awardDecisionCommit`; FINISHED/`hasOfficialWinner` chưa chứng minh đã cấp cúp.
- Nguồn là DB đã commit; Client không tự khai cupDelta/rank/thời gian/correct. UI, reconnect, mở result hoặc Room close không cấp thưởng.
- Một decision duy nhất cho Game, kể cả không qualified; terminal decision bất biến. Rollback không có nửa ledger; retry/restart không nhân đôi.
- Không lấy AFK/disconnect/current membership/occupancy làm lý do ADMIN loại khỏi award. Spectator không có PlayerSession để tự nhận thưởng.
- Không tự hồi tố Game V1, FRIENDLY, CANCELLED/SERVER_INTERRUPTED hoặc Game cũ trước activation thành cúp mới.
- Ban/Kick security và scoring câu đã OPEN tiếp tục theo T08/T09. Award không gọi ejection, không giải phóng occupancy và không sửa quyền Game Y.

## 2. Policy đã ký và các mục hoãn

Các mục A01–A13, A16–A17 bên dưới đã được người dùng duyệt nguyên đề xuất để triển khai AWARD_V1. A14–A15 đã hoãn ở T11 và **được phê duyệt riêng tại T12 ngày 17/10/2026** theo approval record bổ sung bên dưới.

| ID / gate | Nội dung duyệt theo phiếu T10 | Phạm vi và ý nghĩa | Trạng thái |
|---|---|---|---|
| A01 / P-AWARD-BASE | Chỉ NORMAL + schemaVersion=2/rulesVersion=2 + FINISHED + COMPLETED + dữ liệu finalize đầy đủ mới có thể qualified | Đồng ý bộ lọc; version/rules chưa biết thì chặn xét, không diễn giải như V2 hiện có | APPROVED AWARD_V1 |
| A02 / P-AWARD-BASE | Roster **ban đầu ≥3 PLAYER**, không tính Spectator; Host PLAYER được tính như Player khác | Chốt `minInitialPlayers`. Giới hạn START NORMAL hiện đã là 3, nhưng đó không phải phê duyệt ngưỡng award | APPROVED AWARD_V1 |
| A03 / P-AWARD-BASE | **≥10 câu configured và ≥10 câu thực sự OPEN+SCORING committed**; Game COMPLETED phải có toàn bộ câu snapshot chấm đủ | Chốt `minConfiguredQuestions`, `minScoredQuestions` và định nghĩa “thực sự diễn ra”; không đếm ngân hàng Quiz hay số stage | APPROVED AWARD_V1 |
| A04 / P-ADMIN-AWARD | Eligible = PLAYER ban đầu, trừ Game-targeted Kick/Ban marker/intent (cả DONE/PENDING), account ban đang hiệu lực và tài khoản đã soft-delete. UNBAN/expiry không xóa restriction Game X | Chốt công thức; soft-delete và trường hợp ADMIN thật sự là PLAYER cần quyết định rõ. Đề nghị role ADMIN không tự tạo quyền thưởng, cũng không tự loại một PLAYER chỉ do role | APPROVED AWARD_V1 |
| A05 / P-ADMIN-AWARD | Nhóm eligible còn **≥2 PLAYER**; ban sau Game FINISHED nhưng trước award xét theo account ban hiệu lực. Nếu đã UNBAN/expiry và không có target X thì không tự bịa restriction X | Chốt `minEligiblePlayers` và cách xét sau Kick/Ban. Không đổi luật Game có thể tiếp tục chỉ còn ít người | APPROVED AWARD_V1 |
| A06 / P-AWARD-BASE | Có **≥1 CORRECT trong nhóm eligible**, không tính correct của người đã bị ADMIN loại | Chốt `minEligibleCorrect`; người thắng theo ranking eligible có bắt buộc tự có CORRECT không? Đề nghị **không thêm điều kiện cá nhân** ngoài comparator, theo §18.6 | APPROVED AWARD_V1 |
| A07 / P-AWARD-TIE | Qualified Game: mọi đồng hạng nhất eligible nhận **mỗi người +1 cúp**; không ép một người thắng bằng userId hoặc chia nhỏ cúp | Chốt số cúp và đồng nhận | APPROVED AWARD_V1 |
| A08 / P-ADMIN-AWARD / TIE | Award ranking dùng lại comparator V2: score DESC, totalCorrectAnswerTimeMs ASC, competition ranks 1/1/3; tính trên eligible. Giữ finalRank gốc riêng; ID chỉ xếp hiển thị các tie | Chốt cách hiển thị awardRank so với historyRank. Không sửa engine/rank History | APPROVED AWARD_V1 |
| A09 / P-AWARD-TIE | Qualified: mỗi eligible +1 officialGamesCount; cup recipient +1 officialWinsCount; người bị loại +0; NOT_QUALIFIED không tăng tử/mẫu cho ai | Chốt mẫu số, đồng vô địch đều +1 win; win rate = wins/games; zero games trả null và “Chưa xếp hạng” | APPROVED AWARD_V1 |
| A10 / P-ADMIN-AWARD | Ban/Kick trước award commit được xét; sau terminal decision không thu hồi cúp/counter. NOT_QUALIFIED đã commit không trở thành qualified sau UNBAN | Chốt tính bất biến nghiệp vụ này. Chưa thiết kế endpoint sửa/thu hồi thưởng | APPROVED AWARD_V1 |
| A11 / P-FAST | **Tay nhanh theo mode**: ≥5 correct, correct/participation ≥60%; mean correct time nhỏ nhất trong những eligible đạt ngưỡng; bằng phân số thời gian thì đồng nhận | Chốt bật/hoãn, `minCorrectPerMode`, accuracy, đồng nhận, gộp theo mode/Game. Không so thời gian giữa các mode | APPROVED AWARD_V1 |
| A12 / P-PERFECT | **Bách phát bách trúng**: ≥10 câu có quyền chơi, CORRECT=100% mẫu số; NO_ANSWER/WRONG làm không đạt. Mọi eligible đạt chuẩn đều nhận | Chốt bật/hoãn, `minParticipatedQuestions`, 100%, nhiều người cùng nhận | APPROVED AWARD_V1 |
| A13 / P-FAST / PERFECT | Danh hiệu lưu hồ sơ chỉ trong Game qualified; mẫu số = participation chụp OPEN, kể cả AFK NO_ANSWER; không tính câu chưa mở, Spectator, trận khác. Một người có thể nhận nhiều loại | Chốt đồng phạm vi với cúp, cách tính denominator và multiple awards | APPROVED AWARD_V1 |
| A14 / P-LEADERBOARD | cups DESC → win rate DESC → accuracy tổng correct/tổng participation DESC trên qualified/eligible games; bằng cả ba thì đồng hạng, userId chỉ ổn định phân trang | Zero games: win rate null/Chưa xếp hạng; không dùng tổng score làm điểm BXH | APPROVED T12 |
| A15 / P-LEADERBOARD | Public chỉ userId/displayName và thống kê đã commit; không username đăng nhập, mode breakdown hay recent History. Ẩn tài khoản đang Ban/soft-delete khỏi public profile và BXH; không sửa/xóa Award Ledger/History. Own profile theo account guard hiện có | Visibility dùng trạng thái account trong committed snapshot và thời gian Server của GET; TEMP đã hết hạn không còn bị ẩn. Không mở private-pack History qua public API | APPROVED T12 |
| A16 / P-AWARD-ACTIVATION | Chỉ Game START **sau activation policy đã ký** có snapshot policy. Không backfill 72 Game V2 COMPLETED hiện có trong DB test, không backfill dữ liệu vận hành cũ | Chốt phạm vi cutover/new-games-only và mã policy đầu tiên. Historical backfill cần lệnh/đặc tả riêng | APPROVED AWARD_V1 |
| A17 / P-AWARD-TIME | TEMP expiry xét tại `eligibilityEvaluatedAtMs` lấy từ Server sau khi khóa đủ Users; kiểm tra lại trước ghi/commit và retry nếu đi qua biên expiry. Timestamp này là thời điểm đánh giá trong TX, không phải timestamp COMMIT vật lý | Chốt định nghĩa thời gian. User lock tuyến tính hóa BAN/UNBAN, **không đóng băng đồng hồ**; nếu yêu cầu đúng timestamp COMMIT vật lý thì cần thiết kế bổ sung, chưa tuyên bố đạt | APPROVED AWARD_V1 |

## 3. Phạm vi sau MVP — không gộp ngầm vào T11

| Gate | Policy V3.4 và signoff bổ sung | Nơi xử lý |
|---|---|---|
| P-MULTITALENT | Mọi Player đạt ≥3 mode, mỗi mode ≥2 đúng và ≥50% participation; milestone cho tất cả người đạt, không chọn một người nhất | Đã duyệt riêng T13-P01/P03; AWARD_V2 new-games-only, không đổi AWARD_V1 |
| P-COMEBACK | ≥2 stage, cuối duy nhất sau stage đầu → nhất duy nhất cuối trận; cùng eligible group ở cả hai mốc, cần dữ liệu stage boundary chính xác | Đã duyệt riêng T13-P02/P03/P04; stage evidence phải qua test, thiếu giữ PENDING |
| P-KING | CANCELLED / OUT_OF_SCOPE theo quyết định người dùng; không triển khai ngưỡng/counter/badge Friendly | T17 bị hủy, không signoff hoặc tính PASS/COMPLETED; giữ danh hiệu T11/T13 |
| P-FRIENDLY / ROOM-SUMMARY / PROFILE-EXTRA | Friendly stats/reservations: CANCELLED / OUT_OF_SCOPE; room summary/profile extra vẫn ngoài phạm vi đã triển khai | T15/T16 bị hủy, không signoff; không xóa phần nền dùng chung hoặc gộp vào CUP ledger |

## 4. Approval record

Approval record: người dùng phê duyệt trực tiếp trong phiên ngày 17/10/2026, A01–A10/A13 duyệt theo phiếu, A11/A12 bật, A16/A17 duyệt new-games-only và thời điểm xét theo phiếu, policy ID AWARD_V1. Người dùng đã giao thực hiện T11 sau cập nhật signoff này; không cần xin lại approval cho phạm vi trên. A14/A15 hoãn T12, T13/T17 không triển khai; không commit/push.

Approval bổ sung T12: người dùng trả lời **“Duyệt toàn bộ phương án đề xuất A14/A15”**, sau đó xác nhận rõ ba tiêu chí/đồng hạng/userId pagination, public allowlist, ẩn Ban/soft-delete không đổi ledger/history, own account guard. Đây là lệnh triển khai T12 riêng, không phê duyệt T13 và không cho commit/push. Signoff AWARD_V1 và ngưỡng T11 không đổi.

Approval bổ sung T13 ngày 17/10/2026: người dùng duyệt cả Đa tài và Lội ngược dòng, T13-P03/P04 nguyên [phiếu T13](T13_ADVANCED_ACHIEVEMENTS_SIGNOFF.md), sau đó xác nhận đầy đủ. AWARD_V2 chỉ START mới; AWARD_V1 ACTIVE/PENDING/terminal không hồi tố; evidence thiếu giữ PENDING. Không T14, không commit/push. Không có mâu thuẫn giữa xác nhận và P03/P04.
