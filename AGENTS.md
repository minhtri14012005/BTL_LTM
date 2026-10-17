## Quy tắc tối ưu tài liệu và token — áp dụng cho mọi task

Ưu tiên cao nhất: triển khai code đúng yêu cầu, giữ nguyên các nghiệp vụ đã chốt và kiểm chứng bằng test. Không dành thời gian hoặc token để tạo tài liệu không cần thiết.

1. Trước mỗi task, chỉ đọc những phần tài liệu và source code thực sự liên quan. Không đọc toàn bộ docs hoặc toàn bộ codebase nếu không cần thiết.
2. Không tự động tạo báo cáo Markdown dài sau mỗi task, không tạo tài liệu trùng lặp, không viết lại nội dung đã có.
3. Chỉ tạo hoặc cập nhật tài liệu khi:
   - Task yêu cầu bắt buộc.
   - Có thay đổi API/WebSocket contract, database schema, migration hoặc quy tắc nghiệp vụ.
   - Có quyết định kiến trúc quan trọng mà các task tiếp theo cần biết.
   - Cần lưu bằng chứng kiểm thử hoặc acceptance gate theo yêu cầu.
4. Nếu tài liệu hiện có đã đầy đủ, giữ nguyên; không chỉnh sửa chỉ để diễn đạt lại hoặc làm đẹp.
5. Nếu bắt buộc cập nhật docs, chỉ sửa đúng mục bị ảnh hưởng, ngắn gọn, chính xác và không tạo thêm file nếu có thể dùng file hiện tại.
6. Không tự ý bỏ qua signoff, acceptance criteria, security gate hoặc tài liệu bắt buộc được chỉ định trong task. Nếu yêu cầu tài liệu quá rộng, chỉ ghi phần tối thiểu đủ để đáp ứng.
7. Ưu tiên chạy kiểm thử thực tế thay vì viết báo cáo mô tả việc kiểm thử. Không giảm phạm vi test chỉ để tiết kiệm token.
8. Sau khi hoàn thành, chỉ trả lời ngắn gọn:
   - Đã triển khai những gì.
   - Các file chính đã thay đổi.
   - Kết quả kiểm thử PASS/FAIL và số lượng test.
   - Những vấn đề còn tồn tại hoặc quyết định cần tôi xác nhận.
9. Không tự triển khai task tiếp theo, không commit hoặc push Git.

Nguyên tắc: CODE + TEST > DOCUMENTATION. Nhưng correctness, security, nghiệp vụ đã chốt và acceptance criteria luôn phải được bảo toàn.
