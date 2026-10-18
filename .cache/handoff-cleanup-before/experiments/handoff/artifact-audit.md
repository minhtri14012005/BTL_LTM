# Kiểm kê artifact Task25 — 10/10/2026

Đã đọc nội dung docs, README, task nguồn và scripts có liên quan; kiểm tra Git/reference trước cleanup. Không xóa theo tuổi/tên. Source/backend, frontend, migration, cấu hình và userdata giữ nguyên. Bảng chi tiết evidence/output với references được ghi ở artifact-audit.csv, không liệt kê từng class sản phẩm. Git M=modified, tracked=đã theo dõi, ??=untracked, ignored=Git ignore; working tree Tasks16–24 chưa commit không được gán hết choTask25.

| Đường dẫn / nhóm | Mục đích / nội dung thật | Git | Reference / quyết định |
|---|---|---|---|
| README.md | Hướng dẫn cài/chạy/DB/LAN/ownership/trace/demo và links bàn giao | M | Báo cáo/guide/checklist dẫn; giữ README chính |
| docs/codebase-guide.md | Giải thích folderfeature, source/static/test/resources và flow | tracked→M | README/report dẫn; cập nhật7mode, giữ |
| docs/course-requirements-checklist.md | Mapping mục tiêu dẫn lại→module→evidence, chưa courseoriginal | tracked→M | README/report/team dẫn; giữ, không tick hồ sơ chưa xác minh |
| docs/database-schema.md | ERD/cột/constraints nền v1 và additiveV3–V7/13bảng | tracked→M | README/report/sourceDDL; không xóa phần v1, ghi provenance |
| docs/final-report.md | Báo cáo đầy đủ bản 7mode, setup/Results/diagrams/discussion/reference | tracked→M | README/coursechecklist; giữ để bảo vệ/bàn giao |
| docs/gameplay-rules.md | Bản trích Overview v1, nguồn bảngQuiz/effect | tracked | TASKS/decisions/contracts/report dẫn; giữ nguồn, không gọi là toàn bộv2 |
| docs/implementation-decisions.md | Lịch sử freeze/nguồn các lựa chọn và quy tắc versioning | M | Mọi docs/contracts/task dẫn; giữ, chỉ thêm quyết định experiment mới |
| docs/project-status.md | Evidence/trạng thái mỗi task, có links target lịch sử | M | README/checklist; giữ, không mất provenance/error đã sửa |
| docs/rest-api-contract.md, websocket-contract.md | Canonical REST/WS implemented, DTO/permissions/message/nullability | M từTask trước | Source/UI/tests/readme/report; giữ, Task25 không đổi behavior/contract |
| docs/team-contributions.md | Nhóm4blank, planned balanced ownership và actual blank | tracked→M | README/report/checklist; giữ, sửa mô tả Git cũ sai |
| docs/assets/gameplay-2026-10-07/*.png |5screenshot thật đợtDecision7s/RESULT1.5s trước7mode | tracked | Status/README/báo cáo lịch sử; giữ theo provenance |
| TASKS.md | Task2.5/quy tắc và Overview nguyên nguồn | tracked | Toàn bộ luật/docs; giữ |
| TASKS_MO_RONG_7_CHE_DO.md, KHAO_SAT_MO_RONG_7_CHE_DO.md | Prompt16–25 và khảo sát lịch sử trước triển khai | ?? | Decisions/tasknguồn; giữ. Khoảng cách trong khảo sát không còn là hiện trạng code |
| 1.txt | Chỉ danh sách16..23; ghi chú người dùng | M trướcTask25 | Khôngreference kỹ thuật thấy; chưa rõ có cần lưu cá nhân, giữ |
| 1.sql | Câu CREATE DATABASE quizz đơn giản, không seed/migration canonical | tracked | Khôngreference; prepare-database.sql là hướng dẫn chính. Giữ vì file người dùng, không execute |
| experiments/experiment-protocol.md | Protocol mới và phần06/10 có label lịch sử | tracked→M | README/report/scripts; giữ |
| experiments/results/2026-10-06-loopback/* | RawCSV/wire/log/env/config/seed/hash, summary/plotsv1Decision5s | tracked | Report Results cũ vàmanifestTask15; **giữ toàn bộ**, không ghép vàov2 |
| experiments/results/2026-10-10-multimode-loopback/* | Raw mới và bảng/plots sinh từCSV,32Gameactual | ?? | Report/README/protocol/index; giữ, không regenerate raw |
| experiments/results/2026-10-10-multimode-pilot-initializing/* | Pilot gửiContinue trước INTRO_STARTED, bịreject | ?? | Environment/protocol/report phân biệt pilot; giữ reproduction, không đếm trial |
| experiments/handoff/* (Task15 files/XML/json/png/log) | Raw/provenance regression/MySQL/browser lịch sử 06/10 | tracked | Existingmanifest/hash/report lịch sử; giữ, không ghi đè collector |
| experiments/handoff/2026-10-10-seven-modes/* | Bản sao evidenceTask21–24 vàrunTask25/hash/archive-map | ?? | Report/current index; giữ originals vàcopies tương đương |
| scripts/*.py, *.ps1, *.sql | Build/clienttests/fixture/experiment/DB/read-onlyaudit/launch | tracked và?? | README/pom/tests/module imports, kể cả harness cũ được7mode import; giữ, không xóa script cũ chỉ vì tênTask cũ |
| src/test/resources/quiz/* | SyntheticMP4 valid/invalid/containerfixtures | ?? | SongIT/browser/generator; giữ, không filemedia người dùng |
| pom.xml, .mvn, mvnw/mvnw.cmd, .gitattributes/.gitignore, config/*.example | Dependency/build/wrapper/sampleconfig/ignore | tracked/M | Build/run/scripts; giữ |
| config/application-local.properties, data/quiz-images, data/quiz-videos | Credential và runtime media người dùng | ignored | Springimport/stores/DBreferences; giữ tại máy, không đưa credential/userdata vào bundle |
| .cache | Maven/dependency offline, tái tải được khi có Internet | ignored | Wrapper/offlinecommands; giữ trong workspace để chạy offline, không nộp |
| .idea/.vscode | Cấu hìnhIDE local | ignored | Không cầnbuild; giữ lựa chọn người dùng, không xóa |
| .git | Repository/history | Gitmetadata | Cóbasecommit; giữ, không commit/push Task25 |
| target/classes,test-classes,client,maven-*/generated-*,surefire/failsafe,JAR | Output build có thể sinh lại | ignored | mvnpackage/verify +build-client; giữ artifactdemo hiện tại, không clean cảtarget |
| target/task*-evidence.json/log/sql/tsv/png vàupdate-* | Nhiều evidenceTask trước, status/manifests trích dẫn | ignored | CSVreferenceaudit vàhistoricalstatus; giữ, không xóa toàn bộ theo tuổi |
| target/*chrome*,tomcat.*,hsperfdata*,*-test-images/video | Profile/temp/testmedia của lượt cũ, một số chưa xác minh process/DBref | ignored | Không đọc cookie/profile content; chưa đủ căn cứ xóa, giữ. Đường dẫn cụ thể ở inventory nhóm vàfilesystem |
| scripts/__pycache__/*.pyc | Bytecode Python sinh lại, không source/evidence | tracked | Import dùng.py, khôngreference report/hash cần bảo toàn; xóa6file còn tồn tại, ignore tái sinh |
| target/competitive-quiz-0.0.1-SNAPSHOT.jar.original | JAR trước Springrepackage, không artifactdemo | ignored | pomrepackage sinh; nếu khôngreference ngoài logbuild thì xóa, tạo lại bằngmvnpackage |

## File cũ vẫn cần giữ

Overview/gameplay/decisions/sourceDDL và Historyv1; CSV/log/env/hash v1; Task15handoff/JUnit; ảnh07/10; Task21–24manifests vàsourcehash cho mediaerror/races không đổi. Các tài liệu lịch sử có từ PLANNED/chưaimplemented hoặc số11bảng: provenance task/ngày giải thích, phần hiện tại nằmcanonical/source/schemaadditive; không kết luận là code chưa có. Finalreport đã cập nhật tại chỗ, không tạo nhiều bản nháp report cạnh tranh.

## Chưa đủ căn cứ

1.txt/1.sql là ghi chú người dùng, giữ. Không xóa media hoặc DB, profileChrome cũ chưa xác minh đã dừng, cacheddependency cầnoffline, targetlog đượcstatus/manifests dùng. Không thấycourseoriginal; không invent file nộp bắt buộc. Audit references là căn cứ hỗ trợ, absence của textreference không đủ chứng minh userdata/file lạ an toàn đểxóa.

## Cleanup đã thực hiện

Đã xóa6bytecode: scripts/__pycache__/browser-cdp.cpython-312.pyc, collect-handoff-evidence.cpython-312.pyc, experiment-client.cpython-312.pyc, plot-experiment.cpython-312.pyc, summarize-experiment.cpython-312.pyc, test-client.cpython-312.pyc. Python import tự sinh lại nếu không dùng -B; source.py còn nguyên, thêm ignore__pycache__/*.pyc. test-game-client.pyc đã bị xóa từtask trước, không tínhlàTask25xóa.

Đã xóa target/competitive-quiz-0.0.1-SNAPSHOT.jar.original (Maven package/repackage tạo lại); target/handoff25-chrome.log và profile handoff25-owned-chrome-774f203d-771d-411e-99e5-82df2224389e sau Browser.close/không cònlistener9224 (scriptChrome tạo lại profile mới). Chỉ generateddiagnostic/profile, khôngrawmeasurement/evidence. Khôngxóa tài liệu cũ đượcviện dẫn/SQL/media/DB/source; mọi đườngdẫn cleanup đãverifyunderworkspace/target.

Bộ bàn giao gọn: source/tests/fixtures +pom/wrapper/migrations +sample/scripts +README/report/schema/guide/luật/contracts/decisions/checklist/team +raw experiment/evidence cóhash. Không cầncache/IDE/profile/credential. Chưa tạozip vìformat/tênfileSubmission chưa có; có thểreview trực tiếp các folder này.
