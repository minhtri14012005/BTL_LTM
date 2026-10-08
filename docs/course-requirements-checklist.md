# Course requirements và checklist bàn giao

Ngày cập nhật **06/10/2026, Asia/Bangkok — Task15 PARTIAL**. **Chưa đối chiếu trực tiếp tài liệu môn gốc, không xác nhận đã đạt môn/hồ sơ đủ nộp.**

Nguồn đã đọc: Overview/TASKS (đặc biệt§14–19), gameplay/decisions/contracts, source và evidence Task11A/11B/12/13/14/15. Chưa có Topics/Topic2, Instruction, Submission, mẫu README môn; Proposal/Technical Design V2 cũng thiếu. `1.txt` và pasted prompt chỉ là yêu cầu task. README root là README ứng dụng tự tạo, report Markdown là fallback dự án; tên/format/USB/Drive/deadline môn chưa xác minh. Các nhãn Topic2/Instruction trong Overview là nguồn dẫn lại, không bằng chứng đã đọc nguyên bản.

## Requirement → module → evidence

| Yêu cầu Overview dẫn lại | Module/implementation | Evidence thật / giới hạn |
|---|---|---|
| Topic2: game Client/Server,≥3 Player | Native Web UI→REST/rawWS→một Spring Server/MySQL | Task15 Chrome4 contexts,3 Player hoàn thành10 câu +Host Spectator3 Player; chưa LAN nhiều máy |
| Server User/Room/Host | auth/user/room, RoomBoundary, GAME_MEMBER roster | AuthNetwork/RoomNetwork/GameLifecycle; Host Room permission, không global role |
| Shared state/logic authoritative | game/engine, Lifecycle/Transactions, session/timer | ScoreEngine113/Ranking6/RulesSnapshot16, SessionQueue21; bảng điểm/Recovery/effect câu sau/elimination theo nguồn |
| Action/permission/deadline/resource | strict parser, auth/generation, replay/guards, constraints | Task13 full +Task14 auth-race fixes +Task15 selected regression73; business reject khác system failure |
| Push Question/Result/Leaderboard/End | lifecycle domain event→socket registry→UI | Real rawWS/MySQL integration +production browser32/9; runtime/cache/broadcast sau commit |
| Connect/disconnect/reconnect | registry generation, actor snapshot, Client revision/model | Phase/scope/nullability tests +Task15 matching snapshot30/30 mỗi2 phase đo; không crash recovery |
| Scoring/ranking/history/Cancel | atomicGameTransactions/HistoryDTO/schema | MySqlSchema12/HistoryCancel12/Isolation2; Accepted-Unscored, eliminated history, Host eliminatedCancel/noabnormalWinner |
| UI Account/Quiz/Room/Game/Final/History | native modules/app/router | Task11A/12 evidence +Task15 smoke9 groups; no mandatory product TODO/button chưa nối tìm thấy qua scope scan/flows |
| JSON action / auth | API cookie/CSRF +rawWSOrigin/capturedUUID | Task15 actual ACKloss retry cùngframe/UUID; Server không tin Client score/userId/timestamp |
| UI nhận ACK/snapshot không tự chấm | game-state/game/history |32 frontend tests +smoke, old revision guard/equal-revision events, countdown display only |
| Instruction§3–6: protocol/concurrency/error | canonicalREST/WS, ingress/queue/cache, finitefailurepolicy | Clock/latch deadline−1/exact/+1, Start same/crossRoom, stale timer, concurrent duplicate, Cancel/scoring, DB rollback/startup,2-Room isolation; MySQL thật |
| Instruction§7–9: contribution/experiment | original response replay/fullsnapshot vsablation | Actual CSV/env/seed/config:30 trial×5scenario×2mode;3round/mức3/5/10/20;44officialGame/Room,1concurrentRoom. Không LAN/Internet, không suy latency từ unit test |
| Instruction§8/§13: report/README | README install/DB/config/run/test/ownership/trace; report đầy đủ diagrams/setup/Results/Discussion/references | Có Markdown/PNG/SVG và raw evidence. Chưa template/format môn gốc |
| Instruction§2/§11: thành viên/AI/contribution | team-contributions.md và code flow cần học | Nhóm4 theo user, planned≈25% mỗi người, bảng actual trống. Chưa xác nhận cá nhân/AI policy môn, không tự gán evidence |
| Compilatio | Chưa kết quả/quy định ngưỡng gốc | Chưa kiểm tra; không tick pass hoặc đoán tỷ lệ |
| Submission tên/format/kênh/deadline | Overview chỉ dẫn lại; gốc chưa có | Chưa xác minh USB/Drive/tênfile/PDF/deadline; Markdown hiện tại phục vụ review |

## Kiểm tra bàn giao kỹ thuật đã chạy

| Mục | Kết quả |
|---|---|
| Mẫu config và MySQL | Copy mẫu byte-identical, credential environment không trong bộ nộp; fresh schema FlywayV1/V2, production readinessUP/MySQL8.0.45 |
| Build/affected regression Task15 | BUILD SUCCESS,47 unit+26 MySQL/HTTP/rawWSIT,0failure/error/skip; ExperimentServer không trong JAR |
| Frontend build/browser |11 modules/13 assets; Chrome15432 frontend tests/9 real flow groups,4 independent cookies; completed10 câu và2 Cancel game riêng |
| Experiment official |22 Game/mode;30 trial/scenario,3round/load; proposed30/30originalrecovery/modelmatching, baseline0/30;0duplicate/0invariant qua44Gamechecks |
| Durable verification |0ACTIVE/UAG/duplicate Answer/cross-game Answer/pool inconsistency/invalidunscored trong experiment schema;51totalGame gồm4pilot+44official+3browser |
| Provenance/security | Historical Task13/14 tách current, source/JAR/harness/artefact hashes; local DB password không trong evidence, không commit tạo contribution |

[Handoff index](../experiments/handoff/evidence-index.md), [results/CSV](../experiments/results/2026-10-06-loopback/results.md), [protocol](../experiments/experiment-protocol.md), [final report](final-report.md), [team](team-contributions.md). Không cộng73 current với316/124 historical có test trùng. Same-machine i5-13500HX/RAM~15.73GiB/TCPloopback, không nhận là demoWi-Fi4 máy.20Client proposed p95 cao hơn baseline nên không claim luôn nhanh hơn.

## Hồ sơ còn thiếu — không thay bằng phân công dự kiến

- [ ] Topics/Topic2/Instruction/Submission/mẫu README môn nguyên bản, đối chiếu từng requirement/format/tênfile/kênh/deadline.
- [ ] Họ tên/MSSV/lớp4 thành viên — ô trống trong README/report/team, không lấy từ máy/account/Git.
- [ ] Nhóm xác nhận phân công dự kiến; bổ sung công việc thực tế/file/evidence/test/report cho mỗi người, tác giả/review xác minh.
- [ ] Kết quả Compilatio và điều kiện chấp nhận theo môn; chưa chạy, không tuyên bố đạt.
- [ ] Build/install/run từ mẫu trên máy mới; phiếu environment/command/evidence trong protocol.
- [ ] Demo LAN3–4 thiết bị thật/ Wi-Fi và evidence topology/disconnect; hiện chỉ cùng máy.
- [ ] Final report/submission định dạng theo Instruction; kiểm tra refs/AI policy môn nếu có.
- [ ] Kiểm tra bundle không credential/.cache/browser profile/data cá nhân; hashes hiện tại có sẵn, format bundle chờ môn.

Thiếu hồ sơ/thiết bị không chặn kỹ thuật độc lập nhưng chặn tuyên bố DONE toànTask15/bàn giao môn đầy đủ. Lệnh/kịch bản chạy lại ở README/protocol/handoff. Dừng review, không commit/push.

## Cập nhật yêu cầu người dùng — 07/10/2026

Decision7000ms/RESULT1500ms và gameplay UI mới là cập nhật từ người dùng, không tự coi là yêu cầu môn gốc. Room migration/snapshot cũ, timer/reconnect/terminal được kiểm tra trên MySQL/rawWS bằng GamePresentationIT; UI nhiều Account/ảnh mới và kết quả chính xác xem project-status. Các giới hạn hồ sơ môn/LAN/contribution phía trên giữ nguyên; evidence06/10/2026 vẫn thuộc bản cũ.
