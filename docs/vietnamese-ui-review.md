# Giao diện ghép câu Vua Tiếng Việt

Áp dụng hướng dẫn AGENTS.md do người dùng cung cấp (tiếng Việt). Không tìm thấy AGENTS.md trên đường dẫn tổ tiên hoặc trong source của workspace. Đã đọc renderer, game state/receipt/countdown và ArrangementEvaluator trước khi sửa.

## Phạm vi thay đổi

- `src/main/resources/static/client/arrangement.js`: renderer riêng cho Vua Tiếng Việt; kéo bằng Pointer Events (chuột/cảm ứng), chạm hoặc Enter để ghép; dùng ID riêng, giữ nguyên text; undo thao tác ghép cuối, reset về thứ tự ô Server đã gửi.
- `src/main/resources/static/client/game.js`: chọn renderer theo mode, lưu bản nháp trong bộ nhớ theo người chơi/Game/câu/danh sách mảnh; khóa khi pending, ACK, snapshot đã nộp, đóng câu hoặc hết deadline. Giữ payload `Answer.itemIds`, receipt/retry/reconnect hiện có.
- `src/main/resources/static/styles.css`: ô từ và hàng đáp án cuộn ngang; CSS bổ sung chỉ áp dụng giao diện mới. Giữ nguyên phần CSS thay đổi có sẵn của người dùng.
- `scripts/test-arrangement-client.py`: bổ sung assertion renderer và smoke kéo chuột thực, từ lặp, undo/reset, bản nháp qua reconnect, không gửi Answer khi sửa nháp.
- `scripts/test-vietnamese-draft-client.py`: fixture trình duyệt riêng với transport mô phỏng để kiểm tra cảm ứng, hàng dài 320px, focus bàn phím, identity bản nháp, deadline khi snapshot Close chưa đến, double submit/ACK.

Không sửa backend/contract, chuẩn hóa đáp án, điểm, deadline, reconnect, Early Close hay gameplay sáu chế độ còn lại. Các thay đổi backend và các file khác đã có trong workspace trước tác vụ này được giữ nguyên. Không commit/push.

## Kết quả thực tế

- Build Client: PASS, 14 module / 16 file (`target/client-build.json`).
- Unit Java: 315 test, 0 failures/errors/skipped (`target/vietnamese-verify.log`). Chạy với `-DargLine=-Djava.io.tmpdir=D:/BTL_LTM/target` vì lần đầu 4 test video không dọn được thư mục tạm ngoài workspace. Bước package của lượt verify đó lỗi vì JAR đang được Server dùng; đã dừng Server riêng, package lại thành công (`target/vietnamese-package.log`), rồi khởi động bằng bản JAR riêng.
- `ArrangementLifecycleIT`: 3 test PASS với MySQL/HTTP/WebSocket thật (`target/vietnamese-lifecycle.log`). Bao gồm deadline −1/đúng hạn/+1, NO_ANSWER, reconnect, retry/concurrency/cancel và chấm điểm.
- Browser arrangement: 74 assertion Client + 6 nhóm smoke PASS (`target/task20-client-tests.json`, `target/vietnamese-browser-final.log`). Chuột thực ở 390px, từ lặp khác ID, undo nhiều bước/reset, giữ nháp/deadline qua reconnect, mất ACK rồi retry cùng request, điểm 70/60/40, lịch sử và ORDERING.
- Browser regression bảy chế độ: 93 assertion Client + 7 nhóm smoke PASS (`target/vietnamese-seven-regression-client-tests.json`, `target/vietnamese-seven-regression.log`). Server/MySQL thật, Quiz/Spin/Star, sáu màn khác, video/ảnh, reconnect, Answer retry sau FINISHED, final/history, trận không Quiz và cancel.
- Browser fixture riêng: 10 tình huống PASS (`target/vietnamese-draft-tests.json`), cảm ứng thực qua CDP và phím Enter; transport trong fixture được mô phỏng. Ảnh kiểm tra layout: `target/vietnamese-320px.png`; đã xem ảnh.
- `git diff --check`: PASS.

Lệnh kiểm tra có thể chạy lại với Chrome headless riêng trên cổng CDP 9225 và Server tại 8087:

```powershell
python scripts/build-client.py
mvn -o -B --no-transfer-progress test '-DargLine=-Djava.io.tmpdir=D:/BTL_LTM/target'
mvn -o -B --no-transfer-progress -Pmysql-smoke '-Dit.test=ArrangementLifecycleIT' '-DargLine=-Djava.io.tmpdir=D:/BTL_LTM/target' failsafe:integration-test failsafe:verify
python -X utf8 scripts/test-arrangement-client.py --origin http://127.0.0.1:8087 --port 9225 --smoke
python -X utf8 scripts/test-vietnamese-draft-client.py --origin http://127.0.0.1:8087 --port 9225
python -X utf8 scripts/test-seven-modes-client.py --origin http://127.0.0.1:8087 --port 9225 --smoke --output-prefix vietnamese-seven-review-2
```

## Giới hạn kiểm chứng

Đã kiểm tra Chrome headless, viewport 320/390px và sự kiện cảm ứng mô phỏng thiết bị. Chưa thử trên điện thoại vật lý, Safari hoặc Firefox. Bản nháp chỉ tồn tại trong bộ nhớ ứng dụng: reload toàn trang không phục hồi bản nháp; người chơi xếp lại sau khi nhận snapshot Server, không được thêm thời gian/lượt nộp. Chưa kiểm tra riêng reload toàn trang giữa câu Vua Tiếng Việt với Server thật. Plugin Browser lỗi khởi tạo trusted RPC; đã dùng bộ Chrome/CDP cục bộ của dự án.

Các fixture smoke đã tạo tài khoản/bộ câu hỏi/phòng/trận mang tiền tố `ui20_` và tiền tố regression do script sinh trong database cấu hình cục bộ. ID được lưu trong các báo cáo JSON; không xóa dữ liệu có sẵn.
