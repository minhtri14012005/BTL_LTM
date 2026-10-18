# Nhóm và contribution

Nhóm 4 người theo yêu cầu người dùng. Không suy ra danh tính hoặc tác giả từ tài khoản, máy hay Git. Cập nhật Task25 ngày10/10/2026; phần dưới là đề xuất trách nhiệm, không lịch sử thực hiện.

| Thành viên | Họ tên | MSSV | Lớp |
|---|---|---|---|
| 1 | | | |
| 2 | | | |
| 3 | | | |
| 4 | | | |

## Phân công dự kiến — chờ nhóm xác nhận

Mục tiêu khối lượng gần tương đương, không gán tỷ lệ25% thực tế. Chia cả code, UI, test, thực nghiệm, báo cáo và review; không đổi package để chia việc. File chung phân trách nhiệm theo method/use-case và review chéo.

| Thành viên | Code và UI dự kiến | Test / thực nghiệm | Báo cáo / demo / review |
|---|---|---|---|
| 1 | auth/user, cookie/CSRF/Origin, registry generation, parser/fingerprint/replay; account/api/transport và replacement | Auth/permission/replay/duplicate; test-only observer và original-response recovery | Auth/protocol/security; demo login/replacement/retry; review TV4 |
| 2 | quiz typed CRUD/visibility/authoring, room kế hoạch/Waiting và Start admission; Quiz/Room UI, phối hợp media access | REST/Room/author/cross-room Start; tạo Accounts và fixture load/seed/config | Requirements/README/ERD; demo 7 bộ/Room/Start; review TV3 |
| 3 | ScoreEngine/MultimodeScoreEngine/evaluators, score transaction, History/projection; Result/Final/History UI và immutable media store phối hợp TV2 | Bảng điểm/aliases/order/schema/Cancel; CSV denominator/summary/plots/SQL audit | Gameplay/Results/Discussion/limitations; demo media/Final/History; review TV2 |
| 4 | Lifecycle/ingress/queue/clock/timer, Intro/hints/reconnect/wait-set/finite failure/startup; game-state và phase UI | Deadline/ordering/rollback/isolation; reconnect đến model apply, offline/current deadline | Architecture/sequence/phase diagrams; demo reconnect/offline/Cancel; review TV1 |

GameTransactions: TV2 Start/roster, TV3 score/standings, TV4 phase/action/failure; TV1 review auth/receipt. game.js: TV4 phase/reconnect, TV3 Result/Final, TV1 receipt/replacement. CSS/layout/module chung mỗi người chịu màn phụ trách, TV2 review điều hướng. Cả4 cùng kiểm tra build/run máy mới, LAN, docs và hồ sơ môn. Trách nhiệm thực nghiệm/báo cáo có phần riêng cho từng người; không giao toàn bộ viết/đo cho một người.

## Contribution thực tế — chưa xác minh

Có Git repository, base commit và working tree chưa commit; chúng không đủ để gán công việc cá nhân. Không tạo commit/PR giả hoặc lấy phân công dự kiến làm chứng cứ.

| Thành viên | Công việc thực tế | File/module | Commit/PR/evidence | Test/demo | Phần báo cáo |
|---|---|---|---|---|---|
| 1 | | | | | |
| 2 | | | | | |
| 3 | | | | | |
| 4 | | | | | |

## Evidence cấp project

- Task24: 302 unit +149 MySQL/REST/WS IT, browser89 component/7 nhóm smoke; [archive](../experiments/handoff/2026-10-10-seven-modes/archive-map.json).
- Task25: build/smoke từ mẫu và benchmark v2; số đếm thật ở [evidence index](../experiments/handoff/evidence-index.md) và [Results mới](../experiments/results/2026-10-10-multimode-loopback/results.md).
- Task15/06-10: [Results Quiz cũ](../experiments/results/2026-10-06-loopback/results.md), giữ provenance, không xem là đo bản 7 mode.

Nhóm điền actual evidence sau xác nhận, kể cả phần viết, kiểm tra và review. Compilatio/AI policy/contribution môn chưa đối chiếu nguồn gốc.
