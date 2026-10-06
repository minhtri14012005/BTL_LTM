# Nhóm, phân công dự kiến và đóng góp thực tế

Nhóm có **4 thành viên**, theo yêu cầu người dùng ngày06/10/2026. Không suy ra danh tính/tác giả từ account, máy hoặc Git. Chưa có xác nhận công việc của từng cá nhân. Ô thông tin trống để nhóm điền sau.

| Nhãn dùng xuyên suốt hồ sơ | Họ tên | MSSV | Lớp |
|---|---|---|---|
| Thành viên 1 | | | |
| Thành viên 2 | | | |
| Thành viên 3 | | | |
| Thành viên 4 | | | |

## Phân công dự kiến — cần nhóm xác nhận

Mục tiêu gần **25% khối lượng dự kiến/người**, không phải đóng góp thực tế25%. Xét code hiện có, độ khó, integration, test, đo và viết; không refactor/package lại để chia việc. Khảo sát source06/10: auth18 class/565 dòng +user2/56; quiz23/722; room22/555; game47/1812 (engine174,service688); realtime36/1455; client11 module. LOC chỉ giúp phát hiện lệch rõ, không đo công sức/tác giả. Queue/race/failure có độ khó cao hơn CRUD; tests/evidence và review liên feature cũng là phần trách nhiệm.

| Thành viên | Phần code phụ trách chính và phối hợp | UI/tích hợp | Kiểm thử | Thực nghiệm cụ thể | Phần báo cáo | Lý do khối lượng gần tương đương |
|---|---|---|---|---|---|---|
| Thành viên 1 | auth/user; session/CSRF/handshake; AuthenticatedSocketRegistry replacement/generation; CommandFingerprint/GameReplayCache và strict Game parser. Phối hợp TV4: command callback/auth fence/replay; TV2: media access policy | account/api/transport, logout/expiry/replaced; wire ACK/error/receipt và identity. Không nhận toàn bộ gameplay UI | AuthNetworkIT, registry/permission/replay/parser; queued Logout/old socket và cùng UUID khác payload. Review phần TV4 | Java ExperimentServer observer/receipt ablation, đối chiếu original-response recovery30 trials; lưu mode/config/hash. Phụ trách1 vòng demo auth/replacement | Authentication/transport/security, idempotency, sơ đồ handshake; viết interpretation replay và review lifecycle của TV4 | Security +receipt/races bù ít CRUD; nhận instrumentation baseline từ TV4; integration, evidence và review có phần riêng |
| Thành viên 2 | quiz CRUD/question/visibility +room/membership/config; RoomBoundary/RoomOperations và Waiting adapter. Start room guard/admission ở GameOperations và GameTransactions.start phối hợp TV4; không sở hữu lifecycle toàn trận | quizzes/rooms/Waiting, author/owner/public/private, config Start; roster3–20 client | QuizNetworkIT/RoomNetworkIT/RoomBoundary, author/Start roster và cross-room uniqueness phối hợp TV4 | Python harness HTTP fixtures/Account riêng, setup Room/load3/5/10/20 +3 rounds; môi trường/DB/Flyway evidence;1 vòng demo Waiting/Start | Requirements, ownership, DB/ERD, install/config/README và phần load setup; review scoring/History TV3 | Nhiều CRUD/UI hơn nên không gánh timer/reconnect hay toàn bộ media filesystem; nhận Start guard vì sát Room, chia harness theo function boundary |
| Thành viên 3 | game/engine scoring/ranking +GameTransactions.score/final standings, GameHistoryService/DTO và GameProjection result; **QuizImageStore blob/file bất biến** và reference lịch sử (API/Owner policy TV2). Game entities/repositories/constraints trong phần history/scoring phối hợp TV4 | resultPanel/finalSummary/standings trong game.js +history.js; Cancel/History error/nullability, Host eliminated quyền lấy policy TV1 | ScoreEngine/Ranking/rules, MySqlSchemaIT, GameHistoryCancelIT; Cancel vs scoring và Answer chưa chấm. Review TV2 | summarize-experiment.py, kiểm chứng CSV denominators/p50/p95, duplicate/resource/history invariant; đối chiếu durable MySQL và bảng Results;1 vòng demo Final/History | Gameplay/bảng điểm/effect, persistence/history, Results/Discussion/limitations; sơ đồ commit và review DB/Room TV2 | Engine thuần ít dòng nhưng113 scoring cases và nhiều luật; nhận analysis/constraints/history/media để giảm code/UI/experiment TV4 |
| Thành viên 4 | GameLifecycle, SessionQueue/Ingress/ServerClock/timer; GameRuntime action/reconnect orchestration; GameTransactions phase/resource/failure và startup cleanup. Phối hợp TV2 Start TX, TV3 score/cancel terminal, TV1 receipt/auth/generation | game-state.js và Decision/Question/reconnect render trong game.js; Snapshot ordering. Result/Final/History TV3, transport/receipt TV1 | SessionQueueTest, GameLifecycleIT/GameReconnectNetworkIT/GameIsolationIT; clock/latch, deadline/stale timer/multi-room/failure. Review TV1 | Harness disconnect/reconnect model-apply và revision oracle;30 trials/phase, phân biệt snapshot response/client apply;1 vòng demo reconnect/offline | Actor/clock/lifecycle/retry/snapshot, phase/sequence diagrams; reliability setup và review auth TV1 | Phần concurrency khó giữ scope hẹp: không gánh toàn bộ auth/network, replaycache, tất cả gameplay UI, harness, Results hay báo cáo |

Boundary các file chung: TV2 phụ trách Start/roster; TV4 phase/action/retry trong GameTransactions; TV3 score/standings/History. TV4 quản lý GameRuntime, TV1 review identity/cache. game.js: TV4 Decision/Question/Reconnect; TV3 Result/Final; TV1 receipt/replacement integration. Không chuyển class/package; người chính review change của phần phối hợp, không hai người cùng nhận toàn bộ file.

Entry point/build/config/system readiness do TV2 phụ trách chính, phối hợp TV4 startup cleanup và TV3 JPA/schema/common IdentityEntity. app.js/layout/core validation Quiz-Room do TV2; TV1 auth/API/transport; TV3 safe DOM/result/history; TV4 game permissions/state. CSS chung: mỗi người chịu view của mình, TV2 review layout/navigation. Docs canonical mỗi người viết phần protocol sở hữu, peer review như vòng dưới; không bỏ lại module system/common/UI chung không người phụ trách.

Mỗi người thực hiện kiểm tra build/run trên một máy được nhóm phân bổ, lưu OS/JDK/MySQL/browser/version/command, cùng kiểm tra mẫu config không chứa credential; hiện chưa có evidence máy mới. Demo chia4 segment như bảng, mỗi segment có người còn lại quan sát. Mỗi người viết nội dung/evidence của mình; review chéo vòng **1→4→1 và2→3→2**. Cả4 cùng duyệt README/report/contracts/Results/Compilatio/submission; không có một người gánh toàn bộ báo cáo. Nhóm có thể chỉnh sau xác nhận tải công việc, không coi phân công này là lịch sử thực hiện.

## Đóng góp thực tế — chưa xác minh ở cấp cá nhân

Các ô dưới để trống đúng yêu cầu. Chỉ điền sau có người xác nhận và evidence tương ứng. Không bịa commit/PR/số giờ/tỷ lệ; project hiện không có Git repository, không tạo commit để làm chứng cứ.

| Thành viên | Công việc thực tế | File/module | Commit/PR hoặc evidence | Kiểm thử/demo | Phần báo cáo |
|---|---|---|---|---|---|
| Thành viên 1 | | | | | |
| Thành viên 2 | | | | | |
| Thành viên 3 | | | | | |
| Thành viên 4 | | | | | |

## Evidence kỹ thuật cấp project — không gán cho cá nhân

| Evidence | Nội dung đã kiểm chứng | Nơi lưu |
|---|---|---|
| Task13 full suite |224 unit/92 real MySQL/network integration,0 fail/error/skip; race controlled clock/latch và multi-room | handoff historical evidence +project-status Task13 |
| Task14 review/regression |3 findings có reproduction,47 unit/77 integration sau sửa; auth/logout boundary và broadcast error đúng | handoff historical evidence +project-status Task14 |
| Task15 browser | Report thực tế riêng cho smoke trên production JAR,4 cookie contexts; không suy ra LAN4 máy | experiments/handoff (kết quả ghi khi chạy) |
| Task15 baseline/proposed | Account riêng/Client, CSV/raw wire/server latency/env/seed/config, tables sinh từ CSV | experiments/results/2026-10-06-loopback |

Contribution môn cần bổ sung: thông tin nhóm, xác nhận phân công, công việc thực tế/mapping evidence, phần viết của từng người, peer review, máy mới và demo LAN. Chưa có tác giả nào được xác minh từ các evidence cấp project trên.
