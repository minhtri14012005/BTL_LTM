# Checklist yêu cầu môn và bàn giao

Task25,10/10/2026 — **PARTIAL**. Không thấy Topics/Topic2, Instruction, Submission hoặc README mẫu môn nguyên bản sau tìm file trong project. Proposal/Technical Design V2 cũng không thấy. README root là hướng dẫn ứng dụng; report Markdown là cấu trúc dự án thông thường, chưa xác nhận filename/format/channel/deadline môn. Prompt/Overview dẫn lại yêu cầu, không bằng chứng đã đọc nguyên bản.

## Mục tiêu dẫn lại → module → evidence

| Mục tiêu cần bảo toàn | Có ở đâu | Evidence thật / giới hạn |
|---|---|---|
| Game Client/Server, nhiều Client và shared state | Browser REST/rawWS, một Spring Server/MySQL; Room roster và Game actor | Task24/25 browser4 cookie contexts,3 Player +Author Host Spectator; không phải4 máy LAN |
| Protocol/message rõ | Canonical REST/WS, strict parser/DTO, auth/CSRF/Origin | MySQL/REST/WS IT và UI thật; Answer ACK không correctness, no fake endpoint |
| Concurrency / deadline / isolation | RoomBoundary, SessionQueue/ingress, monotonic clock, UAG/transaction | Task24 full149IT +302unit; clock/latch deadline/races/stale timer; GameIsolationIT nhiềuRoom, không suy thành load benchmark |
| Duplicate / disconnect / replacement / timeout | Replay fingerprint, connection generation, private phase snapshot, wait-set | Task24 retry FINISHED/media/hint/video/offline; Task25 CSV so baseline/proposed, metric resync đến harness model apply |
| DB rollback / terminal / startup cleanup | GameTransactions/Lifecycle/StartupCleanup, finite retry | MySQL thật Task24 HistoryCancel/Clues/Lifecycle; rollback không cache/broadcast ACCEPT, không H2 |
| Author/privacy / History | QuestionBankService/media stores, immutable snapshots, versioned History DTO | Private/source author restriction, future secret/media guard, unscored nullability; browser/SQL Task24/25 |
| Novelty/contribution có chứng cứ | Replay response gốc + full reconnect snapshot; authoritative commit/order | Experiment mới30trial/scenario/mode,3round ở3/4Players; không gọi7mode tự nó novelty mạng |
| Ít nhất một nhóm thực nghiệm có số liệu thật | Raw CSV/wire/log/env/seed/config và summary/plots | [V2 run10/10](../experiments/results/2026-10-10-multimode-loopback/results.md); same-machine, một Roomconcurrent. [06/10](../experiments/results/2026-10-06-loopback/results.md) làQuizv1/Decision5s riêng |
| README / report / diagrams / khả năng demo | README install/DB/config/LAN/ownership/trace, final-report diagrams/demo/results | Hồ sơ fallback đầy đủ đểreview, chưa đối chiếu mẫu môn gốc |
| Nhóm/contribution cá nhân | team-contributions planned responsibilities/blank actual | Có4 nhãn, tên/MSSV/actual evidence trống; không bịa tác giả/tỷ lệ |

Không gán section/threshold của Instruction chưa có. Tải3/4/5/10/20 và30trial/3round là kế hoạch bộ task, không quy định thầy đã xác minh.

## Evidence kỹ thuật hiện tại

Task24 fullverify302unit+149IT và browser89/7 được lưu bản sao, không chạy/cộng trùng Task25. Task25 build302unit, sample config/MySQL fresh schema và smoke mới ghi trong [index](../experiments/handoff/evidence-index.md); [report](final-report.md) có Results/limits. Raw cũ còn nguyên, hashes/provenance riêng. [Artifact audit](../experiments/handoff/artifact-audit.md) phân loại giữ/xóa và file chưa rõ mục đích.

## Hồ sơ chưa xác minh — ô kết quả để trống

| Mục còn phải kiểm tra | Trạng thái nghiệm thu / evidence |
|---|---|
| Topics/Instruction/README mẫu môn/Submission nguyên bản | |
| Filename/format/channel/deadline bàn giao | |
| Họ tên/MSSV/lớp4 thành viên | |
| Contribution thực tế +evidence +review cá nhân | |
| Compilatio và ngưỡng theo tài liệu môn | |
| Build/install/run từ mẫu trên máy mới | |
| Demo Wi-Fi/LAN3–4 thiết bị thật | |
| Backup/restore DB và media trên máy mới | |
| AI policy/references/định dạng final theo môn | |

Ô trống nghĩa chưa có evidence, không PASS. Cần kiểm tra bundle không credential/config local/.cache/browser profile/userdata ngoài phạm vi; quy cách nộp chính thức vẫn chờ nguồn môn. Thiếu này chặn DONE hồ sơ Task25, không phủ nhận các test/measurement kỹ thuật đã chạy.
