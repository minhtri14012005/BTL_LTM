# Handoff evidence — Task15

**06/10/2026, Asia/Bangkok. Kỹ thuật local PASS; hồ sơ tổng thể PARTIAL.** Thiếu môn gốc/Compilatio/nhóm-contribution/máy mới/LAN. Không có commit/push hoặc xác nhận cá nhân tự động.

## Evidence mới

| File | Kết quả thật / phạm vi |
|---|---|
| evidence.json | Manifest nguồn production/harness, JAR hash, readiness/sample config identical, current test totals, experiment counts, local credential leak check |
| task15-regression.log +11 TEST-*.xml | BUILD SUCCESS;7 unit suites47 tests +4 MySQL/network suites26 tests,0 failure/error/skip. Observer profile không ảnh hưởng normal context |
| task15-readiness.json / task15-smoke-server.log | Production JAR startup với copy sample config, credential environment, DB UP; không test-classpath/baseline. MySQL8.0.45/FlywayV1V2 validate |
| fresh-migrations.log | Pilot trên schema experiment empty: actualFlyway V1/V2 create/migrate; chỉ startup lines, không debug HTTP/password |
| task15-client-tests.json / task15-client.log | Chrome154,32 frontend tests/9 smoke groups PASS,4 Account/cookie contexts.3 Game riêng: completed10 câu, Spectator Cancel, Host eliminated Cancel. Real HTTP/rawWS/MySQL, không API mocks |
| task15-final.png / task15-history-mobile.png | Screenshot từ browser run Task15 mới; filenames gốc toolTask12 được copy sang namespace Task15. Final/History mobile thật |
| task15-mysql-evidence.txt | SELECT MySQL8.0.45, migrations1/2 success;0 ACTIVE Game/UAG/duplicate Answer/cross-game Answer/Spin pool inconsistency/invalid unscored results |
| sha256.json | Hash các file trong directory; không chứa credential local |

Read-only SQL toàn schema experiment sau tất cả run gồm4 pilot +44 measurement +3 browser Game=51:50 CANCELLED/1 COMPLETED. Không coi51 là số Game benchmark; experiment official chỉ44. Fixtures được giữ để audit History, không xóa dữ liệu quizz. Server/Chrome do scripts tạo đã dừng; không ngắt MySQL service.

## Evidence lịch sử tái sử dụng

historical-task13-test-summary.json / historical-task13-evidence.json: full224unit+92IT, các race/scoring/schema/multi-room không đổi. historical-task14-review-evidence.json: review fixes và47unit+77IT. Đây là **historic runs** với provenance, không chạy full suite lại hoặc cộng trùng vào73 current tests. Historical file có thể dẫn log ở target cũ; file/summary/hashes dùng kiểm toán source, chưa phải archive toàn JUnit316 cũ.

Code production/transport/schema/dependency/13 UI assets giữ nguyên Task14, chỉ thêm test-only experiment source/tooling/docs. Baseline không trong JAR; scan/queue/replay/command/reconnect và MySQL wiring được current regression kiểm tra. [Results](../results/2026-10-06-loopback/results.md)/CSV/env/config/wire/server spans lưu riêng; plotPNG/SVG là standalone exports từ summary.

## Lệnh tái lập

Theo [README](../../README.md) chuẩn bị DB/credential, rồi:

```powershell
$env:MAVEN_USER_HOME = Join-Path $PWD '.cache/maven-home'
.\mvnw.cmd -B --no-transfer-progress verify -Pmysql-smoke '-Dtest=CodebaseStructureTest,GameReplayCacheTest,GameCommandParserTest,AuthenticatedSocketRegistryTest,SystemWebTest,SessionQueueTest,RoomBoundaryTest' '-Dit.test=GameCommandNetworkIT,GameReconnectNetworkIT,GameStartAuthorizationIT,MySqlSmokeIT' '-Dspring.flyway.enabled=false' -Ddebug=false
python scripts/build-client.py
# Khi production Server readyUP (không chạy experiment cùng lúc):
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-client.ps1 -Gameplay -ReportName task15-client-tests.json
# Dừng production Server8080 trước experiment:
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run-experiment.ps1 -OutputDirectory experiments/results/new-run
python scripts/summarize-experiment.py experiments/results/new-run
python scripts/plot-experiment.py experiments/results/new-run # optional Matplotlib
mysql -h 127.0.0.1 -u root -p -e 'source scripts/verify-experiment.sql'
python scripts/collect-handoff-evidence.py # fixed Task15 review dataset/artifacts
```

collect-handoff-evidence.py là collector cho dataset Task15 này, không generic claim một run khác đã đạt. Build/test XML paths có thể bị Maven ghi đè khi chạy bộ khác; giữ directory này để review. File doPowerShell redirect dùngUTF16BOM, collector decode khi kiểm chứng; JSON/CSV UTF8/UTF8BOM. Các logs nằm trong folder handoff nên đã force-retain qua rule .gitignore, không credential/raw DBdump.

Còn: clean-machine install/build/run, LAN3–4 máy, Compilatio, danh tính4 thành viên và contribution có evidence, Instruction/Submission/mẫu README môn và format cuối. Bảng cần điền ở [team](../../docs/team-contributions.md), [course checklist](../../docs/course-requirements-checklist.md) và [LAN protocol](../experiment-protocol.md#lan-và-máy-mới). Không tick các mục bằng browser contexts hoặc phân công dự kiến.
