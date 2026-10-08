# Experimental protocol và tái lập

Task15,06/10/2026. Phạm vi: **reliability của response replay và full reconnect snapshot**, và latency dưới burst thao tác trên một Room. Tải3/5/10/20 và30 trial/3 round là kế hoạch task, chưa phải yêu cầu thầy đã đối chiếu. Nguồn gameplay/schema/stack không đổi. Kết quả hiện có là **same-machine loopback**, không LAN nhiều thiết bị.

## Hệ thống và chạy lại

Java21/SpringBoot3.5.16, MySQL8.0.45, Python3.12.10;4 game workers, pool JDBC5. Setup/credential theo [README ứng dụng](../README.md). Tạo schema riêng bằng scripts/prepare-experiment-database.sql; dừng Server8080 trước harness. Mỗi mode một Server/một DB, chạy tuần tự. Không chạy hai instance cùng runtime DB.

```powershell
# MySQL client trong PATH, password nhập tại prompt
mysql -h 127.0.0.1 -u root -p -e 'source scripts/prepare-experiment-database.sql'
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run-experiment.ps1 -OutputDirectory experiments/results/my-new-run
python scripts/summarize-experiment.py experiments/results/my-new-run
```

Output mới bắt buộc, script không ghi đè CSV run đã có. Compile test classpath qua test-compile/dependency:build-classpath; main **src/test/java/.../realtime/session/ExperimentServer.java** chỉ được bật với profile mysql,experiment và explicit mode. Production JAR không chứa class này; demo không có property baseline hoặc endpoint mới. Script khởi động process Java hidden, readiness MySQL thật, rồi cleanup đúng PID/command của nó. Dữ liệu fixtures giữ trong schema experiment để kiểm tra durable History; không xóa history/quizz. Run fail vẫn giữ CSV/log đã flush; không biến partial thành success.

Pilot nhỏ (không dùng trong Results): script -Mode both -Loads3 -Rounds1 -ReliabilityGames1, output target/task15-pilot. Run chính thức: [2026-10-06-loopback](results/2026-10-06-loopback), default10 reliability games +12 load games/mode. Chỉ rerun khi có thay đổi/concern; summarize đọc CSV, không chạy workload lại.

## Baseline/proposed

| Nội dung | Baseline thử nghiệm | Proposed |
|---|---|---|
| Account/session/queue/gameplay/DB | Code thật giống nhau,1 Account mỗi Client; Clock/timer thật | Giống baseline |
| Spin/Answer duplicate guard | GameLifecycle/DB vẫn enforce1 Spin/câu,1 valid Answer/câu | Giống baseline |
| Committed receipt | Observer test erases receipts/counts và trả semaphore budget **sau commit, trước giao future cho adapter**; không giữ cache giữa commands. Lookup retry vì vậy miss, business guard trả ERROR | Cache production giữ response gốc, retry cùng UUID replay trước phase validation |
| Socket reconnect | Mở lại authenticated socket, SUBSCRIBE_ROOM vào roster; **không gửi RECONNECT/full Game snapshot** | Mở lại socket, RECONNECT Game, apply full private snapshot |
| Lỗi/phase/Spin | Không random lại hay bỏ guard, không inject mock DB/engine/clock | Code production |

Ablation xóa receipt dùng reflection ở **test-only**, fail rõ nếu cấu trúc runtime/cache không khớp. Vẫn có allocation/put rồi xóa, do đó không phải phiên bản tối ưu hiệu năng của “no-cache”; mục tiêu là chứng minh khác biệt khôi phục response. Không dùng số đo để tuyên bố cache luôn nhanh hơn. Chỉ receipt retention bị loại, auth/permission/Start Room cache và constraints không bị tắt. Baseline reconnect không đồng nghĩa reconnect hoàn toàn không có tác dụng: vẫn handshake/subscribe, nhận được các event tương lai; nó thiếu state đã bỏ lỡ trước khi event mới đến.

## Workload, seed và mẫu số

Mỗi mode tạo **21 Account** riêng:20 Player Client tối đa +1 Author setup (không socket/load Player, không chơi Quiz tự tạo). Players giữ cookie jars/socket riêng; Account có thể chơi lại ở round sau nhưng không đồng thời ở2 Game. Từng trial dùng command UUID riêng; retry giữ nguyên **toàn frame/UUID**. Prefix/IDs thật lưu fixtures.json, không lưu password/cookie/DB credential.

Quiz PUBLIC10 câu4 options, correct A; question snapshot random do Server; questionDuration10000ms, Decision5000ms. Uniform correctA chỉ dùng kiểm soát input load; không thay bảng scoring. **seed15062026 là seed cấu hình workload/password fixture, không seed SecureRandom Spin hay UUID/random question của Server**. Hiệu ứng Spin thực tế được lưu ACK/history/wire, không giả tái lập cùng draw. Baseline chạy trước proposed trong run hiện tại, không warmup/counterbalance/JIT isolation.

Reliability:10 Game tuần tự, mỗi Game3 Player, mỗi Player thử lostSpinACK và lostAnswerACK → **30 attempts/scenario/mode**. ACK thật nhận ở lớp harness nhưng không apply vào Client model (application-level lost delivery), oracle giữ bản gốc để so equality. Spin trước rồi Star nếu effect không HARDSHIP. Reconnect DECISION ngay sau resource commit; retry Spin cùng frame; đợi QUESTION_START thật; gửi Answer rồi disconnect Client, chờ Server scoring→câu2; reconnect và retry ACK Answer; Cancel rồi retry Answer sau FINISHED. Trial loss mô phỏng delivery trên Client, không tắt Wi-Fi hay packet-loss emulation.

Load: mỗi mức3/5/10/20 Player, **3 round**, mỗi round1 Room/Game với barrier cho tất cả USE_SPIN, rồi barrier ANSWER câu1. Mẫu latency/mode =2×N×3:18/30/60/120 (**228 total**). Round Cancel/History/Room close sau verify, không chạy10 câu để load; smoke browser riêng kiểm chứng trận hoàn chỉnh. **22 Room/Game/mode,44 toàn run; chỉ1 Room active tại một thời điểm**. Không suy ra load multi-room; evidence multi-room isolation Task13 là correctness với2 Room/clock/latch, khác experiment tải này.

## Metrics và clock

| Metric / CSV field | Bắt đầu → kết thúc; đơn vị và denominator |
|---|---|
| server processing duration_ms | Java System.nanoTime tại entry GameRuntime.command → completion future sau service/transaction/runtime/cache (kể cả queue wait). Không gồm parse/auth trước runtime hoặc socket send/client receive; không phải chỉ DB execution. RECONNECT server span entry→futurecomplete, có delivery callback. Thu đủ SUCCESS và REJECTED_OR_ERROR, load table chọn SUCCESS |
| response_ms | Python perf_counter_ns trước send frame → receive/parse ACK/ERROR tương quan UUID; ms. Gồm client/socket/network +Server. Clock trên một process, không trừ timestamp giữa máy |
| original_recovered | Deep equality **toàn JSON receipt** retry và ACK gốc gồm revision/time/payload; lostSpin30, lostAnswer30, FINISHED30/mode. ERROR guard baseline là expected rejection, không system error |
| snapshot_response_ms | Proposed: gửi RECONNECT → nhận/parse ACK full snapshot; ms,30 DECISION +30 afterAnswer. Baseline không snapshot nên để rỗng, không gán0 |
| client_apply_resync_ms | Bắt đầu mở socket mới → AUTH_READY→RECONNECT ACK→deep-copy snapshot vào **harness Client model** hoàn tất; ms. Không chỉ Server sent, không phải thời gian render UI browser. Baseline thiếu full snapshot, để rỗng |
| state_mismatch | Sau model apply/resubscription so với REST oracle, đúng status/phase/questionIndex/revision/player/members. REST read oracle không feed baseline model.30 attempts/scenario/mode; không so serverTime biến thiên |
| duplicate_side_effects | Retry ACK mới khác original hoặc extra Answer rows; durable History cardinality/unique user và remainingSpins0 checked mỗi Game. Replay ACK gốc không tính duplicate mutation |
| invariant_violation | Terminal FINISHED, abnormal không officialWinner, RoomWAITING, câu1 đúngN unique Answers và remainingSpins0;22 durable Game checks/mode. MySQL aggregate kiểm chứng riêng UAG0/ACTIVE0 và pool/answer FK |

Durations đơn vị ms từ monotonic clock. Deadline nội bộ production vẫn ServerClock monotonic; epoch chỉ hiển thị. Client không đóng/chấm câu. server/user_id/game_id/request_id đủ join tương quan; code/outcome tách business rejection với system failure. Harness abort nếu SERVICE_UNAVAILABLE/AUTH_UNAVAILABLE/receipt limit; failed run không vào Results pass. CSV flush mỗi row, log mỗi response; observer ghi đồng bộ nên có overhead ảnh hưởng queue wait, cả modes dùng cùng instrumentation.

p50/p95 **nearest rank** trên các mẫu thực tế; không percentile interpolation hoặc bỏ outlier. Mẫu số success là attempts đúng scenario, không tất cả packet. Load merge Spin+Answer của3 round, không gọi throughput benchmark/sustained capacity; không suy ra giới hạn deadline/Internet. Packet/network outages, phase SCORING/replacement concurrency được test hệ thống, không phải scenarios đo latency này.

## Evidence và tính tái lập

Run directory gồm environment.json (CPU/RAM/OS/version/network/config), mode-client.csv, mode-server.csv, mode-wire.jsonl (command/response, không secrets), fixtures.json, client/server logs, summary.json, results.md, sha256.json. Tables chỉ từ summarize-experiment.py. Pilot riêng không trộn. Handoff lưu production smoke/report/images/build/SQL và evidence Task13/14 còn đúng; JUnit full chi tiết ở target nếu cần kiểm toán. Sources/hash của harness ghi trong manifest bàn giao.

Máy đã đo: Intel i5-13500HX14 cores/20 threads, RAM16886128640 bytes (~15.73GiB), Windows11 Home10.0.26200. Server/MySQL/Python và browser smoke đều cùng máy, network127.0.0.1 Windows TCP stack. Không CPU pinning, không đo usage/sample RAM runtime, không claim Wi-Fi/Internet latency hoặc causal performance improvement; background CPU/JIT/cache/data growth/mode order có thể ảnh hưởng. Kết quả30/30 không chứng minh không lỗi ngoài tập trial.

## LAN và máy mới

Chưa có nhiều thiết bị/clean-machine evidence. Chạy lại bằng source/mẫu config; không phải copy credential hoặc whole target/profile sang máy khác. Protocol:

1. Máy Server cài JDK21/MySQL8, setup DB/config theo README, build/start production JAR; bind0.0.0.0, origin IP LAN chính xác. Xác nhận readyUP, không profileexperiment trong demo.
2.3–4 Client máy thật cùng Wi-Fi mở http://IP-Server:8080, mỗi Account/browser riêng. Một scenario Host Participate3Player; một AuthorHost Spectator+3Player. Ghi topology, version, số máy/Client/Room thực tế.
3. Chạy10 câu; Spin→optionalStar, Star riêng, Answer/NO_ANSWER; tắt/bật Wi-Fi một Player, reconnect giữ resource; socket replacement hai tab; Lost ACK retry theo test tooling; Final/History; trận riêng Host eliminatedCancel/Cancelunscored. Lưu screenshot/log/event/HistoryIDs và mismatch.
4. Nếu đo baseline LAN, khởi động **test-only** main trên Server với classpath do run-experiment tạo, --server.address=0.0.0.0 --ALLOWED_ORIGINS=http://IP-Server:8080; Client máy khác `python scripts/experiment-client.py --origin http://IP-Server:8080 --mode proposed --output experiments/results/lan-new-run` (từng baseline/proposed một process). Chỉnh topology/env/report placement bằng dữ kiện LAN thật; script run-experiment hiện orchestrationlocalhost, không ghi LAN pass tự động.
5. Phân công mỗi TV một segment/evidence theo team doc; bảng máy mới/LAN để trống tới lúc chạy. Nếu không đủ máy ghi PARTIAL, không dùng nhiều contexts trên cùng máy để thay evidence LAN.

| Mục chờ | Máy/OS/version | Người thực hiện | Command/scenario | Evidence/result |
|---|---|---|---|---|
| Build/run máy mới | | | | |
| LAN3–4 thiết bị thật | | | | |
| Baseline/proposed LAN nếu đo | | | | |

### Phạm vi evidence sau cập nhật07/10/2026

CSV/log/config/hash và ảnh bàn giao06/10/2026 giữ nguyên: Decision5000ms, RESULT chuyển tiếp ngay. Production mới Decision7000ms/RESULT1500ms; chạy lại scenario lifecycle/reconnect/UI trong đợt cập nhật, không tái gán benchmark cũ cho phiên bản mới. Chưa đo lại benchmark/LAN nhiều máy; evidence mới xem docs/project-status.md.
