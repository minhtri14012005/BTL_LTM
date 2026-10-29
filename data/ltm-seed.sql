-- ClassQuest LTM demo data, MySQL 8. Run after setup.cmd (Flyway migrations).
-- Includes LTM subject, academic period, class, teacher/student accounts,
-- membership, 15 public question banks and 105 questions. Passwords are hashes.
-- Intended for a fresh database; refuses to overwrite existing academic data.
SET NAMES utf8mb4;
DELIMITER $$
CREATE PROCEDURE ltm_seed_preflight()
BEGIN
 IF EXISTS(SELECT 1 FROM academic_year) OR EXISTS(SELECT 1 FROM academic_subject)
    OR EXISTS(SELECT 1 FROM quiz) OR EXISTS(SELECT 1 FROM course_class)
    OR EXISTS(SELECT 1 FROM app_user WHERE id IN (84,85) OR username IN ('gv1','player1')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Import LTM seed into a fresh database after setup.cmd';
 END IF;
END$$
DELIMITER ;
CALL ltm_seed_preflight();
DROP PROCEDURE ltm_seed_preflight;
START TRANSACTION;

-- app_user
INSERT INTO `app_user` (`id`, `username`, `password_hash`, `display_name`, `created_at_ms`, `deleted_at_ms`, `role`, `ban_kind`, `banned_until_ms`, `ban_version`, `auth_epoch`, `ma_sinh_vien`) VALUES (84,'player1','$2a$10$EThGOoxLCETFITCCCHPI6uI5VpBSh2BtuWQsIS71DKE7ueWuiFNZK','Vu Minh Tri',1792866845151,NULL,'SINH_VIEN','NONE',NULL,2,1,'B23DCCN841');
INSERT INTO `app_user` (`id`, `username`, `password_hash`, `display_name`, `created_at_ms`, `deleted_at_ms`, `role`, `ban_kind`, `banned_until_ms`, `ban_version`, `auth_epoch`, `ma_sinh_vien`) VALUES (85,'gv1','$2a$10$YAt73Yg.3WtBivRY5JyODefCCQ116BTSAwLi2TQKe6yYC8f4DJSKm','Nguyen Van A',1792867007280,NULL,'GIANG_VIEN','NONE',NULL,0,0,NULL);

-- academic_year
INSERT INTO `academic_year` VALUES (2,'1','2025-2026');

-- academic_semester
INSERT INTO `academic_semester` VALUES (2,2,'HK1','Học kì 1');

-- academic_subject
INSERT INTO `academic_subject` VALUES (2,'LTM','Lập trình mạng');

-- subject_offering
INSERT INTO `subject_offering` VALUES (2,2,2);

-- course_class
INSERT INTO `course_class` VALUES (3,2,'01','LTM01',5);

-- teaching_assignment
INSERT INTO `teaching_assignment` VALUES (3,3,85);

-- class_room
INSERT INTO `class_room` VALUES (3,3,'E0DA8E0271394FEF8BD83AEABFD72038');

-- class_membership
INSERT INTO `class_membership` VALUES (2,3,84,'APPROVED',3,1792867198068,1792867210092,85,NULL);

-- quiz
INSERT INTO `quiz` VALUES (71,85,'LTM · Quiz · Kiến trúc Client–Server và giao thức','PUBLIC',1792867677461,NULL,0,'QUIZ',2);
INSERT INTO `quiz` VALUES (72,85,'LTM · Quiz · TCP Socket trong Java','PUBLIC',1792867677461,NULL,0,'QUIZ',2);
INSERT INTO `quiz` VALUES (73,85,'LTM · Quiz · UDP và Datagram trong Java','PUBLIC',1792867677461,NULL,0,'QUIZ',2);
INSERT INTO `quiz` VALUES (74,85,'LTM · Quiz · HTTP, REST API và WebSocket','PUBLIC',1792867677461,NULL,0,'QUIZ',2);
INSERT INTO `quiz` VALUES (75,85,'LTM · Quiz · Đồng thời, độ tin cậy và Server Authoritative','PUBLIC',1792867677461,NULL,0,'QUIZ',2);
INSERT INTO `quiz` VALUES (76,85,'LTM · Điền từ · Nền tảng mạng','PUBLIC',1792867677461,NULL,0,'FILL_BLANK',2);
INSERT INTO `quiz` VALUES (77,85,'LTM · Điền từ · TCP Socket Java','PUBLIC',1792867677461,NULL,0,'FILL_BLANK',2);
INSERT INTO `quiz` VALUES (78,85,'LTM · Điền từ · UDP Datagram Java','PUBLIC',1792867677461,NULL,0,'FILL_BLANK',2);
INSERT INTO `quiz` VALUES (79,85,'LTM · Điền từ · HTTP và WebSocket','PUBLIC',1792867677461,NULL,0,'FILL_BLANK',2);
INSERT INTO `quiz` VALUES (80,85,'LTM · Điền từ · Đồng thời và tính nhất quán','PUBLIC',1792867677461,NULL,0,'FILL_BLANK',2);
INSERT INTO `quiz` VALUES (81,85,'LTM · Bài tập · Thiết kế giao thức chat TCP','PUBLIC',1792867677461,NULL,0,'ASSIGNMENT',2);
INSERT INTO `quiz` VALUES (82,85,'LTM · Bài tập · UDP tin cậy ở tầng ứng dụng','PUBLIC',1792867677461,NULL,0,'ASSIGNMENT',2);
INSERT INTO `quiz` VALUES (83,85,'LTM · Bài tập · REST API cho hệ thống lớp học','PUBLIC',1792867677461,NULL,0,'ASSIGNMENT',2);
INSERT INTO `quiz` VALUES (84,85,'LTM · Bài tập · WebSocket realtime và reconnect','PUBLIC',1792867677461,NULL,0,'ASSIGNMENT',2);
INSERT INTO `quiz` VALUES (85,85,'LTM · Bài tập · Server đa Client và kiểm soát đồng thời','PUBLIC',1792867677461,NULL,0,'ASSIGNMENT',2);
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`root`@`localhost`*/ /*!50003 TRIGGER `trg_quiz_mode_immutable` BEFORE UPDATE ON `quiz` FOR EACH ROW BEGIN
 IF NOT (OLD.mode <=> NEW.mode) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Question set mode is immutable'; END IF;
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;

-- question
INSERT INTO `question` VALUES (682,71,1,'Trong kiến trúc Client–Server, thành phần nào thường lắng nghe yêu cầu tại một cổng đã biết?','Server','Client luôn luôn','Bộ biên dịch','Trình soạn thảo','A',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (683,71,2,'Một endpoint TCP thường được xác định bởi cặp thông tin nào?','Tên file và phần mở rộng','Địa chỉ IP và số cổng','Tên người dùng và mật khẩu','Tên lớp và tên biến','B',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (684,71,3,'Đặc điểm nào mô tả đúng giao tiếp full-duplex?','Chỉ Client được gửi','Hai phía bắt buộc gửi luân phiên','Hai phía có thể gửi và nhận đồng thời','Chỉ Server được gửi','C',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (685,71,4,'Vai trò chính của DNS là gì?','Mã hóa toàn bộ dữ liệu ứng dụng','Cấp số thứ tự cho byte TCP','Biên dịch chương trình Java','Phân giải tên miền thành thông tin địa chỉ','D',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (686,71,5,'Thông tin nào giúp hệ điều hành chuyển dữ liệu đến đúng dịch vụ mạng trên một máy?','Số cổng','Tên hiển thị người dùng','Độ dài tên file','Phiên bản IDE','A',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (687,71,6,'Địa chỉ IPv4 127.0.0.1 dùng để truy cập đâu?','Máy chủ DNS công cộng bất kỳ','Chính máy đang thực hiện kết nối','Mọi thiết bị trên Internet','Router của mọi mạng','B',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (688,71,7,'Để điện thoại cùng LAN truy cập Server trên laptop, thường cần dùng địa chỉ nào?','127.0.0.1 của điện thoại','Tên biến chứa socket trong Java','Địa chỉ LAN của laptop và cổng dịch vụ','localhost của điện thoại','C',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (689,71,8,'Vì sao giao thức ứng dụng cần quy định cấu trúc thông điệp?','Để không cần xử lý lỗi mạng','Để mọi yêu cầu luôn thành công','Để thay thế địa chỉ IP','Để hai phía giải mã và hiểu thống nhất dữ liệu','D',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (690,71,9,'Serialization trong trao đổi dữ liệu là thao tác nào?','Chuyển dữ liệu thành biểu diễn có thể truyền đi','Đóng tất cả kết nối đang mở','Cấp quyền quản trị cho Client','Tăng tốc độ Wi-Fi bằng phần mềm','A',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (691,71,10,'Khi hai phía truyền văn bản có dấu tiếng Việt, cần thống nhất điều gì?','Hãng sản xuất bàn phím','Bảng mã ký tự, chẳng hạn UTF-8','Tên thư mục project','Màu giao diện','B',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (692,72,1,'Lớp Java nào thường dùng để lắng nghe kết nối TCP đến?','ServerSocket','DatagramPacket','StringBuilder','ArrayList','A',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (693,72,2,'Phương thức nào của ServerSocket chờ và nhận một kết nối đến?','substring()','accept()','send()','flush()','B',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (694,72,3,'ServerSocket.accept() trả về đối tượng thuộc lớp nào?','DatagramSocket','ThreadPoolExecutor','Socket','ServerSocket','C',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (695,72,4,'TCP cung cấp cho ứng dụng cách truyền dữ liệu nào?','Các thông điệp giữ nguyên ranh giới mỗi lần write','Broadcast đến toàn mạng theo mặc định','Các gói không có cơ chế truyền lại','Luồng byte tin cậy và có thứ tự','D',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (696,72,5,'Vì sao TCP cần framing ở tầng ứng dụng?','TCP không tự giữ ranh giới thông điệp ứng dụng','TCP chỉ truyền được một byte','TCP không hỗ trợ tiếng Việt','TCP không dùng cổng','A',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (697,72,6,'InputStream.read() trả về -1 có ý nghĩa gì?','Cần tăng số cổng','Đã đến cuối luồng','Đọc được một byte có giá trị âm bất kỳ','Socket chắc chắn chưa kết nối','B',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (698,72,7,'Trong Java Socket, setSoTimeout(...) chủ yếu giới hạn thao tác nào?','Thời gian biên dịch Java','Thời gian mọi lần ghi vào mạng','Thời gian chờ đọc khi chưa có dữ liệu','Tổng thời gian chạy của ứng dụng','C',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (699,72,8,'Khi dùng BufferedWriter, flush() có vai trò gì?','Bảo đảm phía nhận đã xử lý nghiệp vụ','Tạo một kết nối TCP mới','Xóa mọi dữ liệu trong database','Đẩy dữ liệu đệm xuống luồng bên dưới','D',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (700,72,9,'Một lần read(byte[]) có thể đọc ít byte hơn kích thước thông điệp mong đợi không?','Có, ứng dụng phải tích lũy và đọc tiếp','Không, TCP luôn đọc đủ một thông điệp','Chỉ khi dùng UDP','Chỉ khi chạy trên Linux','A',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (701,72,10,'Cách nào phù hợp để giải phóng Socket khi kết thúc xử lý?','Tạo thêm Socket để thay thế','Dùng try-with-resources hoặc đóng trong finally','Chỉ đặt biến tham chiếu thành null','Chờ người dùng đóng trình duyệt','B',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (702,73,1,'Đơn vị dữ liệu ứng dụng thường gửi qua UDP là gì?','Datagram','Luồng ký tự vô hạn có bảo đảm thứ tự','Bảng database','Phiên đăng nhập HTTP','A',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (703,73,2,'Lớp Java nào thường dùng để gửi và nhận UDP?','ObjectOutputStream','DatagramSocket','ServerSocket','FileReader','B',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (704,73,3,'Đối tượng Java nào mang dữ liệu và địa chỉ khi gửi UDP?','StringBuffer','Semaphore','DatagramPacket','HttpSession','C',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (705,73,4,'UDP có tự bảo đảm thứ tự nhận của mọi datagram không?','Có, trong mọi trường hợp','Chỉ khi payload là JSON','Chỉ khi số cổng nhỏ hơn 1024','Không','D',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (706,73,5,'Nếu ứng dụng UDP cần bảo đảm nhận dữ liệu, cần làm gì?','Thiết kế ACK, timeout và gửi lại ở tầng ứng dụng','Chỉ đổi tên biến chứa dữ liệu','Chỉ dùng UTF-8','Chỉ tăng kích thước cửa sổ giao diện','A',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (707,73,6,'Khi giải mã DatagramPacket vừa nhận, nên dùng độ dài nào?','Số cổng đích','getLength() của gói vừa nhận','Toàn bộ kích thước buffer cấp phát trong mọi trường hợp','Độ dài tên máy chủ','B',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (708,73,7,'Mất gói UDP có thể xảy ra không?','Chỉ xảy ra với HTTP','Chỉ xảy ra khi không dùng Java','Có','Không vì UDP luôn truyền lại','C',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (709,73,8,'Để phát hiện datagram trùng do gửi lại, ứng dụng nên bổ sung gì?','Màu nền của Client','Tên project khác nhau','Tên file log dài hơn','Mã định danh hoặc số thứ tự thông điệp','D',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (710,73,9,'DatagramSocket.receive(...) mặc định là loại thao tác nào khi chưa có gói đến?','Chờ chặn cho đến khi có dữ liệu hoặc xảy ra lỗi','Luôn trả về ngay một gói rỗng','Tự tạo dữ liệu ngẫu nhiên','Tự chuyển sang TCP','A',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (711,73,10,'Đặc điểm nào phù hợp với UDP?','Tự lưu mọi datagram vào database','Ít cơ chế bảo đảm tích hợp hơn TCP','Tự xác thực danh tính người gửi','Luôn nhanh hơn TCP trong mọi tình huống','B',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (712,74,1,'HTTP thường tổ chức trao đổi theo mô hình nào?','Request–Response','Chỉ Server gửi một chiều','Chỉ gửi datagram broadcast','Chỉ giao tiếp qua file cục bộ','A',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (713,74,2,'Phương thức HTTP nào được thiết kế để truy xuất tài nguyên mà không yêu cầu thay đổi trạng thái tài nguyên?','DELETE','GET','POST','PATCH','B',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (714,74,3,'Mã HTTP 401 thường biểu thị điều gì?','Server chuyển sang WebSocket thành công','Nội dung phản hồi luôn rỗng','Yêu cầu thiếu thông tin xác thực hợp lệ','Tài nguyên đã được tạo thành công','C',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (715,74,4,'Mã HTTP 403 thường biểu thị điều gì?','Server không tìm thấy tên miền DNS','Client đã nhận đủ mọi gói TCP','Yêu cầu chắc chắn chưa tới Server','Server từ chối thực hiện yêu cầu do quyền truy cập','D',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (716,74,5,'Header Content-Type mô tả điều gì?','Kiểu biểu diễn của nội dung thông điệp','Địa chỉ MAC của Server','Số lượng luồng Java','Mật khẩu database','A',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (717,74,6,'JSON thuộc loại biểu diễn dữ liệu nào?','Một giao thức tầng vận chuyển','Văn bản có cấu trúc','Mã máy Java','Định dạng chỉ dành cho video','B',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (718,74,7,'Sau khi thiết lập, WebSocket hỗ trợ cách giao tiếp nào?','Mỗi thông điệp phải tạo TCP mới','Chỉ truyền dữ liệu khi tải lại trang','Trao đổi hai chiều trên kết nối duy trì','Chỉ Client được gửi yêu cầu','C',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (719,74,8,'HTTPS bảo vệ dữ liệu truyền bằng cơ chế nào?','Base64 tự nó là mã hóa bảo mật','Tên miền viết hoa','Đổi cổng thành một số bất kỳ','TLS','D',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (720,74,9,'Trong ứng dụng trình duyệt, CORS chủ yếu kiểm soát điều gì?','Quyền truy cập phản hồi giữa các origin theo chính sách trình duyệt','Quyền của tài khoản ADMIN trong database','Thứ tự các byte TCP','Khả năng router cấp địa chỉ IP','A',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (721,74,10,'Khi Client reconnect sau mất WebSocket, cách khôi phục trạng thái phù hợp là gì?','Xóa kết quả đã lưu trên Server','Lấy snapshot mới từ Server và đồng bộ lại','Tự tăng điểm theo thời gian mất mạng','Giả định mọi yêu cầu trước đó đều thất bại','B',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (722,75,1,'Race condition có thể xảy ra khi nào?','Nhiều luồng truy cập trạng thái dùng chung mà thiếu đồng bộ phù hợp','Chỉ khi mọi Client đã đăng xuất','Chỉ khi dùng một biến hằng','Chỉ khi không có kết nối mạng','A',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (723,75,2,'Trong Java, synchronized thường dùng để làm gì?','Gửi broadcast UDP','Bảo vệ đoạn mã truy cập trạng thái chung bằng monitor','Mã hóa payload HTTP','Tự tạo bảng MySQL','B',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (724,75,3,'Thread pool giúp Server giải quyết vấn đề nào?','Loại bỏ nhu cầu phân quyền','Thay thế hoàn toàn database','Tái sử dụng và quản lý số lượng luồng xử lý','Bảo đảm không bao giờ có lỗi nghiệp vụ','C',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (725,75,4,'Idempotency giúp xử lý tình huống nào?','Mọi yêu cầu đều phải tạo bản ghi mới','Không cần kiểm tra deadline','Client tự quyết định điểm số','Một thao tác được gửi lại nhưng không gây hiệu ứng nghiệp vụ lặp','D',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (726,75,5,'Nếu Client không nhận được phản hồi sau khi gửi bài, điều nào có thể đúng?','Server có thể đã xử lý bài nhưng phản hồi bị mất','Server chắc chắn chưa nhận yêu cầu','Client được quyền tự chấm điểm','Database chắc chắn bị xóa','A',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (727,75,6,'Trong Server Authoritative, bên nào quyết định điểm và tính hợp lệ của đáp án?','Thiết bị có Wi-Fi mạnh nhất','Server','JavaScript trên trình duyệt sinh viên','Đồng hồ hệ điều hành của sinh viên','B',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (728,75,7,'Để kiểm tra bài nộp có đúng hạn, nên dùng mốc thời gian nào làm thẩm quyền?','Thời điểm người dùng mở bàn phím','Thời gian render hiệu ứng CSS','Thời gian và deadline do Server quản lý','Đồng hồ Client tự gửi mà không kiểm tra','C',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (729,75,8,'Transaction database cung cấp lợi ích nào?','Bảo đảm mọi mạng không mất kết nối','Tự giải quyết mọi deadlock mà ứng dụng không cần xử lý','Thay thế việc xác thực','Nhóm các thay đổi liên quan để commit hoặc rollback cùng nhau','D',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (730,75,9,'Khi một hàng đợi nhận dữ liệu nhanh hơn tốc độ xử lý trong thời gian dài, nên xem xét điều gì?','Giới hạn hàng đợi và áp dụng backpressure','Cho phép bộ nhớ tăng không giới hạn','Tắt kiểm tra quyền để tăng tốc','Xóa bảng điểm sau mỗi yêu cầu','A',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (731,75,10,'Khi thu hồi quyền của người dùng đang kết nối WebSocket, Server nên làm gì?','Chỉ thay đổi màu avatar','Chặn thao tác tiếp theo và thu hồi hoặc đóng phiên liên quan','Chỉ ẩn nút trên giao diện','Chờ Client tự nguyện đăng xuất','B',NULL,1792867677461,NULL,'QUIZ',NULL);
INSERT INTO `question` VALUES (732,76,1,'Thành phần chủ động gửi yêu cầu tới dịch vụ thường được gọi là {{blank}}.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"Client\", \"máy khách\"]}');
INSERT INTO `question` VALUES (733,76,2,'Thành phần lắng nghe và cung cấp dịch vụ thường được gọi là {{blank}}.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"Server\", \"máy chủ\"]}');
INSERT INTO `question` VALUES (734,76,3,'Cơ chế phân giải tên miền có tên viết tắt là {{blank}}.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"DNS\"]}');
INSERT INTO `question` VALUES (735,76,4,'Một endpoint thường gồm địa chỉ IP và số {{blank}}.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"cổng\", \"port\"]}');
INSERT INTO `question` VALUES (736,76,5,'Địa chỉ IPv4 loopback thường dùng trong ví dụ lập trình mạng là {{blank}}.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"127.0.0.1\"]}');
INSERT INTO `question` VALUES (737,76,6,'Để truyền tiếng Việt thống nhất giữa Client và Server, có thể dùng bảng mã {{blank}}.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"UTF-8\", \"UTF8\"]}');
INSERT INTO `question` VALUES (738,76,7,'Khả năng hai phía gửi và nhận đồng thời được gọi là {{blank}}.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"full-duplex\", \"full duplex\"]}');
INSERT INTO `question` VALUES (739,76,8,'Mạng cục bộ thường được viết tắt là {{blank}}.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"LAN\"]}');
INSERT INTO `question` VALUES (740,76,9,'Quá trình biến đối tượng thành dữ liệu để truyền đi gọi là {{blank}}.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"serialization\", \"tuần tự hóa\"]}');
INSERT INTO `question` VALUES (741,76,10,'Trên điện thoại, localhost trỏ đến chính {{blank}} đang chạy trình duyệt.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"điện thoại\", \"thiết bị\"]}');
INSERT INTO `question` VALUES (742,77,1,'Lớp Java thường dùng để lắng nghe TCP là {{blank}}.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"ServerSocket\"]}');
INSERT INTO `question` VALUES (743,77,2,'Phương thức ServerSocket.{{blank}}() chờ một kết nối TCP đến.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"accept\"]}');
INSERT INTO `question` VALUES (744,77,3,'Đối tượng đại diện cho một kết nối TCP trong Java là {{blank}}.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"Socket\"]}');
INSERT INTO `question` VALUES (745,77,4,'Để lấy luồng đọc của Socket, gọi phương thức {{blank}}().',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"getInputStream\"]}');
INSERT INTO `question` VALUES (746,77,5,'Để lấy luồng ghi của Socket, gọi phương thức {{blank}}().',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"getOutputStream\"]}');
INSERT INTO `question` VALUES (747,77,6,'InputStream.read() trả về {{blank}} khi đã đến cuối luồng.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"-1\"]}');
INSERT INTO `question` VALUES (748,77,7,'Phương thức {{blank}}() đẩy dữ liệu từ bộ đệm ghi xuống luồng bên dưới.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"flush\"]}');
INSERT INTO `question` VALUES (749,77,8,'Phương thức Socket.{{blank}}(...) đặt thời gian chờ đọc dữ liệu.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"setSoTimeout\"]}');
INSERT INTO `question` VALUES (750,77,9,'TCP cung cấp một luồng {{blank}}, không tự giữ ranh giới thông điệp ứng dụng.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"byte\"]}');
INSERT INTO `question` VALUES (751,77,10,'Để tự đóng Socket khi ra khỏi khối xử lý trong Java, có thể dùng {{blank}}.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"try-with-resources\", \"try with resources\"]}');
INSERT INTO `question` VALUES (752,78,1,'Lớp gửi và nhận UDP trong Java là {{blank}}.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"DatagramSocket\"]}');
INSERT INTO `question` VALUES (753,78,2,'Lớp chứa dữ liệu cùng địa chỉ của một gói UDP trong Java là {{blank}}.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"DatagramPacket\"]}');
INSERT INTO `question` VALUES (754,78,3,'Phương thức DatagramSocket.{{blank}}(...) gửi một datagram.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"send\"]}');
INSERT INTO `question` VALUES (755,78,4,'Phương thức DatagramSocket.{{blank}}(...) chờ nhận một datagram.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"receive\"]}');
INSERT INTO `question` VALUES (756,78,5,'Phương thức DatagramPacket.{{blank}}() trả về độ dài dữ liệu gói vừa nhận.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"getLength\"]}');
INSERT INTO `question` VALUES (757,78,6,'Thông điệp xác nhận nhận dữ liệu thường được viết tắt là {{blank}}.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"ACK\"]}');
INSERT INTO `question` VALUES (758,78,7,'UDP không tự bảo đảm gói đến đúng {{blank}} gửi.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"thứ tự\"]}');
INSERT INTO `question` VALUES (759,78,8,'Khi quá thời gian chờ ACK, ứng dụng tin cậy chạy trên UDP có thể {{blank}} thông điệp.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"gửi lại\", \"truyền lại\", \"retransmit\"]}');
INSERT INTO `question` VALUES (760,78,9,'Để nhận biết một thông điệp UDP bị gửi trùng, có thể sử dụng mã {{blank}}.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"định danh\", \"ID\", \"id\"]}');
INSERT INTO `question` VALUES (761,78,10,'Sau khi dùng xong DatagramSocket, gọi phương thức {{blank}}() để giải phóng tài nguyên.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"close\"]}');
INSERT INTO `question` VALUES (762,79,1,'HTTP thường trao đổi theo mô hình Request–{{blank}}.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"Response\"]}');
INSERT INTO `question` VALUES (763,79,2,'Phương thức HTTP thường dùng để lấy dữ liệu là {{blank}}.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"GET\"]}');
INSERT INTO `question` VALUES (764,79,3,'Phương thức HTTP thường dùng để gửi dữ liệu tạo tài nguyên mới là {{blank}}.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"POST\"]}');
INSERT INTO `question` VALUES (765,79,4,'Mã HTTP {{blank}} biểu thị yêu cầu thiếu thông tin xác thực hợp lệ.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"401\"]}');
INSERT INTO `question` VALUES (766,79,5,'Mã HTTP {{blank}} thường biểu thị không tìm thấy tài nguyên.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"404\"]}');
INSERT INTO `question` VALUES (767,79,6,'Header khai báo kiểu dữ liệu của phần thân là {{blank}}.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"Content-Type\"]}');
INSERT INTO `question` VALUES (768,79,7,'Kiểu nội dung HTTP thường dùng cho JSON là {{blank}}.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"application/json\"]}');
INSERT INTO `question` VALUES (769,79,8,'Giao thức duy trì kết nối và hỗ trợ trao đổi full-duplex giữa trình duyệt và Server là {{blank}}.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"WebSocket\"]}');
INSERT INTO `question` VALUES (770,79,9,'HTTPS sử dụng {{blank}} để bảo vệ kênh truyền.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"TLS\"]}');
INSERT INTO `question` VALUES (771,79,10,'Sau khi reconnect, Client nên lấy {{blank}} từ Server để khôi phục trạng thái hiện tại.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"snapshot\", \"bản chụp trạng thái\"]}');
INSERT INTO `question` VALUES (772,80,1,'Trong Java, từ khóa {{blank}} có thể bảo vệ một đoạn mã bằng monitor.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"synchronized\"]}');
INSERT INTO `question` VALUES (773,80,2,'Một nhóm luồng được tái sử dụng để xử lý công việc gọi là {{blank}}.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"thread pool\", \"threadpool\"]}');
INSERT INTO `question` VALUES (774,80,3,'Tình huống kết quả phụ thuộc vào thứ tự truy cập không được kiểm soát giữa các luồng gọi là {{blank}}.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"race condition\"]}');
INSERT INTO `question` VALUES (775,80,4,'Cơ chế tránh lặp hiệu ứng nghiệp vụ khi nhận lại cùng thao tác gọi là {{blank}}.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"idempotency\"]}');
INSERT INTO `question` VALUES (776,80,5,'Trong Server Authoritative, bên quyết định điểm số là {{blank}}.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"Server\", \"máy chủ\"]}');
INSERT INTO `question` VALUES (777,80,6,'Khi một giao dịch thất bại, thao tác hoàn tác thay đổi chưa commit được gọi là {{blank}}.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"rollback\"]}');
INSERT INTO `question` VALUES (778,80,7,'Thao tác xác nhận các thay đổi của giao dịch database được gọi là {{blank}}.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"commit\"]}');
INSERT INTO `question` VALUES (779,80,8,'Để kiểm tra nộp bài đúng hạn, dùng đồng hồ và deadline của {{blank}} làm thẩm quyền.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"Server\", \"máy chủ\"]}');
INSERT INTO `question` VALUES (780,80,9,'Khi bộ nhận không theo kịp bộ gửi, cơ chế phản hồi để hạn chế tốc độ gửi thường gọi là {{blank}}.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"backpressure\", \"back pressure\"]}');
INSERT INTO `question` VALUES (781,80,10,'Một mã {{blank}} cho mỗi thao tác giúp Server nhận diện yêu cầu được gửi lại.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'FILL_BLANK','{\"matchingPolicy\": \"CASE_INSENSITIVE\", \"acceptedAnswers\": [\"requestId\", \"request ID\", \"request_id\"]}');
INSERT INTO `question` VALUES (782,81,1,'Thiết kế ứng dụng chat TCP gồm một Server Java và nhiều Client trong cùng LAN.\nYêu cầu: xác định các loại thông điệp JOIN, CHAT, LEAVE, ERROR; đề xuất framing bằng tiền tố độ dài hoặc ký tự kết thúc; thống nhất UTF-8; giải thích cách xử lý read thiếu byte, nhiều thông điệp trong một lần đọc và ngắt kết nối.\nBài nộp trực tiếp trên web: mô tả giao thức, ví dụ tối thiểu 4 thông điệp, mã Java hoặc giả mã vòng nhận dữ liệu, cách quản lý danh sách Client đồng thời và 3 tình huống kiểm thử.\nTiêu chí chấm: giao thức và framing 35%; xử lý nhiều Client 25%; lỗi mạng và giải phóng tài nguyên 25%; ví dụ và kiểm thử 15%.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'ASSIGNMENT','{}');
INSERT INTO `question` VALUES (783,82,1,'Thiết kế chương trình Java gửi thông báo qua UDP có ACK và gửi lại. Mỗi thông báo có messageId; bên nhận không áp dụng lại cùng thông báo khi datagram bị lặp.\nYêu cầu: mô tả cấu trúc DATA và ACK; timeout, giới hạn số lần thử, xử lý ACK đến muộn, datagram sai định dạng và thứ tự nhận khác thứ tự gửi. Không giả định UDP tự bảo đảm giao hàng.\nBài nộp trực tiếp trên web: sơ đồ bước bằng văn bản, ví dụ thông điệp, mã hoặc giả mã bên gửi và nhận, phân tích trường hợp DATA đã được xử lý nhưng ACK bị mất.\nTiêu chí chấm: giao thức 25%; timeout và gửi lại 25%; chống xử lý trùng 30%; xử lý lỗi và kiểm thử 20%.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'ASSIGNMENT','{}');
INSERT INTO `question` VALUES (784,83,1,'Thiết kế REST API cho việc xem danh sách buổi học, tham gia buổi và nộp câu trả lời. Server chịu trách nhiệm xác thực, quyền truy cập, deadline và kết quả.\nYêu cầu: nêu method, đường dẫn, request/response JSON và mã HTTP; phân biệt 401, 403, 404, 409; dùng requestId hoặc idempotency key để xử lý nộp lại khi mất phản hồi. Không gửi đáp án đúng cho sinh viên trước thời điểm được phép.\nBài nộp trực tiếp trên web: tối thiểu 3 endpoint, 2 ví dụ lỗi, giả mã transaction nộp bài và phân tích hai request cùng đến cho một sinh viên.\nTiêu chí chấm: thiết kế API 30%; xác thực và phân quyền 25%; deadline và tính nhất quán 30%; ví dụ 15%.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'ASSIGNMENT','{}');
INSERT INTO `question` VALUES (785,84,1,'Thiết kế luồng WebSocket cho một trò Quiz có một giảng viên và nhiều sinh viên. Server tự quản lý câu hiện tại, thời hạn và điểm số.\nYêu cầu: định nghĩa thông điệp bắt đầu câu, nộp đáp án, ACK, lỗi và snapshot; bổ sung mã thao tác và phiên bản trạng thái; mô tả reconnect khi mất mạng giữa câu hoặc sau khi đã gửi đáp án nhưng chưa nhận ACK. Không dùng đồng hồ Client làm thẩm quyền chấm hạn.\nBài nộp trực tiếp trên web: một kịch bản trao đổi tối thiểu 6 bước, JSON minh họa, giả mã xử lý reconnect và cách xử lý sự kiện trùng hoặc phiên bản cũ.\nTiêu chí chấm: giao thức realtime 30%; snapshot và reconnect 30%; Server Authoritative và deadline 25%; tình huống lỗi 15%.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'ASSIGNMENT','{}');
INSERT INTO `question` VALUES (786,85,1,'Thiết kế Server Java tiếp nhận câu trả lời từ nhiều Client đồng thời, bảo đảm mỗi sinh viên chỉ được ghi nhận một kết quả cho cùng câu.\nYêu cầu: đề xuất thread pool và hàng đợi có giới hạn; chỉ ra trạng thái dùng chung; kết hợp transaction, unique constraint hoặc locking phù hợp; xác định điểm kiểm tra deadline trên Server; giải thích xử lý retry và thu hồi quyền trong khi Client còn kết nối.\nBài nộp trực tiếp trên web: mô tả kiến trúc, giả mã xử lý nộp bài, phân tích hai request cạnh tranh và kế hoạch kiểm thử nhiều Client trong LAN có mất mạng rồi reconnect.\nTiêu chí chấm: quản lý luồng và hàng đợi 25%; tránh race condition 30%; idempotency và transaction 25%; kiểm thử và xử lý lỗi 20%.',NULL,NULL,NULL,NULL,NULL,NULL,1792867677461,NULL,'ASSIGNMENT','{}');

COMMIT;
