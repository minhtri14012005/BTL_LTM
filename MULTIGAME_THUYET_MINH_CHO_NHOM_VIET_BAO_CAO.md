# MULTIGAME — THUYẾT MINH HỆ THỐNG PHỤC VỤ VIẾT BÁO CÁO LẬP TRÌNH MẠNG

**Mục đích tài liệu:** Giúp thành viên phụ trách báo cáo hiểu, mô tả và giải thích hệ thống bằng ngôn ngữ báo cáo kỹ thuật đại học, không cần đọc trực tiếp source code. Đây là tài liệu thuyết minh phục vụ biên soạn báo cáo, **chưa phải báo cáo cuối kỳ hoàn chỉnh**.

**Nguồn nội dung:** Biên soạn lại từ tài liệu kỹ thuật `REPORT_SOURCE_EVIDENCE(2).md`, khảo sát source ngày 19/10/2026. Bản gốc cần được giữ riêng để đối chiếu các chi tiết code, phép thử và dữ liệu. Không tự bổ sung tính năng hoặc kết quả không có trong nguồn.

**Giới hạn bằng chứng:** Kết quả kiểm thử T18 và benchmark trong tài liệu là kết quả đã lưu của những lần chạy trước, không phải lần chạy lại toàn bộ phiên bản hiện tại. Chưa có bằng chứng demo trên nhiều thiết bị LAN vật lý. Những tình huống minh họa bằng A/B/C dưới đây là **ví dụ giải thích cơ chế**, không được xem là biên bản thực nghiệm mới.

---

# PHẦN 1. GIỚI THIỆU ĐỀ TÀI

## 1.1. Bối cảnh và bài toán đặt ra

MultiGame là hệ thống tổ chức trò chơi hỏi đáp có nhiều người tham gia qua mạng. Mỗi người chơi sử dụng một trình duyệt riêng để vào phòng, nhận câu hỏi và gửi đáp án. Điểm khác biệt giữa hệ thống này với một ứng dụng trắc nghiệm làm bài cá nhân nằm ở việc tất cả người chơi trong cùng trận đều tham gia một tiến trình chung: câu hỏi được mở đồng thời theo quyết định của máy chủ, thời hạn trả lời được áp dụng thống nhất, và kết quả được công bố sau khi quá trình chấm điểm hoàn tất.

Khi có nhiều người chơi, hệ thống phải xử lý những tình huống không xuất hiện trong một bài trắc nghiệm thông thường. Hai hoặc ba người có thể gửi đáp án gần như cùng lúc; một người bị mất kết nối giữa câu hỏi; thông báo đã nhận đáp án có thể đến chậm; hoặc quản trị viên thực hiện lệnh cấm khi trận đấu đang diễn ra. Nếu mỗi trình duyệt tự quyết định trạng thái và điểm số thì những người tham gia có thể nhìn thấy kết quả khác nhau. Vì vậy, bài toán cốt lõi là **duy trì một trạng thái trận đấu thống nhất và đáng tin cậy trước các yêu cầu đồng thời và sự cố kết nối**.

Hệ thống được phát triển để vận dụng các kiến thức của học phần Lập trình mạng: mô hình Client–Server, truyền thông hai chiều theo thời gian thực, thiết kế giao thức thông điệp, quản lý kết nối, xử lý đồng thời, kiểm soát quyền truy cập và duy trì tính nhất quán dữ liệu.

## 1.2. Mục tiêu xây dựng

Mục tiêu về chức năng là tạo được một môi trường chơi nhiều người, có tài khoản, phòng chơi, bộ câu hỏi, nhiều chế độ tương tác, chấm điểm, lịch sử và thành tích. Người chủ phòng có thể chuẩn bị nội dung và bắt đầu trận; người chơi nhận câu hỏi và trả lời từ những thiết bị hoặc phiên trình duyệt riêng; người xem có thể theo dõi mà không tham gia trả lời.

Mục tiêu kỹ thuật quan trọng hơn đối với môn học là để máy chủ nắm quyền quyết định toàn bộ trận đấu. Máy chủ xác định ai được tham gia, câu hỏi nào đang mở, thời gian có còn hợp lệ hay không, một đáp án đã được chấp nhận chưa, kết quả chấm điểm và thứ hạng cuối cùng. Hệ thống còn được thiết kế để tránh ghi nhận trùng khi có yêu cầu lặp lại và giúp người chơi khôi phục trạng thái sau khi kết nối bị gián đoạn.

## 1.3. Phạm vi thực hiện

Phiên bản được khảo sát gồm một máy chủ ứng dụng Spring Boot, một cơ sở dữ liệu MySQL và giao diện web sử dụng trong trình duyệt. Giao diện và máy chủ giao tiếp qua HTTP/REST và WebSocket. Kiến trúc hướng tới sử dụng trong mạng cục bộ (LAN); tuy nhiên, việc **thực sự chạy trên nhiều máy vật lý** vẫn cần một buổi thử nghiệm riêng để ghi nhận kết quả.

Hệ thống hiện có bảy chế độ chơi. Chế độ thách đấu riêng Friendly 1v1 và các công việc phụ thuộc T15–T17 đã được loại khỏi phạm vi, nên không được liệt kê là chức năng đã triển khai. Hệ thống cũng không được xây dựng theo mô hình nhiều máy chủ phân tán, không có cơ chế tự chuyển máy chủ khi gặp sự cố, và không tiếp tục câu hỏi đang chơi sau khi tiến trình máy chủ khởi động lại.

# PHẦN 2. CÁCH NGƯỜI DÙNG SỬ DỤNG HỆ THỐNG

## 2.1. Các vai trò

Hệ thống phân biệt **quyền ở cấp tài khoản** và **vai trò trong từng phòng chơi**. Ở cấp tài khoản, người dùng thông thường có quyền USER, còn ADMIN có quyền quản trị. Một tài khoản USER có thể làm chủ phòng, tham gia chơi hoặc đứng xem tùy tình huống. Việc làm chủ phòng không tự biến người đó thành ADMIN.

**Chủ phòng (Host)** tạo và cấu hình phòng, lựa chọn các bộ câu hỏi, mời thành viên tham gia và bắt đầu trận khi đủ điều kiện. Host có quyền quản lý phòng nhưng vẫn phải tuân theo những ràng buộc do Server kiểm tra. **Người chơi (Player)** nhận câu hỏi, gửi đáp án và được tính điểm. **Người xem (Spectator)** theo dõi trận đấu nhưng không được trả lời hay sử dụng vật phẩm gameplay. **Quản trị viên (ADMIN)** có công cụ giám sát và áp dụng biện pháp xử lý người chơi theo các điều kiện hệ thống quy định.

**Cách hiểu sự khác nhau giữa Host và ADMIN.** Host chỉ quản lý **phòng do mình tạo** trong phạm vi chức năng cho phép: chuẩn bị nội dung, điều chỉnh cấu hình và đề nghị bắt đầu trận. ADMIN quản lý ở **cấp hệ thống**, có thể xử lý tài khoản hoặc Game theo quyền quản trị được Server kiểm tra. Ví dụ, Host không thể tự ý cấm vĩnh viễn tài khoản của người khác chỉ vì người đó đang ở phòng mình. Ngược lại, ADMIN không cần trở thành Host của từng phòng mới có thể sử dụng chức năng quản trị thuộc thẩm quyền. Việc cùng một người có thể vừa mang quyền tài khoản vừa giữ vai trò trong phòng không làm hai loại quyền này trở thành một.

**Cách hiểu sự khác nhau giữa Player và Spectator.** Cả hai có thể quan sát diễn biến trận phù hợp với quyền truy cập, nhưng chỉ Player thuộc đối tượng được nhận câu và gửi đáp án để chấm điểm. Spectator không được biến thành Player bằng việc tự sửa giao diện hoặc tự gửi thông điệp ANSWER: Server vẫn kiểm tra vai trò thực tế của người gửi.

Một điểm cần lưu ý là tác giả của bộ câu hỏi không được trực tiếp thi đấu bằng chính bộ câu hỏi mình tạo. Quy tắc này hạn chế lợi thế do biết trước đáp án. Nếu Host cũng là tác giả bộ câu hỏi, Host có thể theo dõi với vai trò người xem và cần đủ số người chơi khác để bắt đầu trận.

## 2.2. Quy trình từ đăng nhập đến kết thúc trận

Một phiên sử dụng điển hình bắt đầu khi người dùng đăng ký hoặc đăng nhập. Sau khi đăng nhập, máy chủ xác nhận danh tính và cấp phiên làm việc. Người dùng truy cập khu vực phòng chơi, nơi có thể tạo phòng mới hoặc tham gia phòng có mã mời.

Khi tạo phòng, Host thiết lập nội dung và các chặng chơi. Phòng ban đầu ở trạng thái chuẩn bị; Host mở phòng để thành viên khác tham gia. Tại phòng chờ, mọi thay đổi về thành viên được máy chủ xác nhận và thông báo tới những người đang theo dõi phòng. Trận chỉ có thể bắt đầu khi có ít nhất **ba người tham gia với vai trò Player**, cùng những điều kiện hợp lệ khác về tài khoản, bộ câu hỏi và cấu hình.

Sau lệnh bắt đầu, Server tạo bản dữ liệu riêng cho trận đấu, gồm danh sách thành viên, cấu hình và nội dung câu hỏi đã chọn. Trong khi chơi, Server lần lượt mở các giai đoạn, nhận câu trả lời, đóng câu, tính điểm và phát kết quả. Người chơi xem diễn biến trực tiếp qua WebSocket. Khi trận kết thúc, hệ thống lưu lại lịch sử, xử lý thành tích phù hợp và đưa phòng về trạng thái có thể chuẩn bị một trận khác.

Có thể tóm tắt hành trình sử dụng như sau: **Đăng nhập → Tạo/Tham gia phòng → Chuẩn bị trận → Bắt đầu → Trả lời và nhận kết quả → Xem lịch sử, thành tích → Chơi trận mới trong cùng phòng**. Điều quan trọng là mỗi bước có thay đổi trạng thái đều do máy chủ xác nhận, không chỉ do giao diện tự chuyển màn hình.

## 2.3. Các chức năng chính

**Quản lý tài khoản.** Người dùng có thể đăng ký, đăng nhập và đăng xuất. Hệ thống lưu mật khẩu theo dạng băm thay vì văn bản gốc; quyền hạn được kiểm tra trên Server. Tài khoản bị cấm không thể tiếp tục sử dụng những phiên cũ để thực hiện thao tác trái phép.

**Quản lý phòng chơi.** Host tạo phòng, cấu hình, mở phòng, đóng phòng trong điều kiện phù hợp và quản lý thành viên. Trạng thái phòng được thông báo tới các Client đang theo dõi. Một phòng có thể được sử dụng cho nhiều trận khác nhau mà không xóa lịch sử trận trước.

**Quản lý bộ câu hỏi.** Người dùng có quyền tạo bộ câu hỏi và lựa chọn chế độ công khai (PUBLIC) hoặc riêng tư (PRIVATE). Hệ thống hỗ trợ dữ liệu văn bản, hình ảnh và video đối với các loại câu hỏi tương ứng. Khi trận bắt đầu, nội dung dùng trong trận được lưu thành bản độc lập; sửa bộ câu hỏi nguồn về sau không thay đổi lịch sử câu hỏi đã chơi.

**Tổ chức trận đấu.** Người chơi tham gia các chặng, nhận câu hỏi và gửi đáp án; Server kiểm tra tính hợp lệ, chấm điểm và xếp hạng. Thời hạn trả lời được quyết định ở Server. Có cơ chế đóng câu sớm khi những người cần chờ đã trả lời đầy đủ theo quy tắc hiện hành.

**Khôi phục khi mất kết nối.** Khi kết nối WebSocket bị gián đoạn, người chơi có thể kết nối lại và yêu cầu máy chủ cung cấp trạng thái trận hiện tại. Những đáp án đã lưu và thời gian gốc của câu hỏi không bị đặt lại vì việc kết nối lại.

**Lịch sử và thành tích.** Hệ thống lưu trận đã kết thúc, kết quả câu hỏi, thứ hạng và các dữ liệu liên quan. Cúp và danh hiệu chính thức được quyết định theo một quy trình độc lập với màn hình kết quả; việc tải lại trang không tự cấp thêm phần thưởng.

**Quản trị.** ADMIN có thể xem thông tin quản trị, đuổi người chơi khỏi một trận trong phạm vi được phép, cấm tài khoản tạm thời hoặc không thời hạn, gỡ cấm và xử lý một số phòng không còn Host hợp lệ. Đây là quyền do Server thực thi, không phải chỉ là nút bấm trên giao diện.

Để hiểu phạm vi từng thao tác, có thể phân biệt như sau:

| Thao tác của ADMIN | Đối tượng bị tác động | Ý nghĩa và giới hạn cần hiểu |
|---|---|---|
| Xem dữ liệu quản trị | Các đối tượng thuộc phạm vi giám sát của ADMIN | Giúp xác định tài khoản, trận hoặc phòng cần xử lý; quyền xem vẫn do Server kiểm tra. |
| Kick | Một người chơi trong **một Game cụ thể** | Chấm dứt quyền tiếp tục tham gia Game đó theo quy tắc hiện hành; không tương đương với việc khóa cả tài khoản. |
| Ban tạm thời | **Tài khoản** | Hạn chế quyền truy cập của tài khoản theo thời hạn cấm được Server quản lý; không phải hình phạt chỉ áp dụng cho một Game. |
| Ban không thời hạn | **Tài khoản** | Tài khoản bị hạn chế cho đến khi có thao tác gỡ cấm hợp lệ; không có thời điểm tự hết hạn theo chế độ này. |
| Unban | Tài khoản đang bị cấm | Gỡ trạng thái cấm theo quyết định của ADMIN; không phải thao tác khôi phục những Answer chưa bao giờ được Server chấp nhận. |
| Xử lý phòng không còn Host hợp lệ (*orphan room*) | Phòng thuộc điều kiện quản trị cho phép | Cho phép quản trị viên xử lý phòng bị bỏ lại theo kiểm tra của Server; không phải quyền tùy tiện xóa bất kỳ phòng hay Game nào. |

Ví dụ, người chơi A có hành vi không phù hợp trong một trận. Nếu ADMIN **Kick A khỏi Game #1**, phạm vi xử lý là Game #1; không được mô tả rằng tài khoản A đã bị cấm đăng nhập toàn hệ thống. Nếu ADMIN **Ban tài khoản A**, việc xử lý áp dụng theo trạng thái tài khoản, nên cần kiểm tra cả phiên đã đăng nhập, kết nối WebSocket và các thao tác mới, chứ không chỉ ẩn nút trên giao diện. Đây là ví dụ để phân biệt phạm vi, không phải biên bản một lượt chạy thực nghiệm.

## 2.4. Bảy chế độ chơi

| Chế độ | Cách người chơi tương tác | Cách hệ thống đánh giá |
|---|---|---|
| Trắc nghiệm (Quiz) | Chọn A, B, C hoặc D | So với phương án đúng đã lưu trong dữ liệu trận |
| Câu đố (Riddle) | Nhập câu trả lời bằng văn bản | So khớp với các đáp án được chấp nhận |
| Sắp xếp chữ tiếng Việt | Sắp xếp các mảnh chữ để tạo đáp án | Kiểm tra từng mảnh được dùng hợp lệ rồi ghép và so đáp án |
| Đoán theo gợi ý (Clues) | Xem gợi ý xuất hiện theo thời gian và nhập đáp án | Chỉ dùng những gợi ý đã được công bố; so câu trả lời hợp lệ |
| Sắp xếp thứ tự (Ordering) | Kéo hoặc sắp xếp các mục | So thứ tự người chơi gửi với thứ tự đúng |
| Đoán từ qua hình ảnh (Image Word) | Xem hình và nhập đáp án | So văn bản với danh sách đáp án hợp lệ |
| Đoán tên bài hát (Song) | Nghe/xem nội dung video và nhập tên | So đáp án; thời gian mở/đóng vẫn do Server quyết định |

Các chế độ sử dụng hình thức nhập khác nhau nhưng có chung nguyên tắc: **Client gửi câu trả lời, Server mới kết luận đúng hoặc sai**. Những thao tác kéo thả, đếm giờ hay phát video ở trình duyệt không thay thế quyền quyết định của máy chủ.

# PHẦN 3. KIẾN TRÚC VÀ CÔNG NGHỆ TRIỂN KHAI

## 3.1. Mô hình Client–Server

MultiGame được tổ chức thành ba thành phần chính. **Client** là giao diện web trong trình duyệt, chịu trách nhiệm hiển thị màn hình, thu nhận thao tác và truyền yêu cầu. **Server** là ứng dụng Spring Boot, quản lý tài khoản, phòng, trận đấu, quyền truy cập, thời gian và các quyết định nghiệp vụ. **Database** là MySQL, lưu các thông tin cần tồn tại lâu dài như tài khoản, bộ câu hỏi, dữ liệu trận, đáp án, lịch sử và thành tích.

Cách tổ chức này giúp nhiều người chơi cùng truy cập một trạng thái chung. Ví dụ, nếu Host bắt đầu trận, chỉ có một quyết định bắt đầu hợp lệ được máy chủ xử lý cho phòng đó. Sau khi trận được tạo thành công, tất cả người tham gia đủ quyền nhận thông báo về cùng trận đấu thay vì mỗi trình duyệt tự tạo một trận riêng.

Server gồm các nhóm trách nhiệm: tiếp nhận yêu cầu, kiểm tra phiên và quyền; quản lý phòng; điều phối trò chơi; kiểm tra và chấm đáp án; ghi dữ liệu; phân phối sự kiện thời gian thực; quản lý lệnh ADMIN; và xử lý thành tích. Việc tách các nhóm này giúp xử lý mạng không bị trộn trực tiếp với công thức tính điểm hoặc các câu lệnh lưu dữ liệu.

## 3.2. Công nghệ được sử dụng

Ứng dụng phía máy chủ dùng **Java 21 và Spring Boot 3.5.16**. Spring Web cung cấp các API HTTP; Spring WebSocket phục vụ kết nối hai chiều; Spring Security xử lý đăng nhập và phân quyền. Spring Data JPA hỗ trợ ánh xạ dữ liệu, đồng thời project sử dụng JDBC ở một số thao tác dữ liệu đặc thù. **MySQL** là hệ quản trị cơ sở dữ liệu; **Flyway** quản lý các thay đổi cấu trúc dữ liệu theo phiên bản.

Giao diện sử dụng HTML, CSS và JavaScript ES modules. Client được phục vụ trực tiếp bởi Spring Server. Điều này phù hợp với phạm vi một máy chủ phục vụ nhiều trình duyệt trong mạng cục bộ; không cần trình chạy frontend riêng cho môi trường sử dụng chính.

## 3.3. Tại sao phải có cả HTTP/REST và WebSocket?

Hai cách truyền thông giải quyết những nhu cầu khác nhau. **HTTP/REST** phù hợp với các hành động mà Client gửi một yêu cầu, Server xử lý rồi trả về kết quả rõ ràng: đăng nhập, tạo phòng, cập nhật bộ câu hỏi, đọc lịch sử hoặc xem hồ sơ cá nhân. **WebSocket** phù hợp với trận đấu đang diễn ra vì Server cần chủ động phát câu hỏi, cập nhật trạng thái và gửi kết quả tới nhiều người mà không phải chờ từng trình duyệt liên tục hỏi lại.

Ví dụ, thao tác tạo phòng có thể hoàn thành bằng một yêu cầu HTTP. Nhưng sau khi vào phòng chờ, nếu một thành viên mới tham gia thì tất cả người đang xem phòng cần nhận danh sách mới. WebSocket giúp máy chủ thông báo thay đổi ngay khi dữ liệu được lưu thành công.

WebSocket tạo ra đường truyền hai chiều, nhưng bản thân nó **không tự bảo đảm** rằng một đáp án không bị ghi trùng hoặc mọi thông báo đều được nhận đúng thời điểm. Những yêu cầu về thứ tự, quyền truy cập, khôi phục và chấm điểm phải được hệ thống xây dựng ở tầng nghiệp vụ.

# PHẦN 4. THIẾT KẾ GIAO TIẾP MẠNG

## 4.1. Quá trình thiết lập kết nối

Người dùng đăng nhập qua HTTP. Sau khi thông tin đăng nhập hợp lệ, Server tạo phiên xác thực để biết những yêu cầu tiếp theo thuộc về tài khoản nào. Khi vào phần tương tác thời gian thực, trình duyệt mở một kết nối WebSocket tới máy chủ. Máy chủ tiếp tục kiểm tra phiên, nguồn kết nối và trạng thái tài khoản trước khi chấp nhận trao đổi thông điệp.

Sau khi kết nối đã được xác thực, máy chủ gửi thông báo rằng phiên realtime sẵn sàng. Từ thời điểm đó, người dùng có thể gửi các lệnh mà vai trò và trạng thái hiện tại cho phép. Khi người dùng đăng xuất hoặc bị cấm, Server không chỉ thay đổi giao diện mà còn kiểm lại quyền sử dụng đối với phiên và kết nối đã có.

## 4.2. Các loại thông điệp trao đổi

Giao thức ứng dụng sử dụng thông điệp JSON truyền qua WebSocket. Bốn nhóm thông điệp chính gồm:

- **Lệnh yêu cầu (Command):** Client yêu cầu thực hiện hành động, chẳng hạn tham gia phòng, gửi đáp án hoặc xin kết nối lại.
- **Xác nhận (ACK):** Server báo yêu cầu đã được chấp nhận theo nghiệp vụ. Riêng đối với trả lời câu hỏi, ACK **không có nghĩa đáp án đúng**.
- **Thông báo từ chối (Error):** Server từ chối yêu cầu, ví dụ người gửi không đủ quyền, gửi sai câu hoặc đã hết thời gian.
- **Sự kiện (Event):** Server chủ động thông báo diễn biến đã xảy ra như mở câu hỏi, kết thúc chấm điểm hoặc cập nhật bảng xếp hạng.

Mỗi yêu cầu có thông tin nhận diện để Server phân biệt các thao tác khác nhau. Nếu Client phải gửi lại một yêu cầu do chưa nhận được xác nhận, nhận diện đó giúp hệ thống biết đây là yêu cầu cũ cần trả lại kết quả chứ không phải một hành động mới.

**Ví dụ dễ hiểu:** Người chơi A chọn đáp án B. Client gửi thông điệp trả lời. Server kiểm tra A có phải người chơi hợp lệ hay không, câu hỏi có đang mở không và A đã trả lời câu này chưa. Nếu hợp lệ, Server lưu đáp án rồi gửi ACK để A biết hệ thống đã nhận. Khi thời gian trả lời kết thúc hoặc đủ điều kiện đóng câu, Server mới chấm điểm và phát sự kiện kết quả cho những người tham gia. Vì vậy không có chuyện chỉ cần thấy ACK là đã biết câu trả lời đúng.

**Phân biệt rõ ba thời điểm khi thuyết minh:** (1) Client gửi Answer: mới là yêu cầu; (2) Server trả ACK: câu trả lời đã được chấp nhận theo điều kiện nghiệp vụ, nhưng chưa phải kết quả đúng/sai; (3) Server công bố Event RESULT sau đóng câu và chấm điểm: lúc này Client mới hiển thị kết quả chính thức. Nhờ tách ba mốc, hệ thống không để một Client đoán trước điểm hoặc tự tính điểm chỉ vì thấy xác nhận đã nhận thông điệp. Trong trường hợp không có ACK do mạng gián đoạn, Client phải kiểm tra receipt/snapshot theo cơ chế ở mục 6.2, thay vì tự giả định yêu cầu thất bại.

## 4.3. Cách Server thông báo tới nhiều người chơi

Một phòng có thể có nhiều kết nối WebSocket đang theo dõi. Khi có thay đổi, Server xác định những người được phép nhận thông báo của phòng hoặc trận đó. Phần thông tin chung, chẳng hạn câu hỏi hiện tại và thời hạn, được gửi tới những người tham gia phù hợp. Thông tin riêng của từng người, chẳng hạn đáp án mà chính người đó đã được chấp nhận hoặc tài nguyên cá nhân, chỉ được gửi cho đúng người.

Đây là cơ chế **phân phối sự kiện tới nhiều Client ở tầng ứng dụng**, không phải sử dụng IP multicast. Thông báo được xếp theo thứ tự trên từng kết nối, nhưng không có bảo đảm rằng mọi thiết bị sẽ nhận và hiển thị chúng đúng cùng một mili giây. Để đánh giá độ chênh hiển thị giữa các máy, cần thực nghiệm LAN và đo riêng.

## 4.4. Luồng xử lý quan trọng trong một câu hỏi

Có thể dùng trình tự sau để giải thích bằng sơ đồ trong báo cáo:

**Bước 1 — Mở câu hỏi:** Server xác định câu hiện tại, những người có nghĩa vụ tham gia chấm điểm và thời hạn hợp lệ. Thông tin được ghi nhận rồi Server phát sự kiện mở câu hỏi.

**Bước 2 — Người chơi gửi đáp án:** Client gửi nội dung trả lời qua WebSocket. Server ghi nhận thời điểm yêu cầu được đưa vào hàng đợi xử lý của trận; thời điểm này được xác định bằng đồng hồ của máy chủ.

**Bước 3 — Kiểm tra và lưu:** Server kiểm tra quyền, câu hỏi, thời gian và việc người chơi đã trả lời hay chưa. Nếu hợp lệ, đáp án được lưu thành công trước khi phát xác nhận.

**Bước 4 — Kết thúc nhận đáp án:** Khi đến hạn hoặc đủ điều kiện đóng sớm, Server chuyển câu sang trạng thái không nhận thêm câu trả lời.

**Bước 5 — Chấm điểm:** Server lấy những dữ kiện đã lưu, chấm theo quy tắc của mode và cập nhật kết quả trong cơ sở dữ liệu.

**Bước 6 — Công bố:** Sau khi kết quả được ghi nhận thành công, Server phát thông báo kết quả và thứ hạng. Mọi Client hiển thị theo dữ liệu Server gửi về.

Trình tự này thể hiện lý do MultiGame là bài toán lập trình mạng: có nhiều yêu cầu đến không đồng thời, thông điệp xác nhận có thể đến muộn, và việc phát kết quả cần gắn với trạng thái đã được máy chủ lưu hợp lệ.

# PHẦN 5. XỬ LÝ ĐỒNG THỜI — NỘI DUNG KỸ THUẬT TRỌNG TÂM

## 5.1. Vấn đề khi nhiều người trả lời cùng lúc

Giả sử phòng có ba người chơi A, B và C. Ở giây cuối cùng, cả ba cùng nhấn gửi đáp án. Trên Server, những thông điệp này có thể được tiếp nhận bởi những luồng xử lý khác nhau. Cũng trong thời điểm đó, bộ định thời chuẩn bị thông báo câu hỏi hết hạn. Nếu mỗi luồng được phép tự cập nhật trận đấu, có thể xuất hiện tình trạng câu bị đóng trước khi một đáp án đã được ghi nhận hợp lệ kịp xử lý, hoặc hai yêu cầu cùng ghi điểm cho một người.

**Race condition là gì trong tình huống này?** Đó là lỗi xuất hiện vì hai công việc cùng đọc hoặc thay đổi trạng thái chung nhưng thứ tự thực thi không được kiểm soát. Chẳng hạn, một luồng đang xác nhận Answer thì luồng khác đã đóng câu và khởi chạy scoring. Nếu không có một điểm điều phối chung, kết quả có thể phụ thuộc ngẫu nhiên vào việc luồng nào được CPU thực hiện trước.

MultiGame giải quyết bằng **hàng đợi công việc riêng cho từng Game**. Answer, các thao tác khác làm thay đổi Game và công việc do timer phát sinh đi qua một trình tự xử lý được Server xác lập. Về mặt nghiệp vụ, một Game không có hai công việc đồng thời cùng tự ý sửa trạng thái của Game đó. Nhờ vậy, Server có thể xác định Answer nào được ghi nhận trước, khi nào câu chuyển trạng thái, rồi mới tới bước chấm điểm.

**Tại sao là hàng đợi theo Game chứ không phải một hàng đợi cho toàn hệ thống?** Vì hai Game khác nhau có trạng thái và thành viên riêng. Việc A trả lời trong Game #1 không cần quyết định thay cho Game #2. Tách phạm vi điều phối giúp bảo vệ thứ tự trong từng trận mà vẫn cho phép nhiều trận tiến triển. Thiết kế khảo sát sử dụng nhóm **bốn worker** phục vụ các hàng đợi Game, không có nghĩa mỗi Game có bốn luồng xử lý song song hoặc hệ thống chỉ phục vụ tối đa bốn Game.

Ví dụ, A và B gửi Answer vào Game #1 trong khi C gửi Answer vào Game #2. Yêu cầu của A và B phải tuân theo thứ tự đã được xác lập của Game #1; yêu cầu của C không cần chờ toàn bộ trận #1 kết thúc mới có thể xử lý. Dù vậy, các worker vẫn dùng chung tài nguyên và có thể phải chờ cơ sở dữ liệu, nên không được khẳng định cơ chế này loại bỏ mọi khả năng tắc nghẽn.

## 5.2. Vì sao Server quyết định thời gian trả lời?

Đồng hồ hiển thị trên trình duyệt có tác dụng giúp người chơi biết mình còn bao lâu, nhưng không được dùng để quyết định tính hợp lệ của đáp án. Đồng hồ ở hai thiết bị có thể lệch nhau, trình duyệt có thể bị treo hoặc người dùng có thể sửa thời gian gửi kèm trong yêu cầu.

Khi một Answer được đưa vào hàng đợi của trận, Server ghi nhận thời điểm theo đồng hồ đơn điệu (*monotonic clock*) và gắn thứ tự tiếp nhận. Hệ thống so sánh thời điểm này với thời điểm câu mở và hạn kết thúc do Server quản lý. Một đáp án chỉ hợp lệ khi nó được ghi nhận **từ lúc câu mở và trước thời hạn kết thúc**. Tới đúng thời điểm deadline thì đã hết hạn.

Ví dụ minh họa: câu hỏi có hạn 30 giây. Nếu đáp án đã được đưa vào hàng đợi ở mốc 29,990 giây nhưng worker xử lý vài chục mili giây sau, hệ thống vẫn xét tính hợp lệ từ mốc 29,990 giây. Ngược lại, nếu yêu cầu chưa được đưa vào hàng đợi cho đến sau hạn, việc Client nói rằng người chơi đã bấm trước đó không đủ để được chấp nhận. Mốc này là **thời điểm được hàng đợi Server tiếp nhận**, không nhất thiết là lúc người dùng nhấn nút hoặc lúc byte đầu tiên đi vào card mạng.

Bộ định thời cũng đưa công việc của mình qua cơ chế hàng đợi thay vì tự ý chấm điểm bên ngoài hàng đợi. Cách này giúp việc đóng câu không vượt qua những yêu cầu đã được xác lập thứ tự xử lý phù hợp.

## 5.3. Ngăn trả lời trùng

Một người chơi có thể nhấn nút gửi nhiều lần hoặc gửi lại yêu cầu khi mạng chập chờn. Nếu Server chỉ dựa vào việc khóa nút ở giao diện thì người dùng vẫn có thể tạo trực tiếp một thông điệp mạng khác. Vì vậy, hệ thống phải kiểm tra ở phía Server rằng một người đã có đáp án được chấp nhận cho câu hỏi hay chưa.

MultiGame sử dụng nhiều lớp bảo vệ. Hàng đợi giúp sắp xếp các thao tác cùng trận; quy tắc nghiệp vụ từ chối một lần trả lời mới khi người đó đã được ghi nhận; cơ sở dữ liệu còn có ràng buộc không cho lưu hai đáp án cho cùng cặp **người chơi trong trận – câu hỏi**. Nếu người chơi gửi lại chính yêu cầu cũ, cơ chế nhận diện yêu cầu có thể trả lại xác nhận ban đầu mà không ghi một đáp án mới.

Điểm cần phân biệt là **không ghi trùng dữ liệu** và **trả lại được thông báo xác nhận cũ** là hai nhu cầu khác nhau. Ràng buộc dữ liệu bảo vệ kết quả, còn cơ chế trả lại xác nhận giúp Client biết thao tác ban đầu đã thành công khi ACK từng bị mất.

## 5.4. Đóng câu sớm và người chơi mất kết nối

Một câu hỏi có thể đóng sớm nếu tất cả những người còn cần chờ đã trả lời hợp lệ. Tuy nhiên, người chơi vừa mất kết nối không nhất thiết phải bị xóa khỏi kết quả của câu đang mở. Hệ thống phân biệt nhóm người có nghĩa vụ được chấm điểm trong câu với nhóm người cần được chờ để quyết định đóng sớm.

Ví dụ, A, B và C đều đang tham gia lúc câu mở. B bị mất mạng. Việc mất kết nối không đồng nghĩa B chưa từng tham gia câu hoặc mọi dữ liệu đã gửi của B phải bị hủy. Hệ thống vẫn lưu nghĩa vụ chấm điểm đã xác định khi mở câu, trong khi quy tắc chờ người offline được áp dụng theo chính sách hiện hành. Ở các câu tiếp theo của một đợt offline, người chơi vắng mặt có thể không còn nằm trong nhóm phải chờ để đóng sớm; điều đó không tự xóa dữ kiện của câu đã mở trước đó.

Cơ chế này tránh một lỗi thường gặp: lấy danh sách người *đang online* làm toàn bộ danh sách cần chấm, rồi làm mất điểm hoặc mất kết quả của người có sự cố kết nối. Quy tắc chi tiết của phiên bản V2 được giữ ở tài liệu kỹ thuật gốc.

## 5.5. Không để một Client chậm làm nghẽn cả trận

Nếu Server gửi trực tiếp thông điệp tới trình duyệt trong khi đang xử lý trận, một kết nối rất chậm có thể làm công việc của những người khác bị trì hoãn. Vì vậy, project tách **quyết định nghiệp vụ** khỏi **thao tác gửi dữ liệu ra mạng**.

Mỗi kết nối có hàng đợi thông điệp gửi riêng và một luồng ghi hoạt động tại từng thời điểm. Công việc xử lý Game xếp thông điệp vào hàng đợi rồi tiếp tục, thay vì phải chờ việc gửi tới từng socket hoàn thành. Hệ thống còn giới hạn bộ đệm và thời gian gửi; kết nối quá chậm hoặc bị kẹt có thể bị đóng để không giữ tài nguyên vô hạn.

Trong những bài kiểm thử đã lưu, tình huống gửi dữ liệu bị kẹt không ngăn các lệnh, timer và những Client khác tiếp tục tiến triển theo điều kiện thử nghiệm. Tuy nhiên, vẫn còn đường xử lý liên quan đến cơ sở dữ liệu có thể phải chờ, nên không được khẳng định toàn bộ hệ thống là *nonblocking*.

# PHẦN 6. MẤT KẾT NỐI, GỬI LẠI YÊU CẦU VÀ PHÂN QUYỀN

## 6.1. Phân biệt mất kết nối với rời phòng

Người chơi mất Wi-Fi hoặc đóng kết nối WebSocket chưa chắc đã chủ động rời phòng. Hệ thống phân biệt **mất kết nối**, **rời phòng** và **hủy trận** thành những tình huống nghiệp vụ riêng. Kết nối bị đứt có thể làm trạng thái hiện diện thay đổi, nhưng không tự xóa tư cách thành viên hoặc những đáp án đã lưu thành công.

Khi người chơi kết nối lại bằng một phiên còn hợp lệ, Server cung cấp bản trạng thái hiện tại phù hợp với quyền của họ: câu hỏi nào đang diễn ra, còn thời gian bao nhiêu, những tài nguyên đã sử dụng và đáp án nào đã được chấp nhận. **Kết nối lại không mở lại câu đã đóng và không cấp thêm thời gian trả lời.**

Nếu tiến trình máy chủ khởi động lại khi trận còn đang diễn ra, cơ chế hiện tại **không tiếp tục câu hỏi đang chơi**. Hệ thống xử lý những trận còn ACTIVE từ phiên chạy cũ theo trạng thái bị gián đoạn và bảo toàn các dữ liệu lịch sử phù hợp. Đây là một giới hạn cần nêu rõ trong báo cáo.

## 6.2. Khi Server đã nhận đáp án nhưng Client chưa thấy ACK

Giả sử A gửi đáp án và Server đã lưu thành công. Đúng lúc Server phát xác nhận, kết nối gặp sự cố nên A không biết yêu cầu đã được ghi nhận hay chưa. Nếu A coi đó là một yêu cầu hoàn toàn mới và gửi nội dung khác, hệ thống phải từ chối để tránh trả lời hai lần cho cùng câu.

Giải pháp được triển khai là gắn mã nhận diện cho yêu cầu. Khi gửi lại **đúng yêu cầu cũ với nội dung cũ**, Server có thể trả lại xác nhận đã ghi nhớ. Nếu sử dụng cùng mã nhận diện nhưng cố ý đổi nội dung, hệ thống từ chối vì không còn là cùng thao tác. Cách xử lý này gọi là *idempotency* trong phạm vi ứng dụng: thử gửi lại không tạo thêm một hiệu ứng nghiệp vụ mới.

Cơ chế xác nhận được giữ trong bộ nhớ với thời hạn và giới hạn dung lượng. Vì vậy không được gọi đây là bảo đảm “mỗi thông điệp chỉ được chuyển đúng một lần trong mọi trường hợp”, nhất là sau khi Server khởi động lại.

## 6.3. Khi người dùng mở kết nối mới trong lúc kết nối cũ chưa đóng

Một tài khoản có thể mở tab mới khi tab cũ vẫn còn kết nối. Nếu sự kiện đóng của tab cũ xảy ra muộn và Server chỉ quản lý theo tài khoản, nó có thể vô tình xóa trạng thái của tab mới. Hệ thống giải quyết bằng cách phân biệt các **thế hệ kết nối** của cùng tài khoản.

Khi có kết nối mới hợp lệ, Server thay kết nối đang được coi là hiện hành. Kết nối cũ không còn được sử dụng để thực hiện thao tác mới hoặc nhận những thông điệp chưa gửi. Sự kiện đóng đến muộn từ kết nối cũ cũng không được phép xóa kết nối mới. Dù vậy, một giao dịch nghiệp vụ đã bắt đầu hợp lệ trước khi thay kết nối vẫn có thể hoàn tất; Client mới cần đọc trạng thái được cập nhật sau đó.

## 6.4. Chứng minh quyền ADMIN thực sự thuộc Server

### 6.4.1. ADMIN quản lý những gì và khác Host ở đâu?

**ADMIN là quyền của tài khoản ở cấp hệ thống**, còn **Host là vai trò quản lý một phòng**. Người làm Host có thể tổ chức trận trong phòng của mình nhưng không vì thế mà được quyền Ban tài khoản người khác. ADMIN có thể sử dụng các thao tác quản trị mà Server cho phép, không cần phải giữ vai trò Host ở từng phòng. Nhóm quyền quản trị được mô tả trong tài liệu này gồm xem dữ liệu quản trị, Kick người chơi khỏi Game, Ban tạm thời, Ban không thời hạn, Unban và xử lý những phòng không còn Host hợp lệ.

Điểm quan trọng của môn Lập trình mạng không nằm ở việc có trang `/admin` đẹp hay không. Vấn đề cần chứng minh là **khi một Client gửi yêu cầu quản trị, Server tự xác thực và phân quyền**, kể cả nếu người dùng không truy cập qua trang ADMIN. Một USER thông thường không thể mở công cụ gửi HTTP request rồi tự biến mình thành ADMIN. Ẩn nút trên Client chỉ cải thiện giao diện; kiểm tra ở Server mới thực sự ngăn hành vi trái quyền.

### 6.4.2. Kick: loại khỏi phạm vi một trận

**Kick** nhằm xử lý một người chơi trong **Game được chỉ định**. Phạm vi của lệnh này không phải toàn bộ tài khoản. Giả sử A đang chơi Game #15 và ADMIN Kick A khỏi Game đó. Sau khi lệnh được Server chấp nhận, A không thể tiếp tục thực hiện các thao tác chơi mới trong Game #15 theo chính sách hiện hành. Những đáp án đã được lưu hợp lệ trước đó không bị tùy ý xóa chỉ vì lệnh Kick xảy ra sau.

Hệ thống phải kiểm tra các điều kiện hợp lệ của đối tượng bị Kick và trạng thái Game tại Server. Không được diễn giải rằng ADMIN có thể Kick bất kỳ ai trong bất kỳ hoàn cảnh nào; danh sách hạn chế cụ thể cần tra tài liệu kỹ thuật gốc nếu báo cáo phải mô tả ở cấp điều kiện chi tiết.

### 6.4.3. Ban tạm thời, Ban không thời hạn và Unban

**Ban là chế tài ở cấp tài khoản.** Khác với Kick, mục tiêu của Ban là ngăn tài khoản bị cấm tiếp tục sử dụng những quyền truy cập trong phạm vi chính sách cấm. Ban tạm thời có thời hạn; Ban không thời hạn không tự kết thúc theo mốc thời gian định trước và cần một thao tác gỡ cấm hợp lệ. **Unban** thay đổi trạng thái cấm, không phải lệnh chấm lại điểm, khôi phục toàn bộ dữ liệu đã bỏ lỡ hoặc hoàn tác mọi sự kiện từng diễn ra lúc bị cấm.

Giả sử A đã đăng nhập từ trước khi bị Ban. Nếu Server chỉ từ chối lần đăng nhập kế tiếp mà vẫn tin tưởng vô điều kiện phiên WebSocket cũ thì biện pháp cấm có thể bị vô hiệu hóa. Vì vậy, project kiểm tra trạng thái tài khoản và quyền sử dụng phiên/kết nối theo những điểm thực thi phù hợp. Trạng thái cấm phải được áp dụng đối với các thao tác mới, không chỉ được hiển thị thành thông báo trên trình duyệt.

### 6.4.4. Orphan Room: vì sao ADMIN cần xử lý phòng không còn Host hợp lệ?

**Orphan Room** có thể hiểu là phòng rơi vào trường hợp không còn Host hợp lệ theo điều kiện hệ thống quy định. Một phòng như vậy có nguy cơ không còn người có quyền thực hiện các thao tác quản lý cần thiết. ADMIN có chức năng xử lý một số trường hợp này để không để dữ liệu và trạng thái phòng bị mắc kẹt vô hạn.

Điều này **không có nghĩa Host vừa mất kết nối WebSocket thì phòng lập tức bị xóa**: mất kết nối tạm thời và mất tư cách Host hợp lệ là hai vấn đề khác nhau. Server phải dựa trên trạng thái phòng và điều kiện cho phép xử lý. Khi cần chứng minh điều kiện cụ thể để đóng một orphan room, nhóm phải đối chiếu tài liệu kỹ thuật gốc; bản thuyết minh này không suy đoán thêm các trường hợp ngoài phạm vi đã ghi nhận.

### 6.4.5. Điều gì xảy ra nếu ADMIN Ban đúng lúc người chơi đang trả lời?

Đây là tình huống có giá trị kỹ thuật cao hơn việc đơn thuần bấm nút Ban. Giả sử câu thứ 5 đang mở; A gửi Answer, gần như đồng thời ADMIN thực hiện Ban A. Hai sự kiện có thể được các luồng khác nhau tiếp nhận, nhưng hệ thống phải xác định **dữ kiện nào đã hợp lệ theo thứ tự xử lý** và **từ thời điểm nào quyền thực hiện thao tác mới bị hạn chế**. Nếu xóa toàn bộ dữ kiện A khỏi câu đang mở, kết quả có thể không còn phản ánh những gì đã được chấp nhận trước đó; nếu chỉ đánh dấu Ban trên giao diện, A có thể tiếp tục gửi yêu cầu từ phiên cũ.

Giải pháp đã được mô tả là giữ dữ kiện/nghĩa vụ chấm điểm của câu đã mở theo quy tắc của Game, đồng thời áp dụng hạn chế với thao tác mới. Với Ban, trạng thái cấm được ghi nhận trước, rồi các tác động tới những kết nối, phòng và Game liên quan được xử lý theo đúng phạm vi. Việc này phối hợp kiểm tra quyền, giao dịch cơ sở dữ liệu và cơ chế điều phối Game; không phải thao tác xóa người chơi khỏi mọi bảng dữ liệu ngay lập tức.

Ví dụ hai trường hợp để phân tích, **không phải kết quả thử nghiệm mới**:

- Nếu Answer của A **đã được Server chấp nhận hợp lệ** trước khi quyền bị thu hồi, không được coi việc Ban xảy ra sau là lý do tự động xóa Answer đó.
- Nếu A gửi **một thao tác mới khi không còn quyền**, Server phải từ chối theo chính sách hiện hành, ngay cả khi giao diện cũ chưa kịp cập nhật.

### 6.4.6. Tại sao HTTP trả thành công không đồng nghĩa mọi socket đã đóng ngay?

Giao tiếp mạng có độ trễ và các thông điệp có thể đã nằm trong quá trình gửi. Khi Server xác nhận lệnh quản trị qua HTTP, điều đó phản ánh **quyết định nghiệp vụ đã được áp dụng theo phạm vi cam kết**, không nhất thiết có nghĩa mọi gói tin đang truyền đều bị thu hồi ngay trong cùng một mili giây. Một thông điệp đã bắt đầu gửi trước khi quyền thay đổi có thể không lấy lại được; những thông điệp chưa bắt đầu gửi phải tiếp tục chịu kiểm tra quyền theo cơ chế hiện hành.

**Cách demo có giá trị:** Dùng tài khoản ADMIN và USER thử nghiệm; chứng minh USER không thể gọi API quản trị chỉ bằng cách biết URL; cho ADMIN Kick một Player ở Game cụ thể và quan sát việc từ chối thao tác mới trong Game đó; tiếp theo kiểm tra tác động của Ban đối với quyền tài khoản và phiên đã tồn tại. Nếu chưa thực hiện demo thực tế, chỉ trình bày đây là **kịch bản đề xuất**, không ghi thành kết quả đã đạt.

# PHẦN 7. QUẢN LÝ DỮ LIỆU, CHẤM ĐIỂM VÀ THÀNH TÍCH

## 7.1. Vì sao cần lưu dữ liệu riêng cho từng trận?

Bộ câu hỏi do người dùng soạn có thể được sửa hoặc xóa mềm sau này. Nếu trận đấu luôn truy cập trực tiếp nội dung nguồn, lịch sử đã chơi có nguy cơ thay đổi theo các lần chỉnh sửa đó. MultiGame tạo **bản chụp nội dung và cấu hình tại lúc bắt đầu trận**. Các câu hỏi, thông tin phục vụ chấm điểm và dữ kiện lịch sử của trận không phụ thuộc vào những thay đổi về sau của bộ nguồn.

Cơ sở dữ liệu còn lưu phòng, thành viên, người chơi, câu hỏi của từng trận, đáp án và kết quả. Những ràng buộc dữ liệu giúp ngăn một số tình huống không hợp lệ như ghi nhiều đáp án cho cùng người và cùng câu hoặc có hai trận ACTIVE trùng một phòng theo quy tắc hiện hành. Các thay đổi cấu trúc dữ liệu được quản lý bằng Flyway; tài liệu nguồn khảo sát các migration V1 đến V13.

## 7.2. Nguyên tắc tính điểm

Tính điểm do Server thực hiện dựa trên dữ kiện câu hỏi và phiên bản luật được giữ theo Game. Cần phân biệt luật Quiz với sáu mode còn lại và không nhầm phiên bản cũ V1 với phiên bản V2.

Ở V2, người chơi bắt đầu từ **0 điểm**, điểm tổng không giảm xuống dưới 0 và không bị loại khỏi trận chỉ vì điểm thấp. Đối với Quiz thông thường không có hiệu ứng, điểm theo kết quả **đúng +10, sai −4, không trả lời −1** trước khi áp dụng điểm sàn. Quiz còn có các hiệu ứng Spin, Hope Star, chuỗi trả lời đúng và cơ chế hồi phục theo bộ luật của phiên bản đang chơi. Các hiệu ứng này có thể làm thay đổi số điểm của một câu, do đó không thể mô tả mọi câu Quiz chỉ bằng ba số trên.

Đối với **sáu mode ngoài Quiz**, câu trả lời đúng được **+10**, riêng câu cuối mỗi chặng đúng được **+20 thay cho +10**; sai hoặc không trả lời được **0**. Không dùng Spin/Star cho các mode này. Bảng chi tiết từng hiệu ứng và ngưỡng điểm cần đối chiếu trong tài liệu nguồn, không tự thay đổi khi viết báo cáo.

Thứ hạng V2 được xác định trước tiên theo tổng điểm giảm dần; khi bằng điểm, hệ thống xét tổng thời gian của những câu trả lời đúng theo chiều tăng dần. Nếu vẫn bằng nhau thì các người chơi có thể đồng hạng theo quy tắc xếp hạng cạnh tranh (ví dụ 1, 1, 3). Mã người dùng chỉ giúp ổn định thứ tự hiển thị, không được dùng để tự ý phá đồng hạng.

## 7.3. Vì sao kết quả trận và phần thưởng được xử lý riêng?

Cần phân biệt **kết quả của một Game** và **quyết định trao thưởng chính thức**. Kết quả Game cho biết ai đạt bao nhiêu điểm, thứ hạng ra sao và trận kết thúc theo lý do nào. Còn quyết định trao thưởng phải xét xem Game đó có đáp ứng chính sách ghi nhận thành tích hay không, chẳng hạn điều kiện về số người tham gia, số câu, loại kết thúc và tư cách hợp lệ. Vì thế, một người đứng hạng nhất trên màn hình kết quả không mặc nhiên có nghĩa người đó đã nhận thêm một cúp chính thức.

**Vì sao không trao cúp ngay trong bước scoring cuối cùng?** Bước chấm điểm có trách nhiệm xác định đúng kết quả trận. Nếu trộn luôn việc xét thưởng và cập nhật thống kê chính thức vào cùng luồng hiển thị, lỗi tạm thời ở phần thưởng có thể gây nhầm lẫn rằng kết quả trận chưa hoàn tất, hoặc một lần retry có nguy cơ phát sinh bản ghi thưởng trùng. MultiGame tách việc **commit kết quả** khỏi việc **quyết định và commit Award**, với cơ chế xử lý lặp lại an toàn theo phạm vi dữ liệu đã lưu.

Ví dụ minh họa: Game #20 đã kết thúc và người chơi nhìn thấy bảng điểm. Hệ thống vẫn đang ở giai đoạn xác định Award. Nếu quá trình xét thưởng bị gián đoạn, việc tải lại trang hoặc thử xử lý tiếp không có nghĩa phải chơi lại trận, chấm lại từng Answer hoặc cấp thêm một cúp mới. Khi đã có quyết định chính thức, hồ sơ và bảng xếp hạng thành tích mới sử dụng dữ kiện thưởng hợp lệ tương ứng.

**Điều người viết báo cáo cần nhấn mạnh:** đây là bài toán tính nhất quán và khả năng retry an toàn giữa hai bước ghi dữ liệu, **không phải một thuật toán tạo huy chương mới**. Những ngưỡng cụ thể cho từng cúp/danh hiệu cần tra lại tài liệu kỹ thuật nguồn trước khi đưa thành bảng quy tắc.

## 7.4. Lịch sử và chơi tiếp trong cùng phòng

Sau khi một Game hoàn thành, Room có thể trở về trạng thái chờ để tổ chức trận mới. Trận tiếp theo có mã định danh và dữ liệu riêng, gồm câu hỏi, điểm, tài nguyên và kết quả độc lập. Lịch sử trận trước không bị ghi đè bởi việc tái sử dụng cùng phòng.

Đây là một điểm kỹ thuật hữu ích: dữ liệu cũ vẫn cần truy cập được, trong khi các thao tác trễ từ trận cũ không được thay đổi trận mới. Bằng chứng kiểm thử lịch sử có kịch bản chơi ba Game liên tiếp trong cùng một Room, nhưng đó không phải bằng chứng hệ thống đã được chạy liên tục nhiều giờ với tải lớn.

# PHẦN 8. ĐIỂM MẠNH VÀ NHỮNG ĐÓNG GÓP KỸ THUẬT

### Hiểu đúng thuật ngữ “novelty” trong đồ án

Trong một báo cáo nghiên cứu, *novelty* thường được hiểu là **tính mới** của giải pháp so với tri thức hoặc công trình đã có. Với đề tài học phần Lập trình mạng, khi chưa thực hiện đối chiếu học thuật rộng rãi, nhóm **không nên tuyên bố đã phát minh thuật toán chưa từng tồn tại**. Cách diễn đạt có căn cứ hơn là **điểm đóng góp kỹ thuật (technical contributions)**: những tình huống khó nhóm đã nhận diện, cơ chế đã thiết kế để giải quyết và bằng chứng kiểm thử cho thấy cơ chế đạt yêu cầu trong phạm vi thử nghiệm.

Một đóng góp có thể trình bày theo bốn câu hỏi: **(1) Bài toán kỹ thuật là gì? (2) Vì sao cách làm đơn giản dễ sai? (3) Project xử lý bằng cơ chế nào? (4) Có bằng chứng gì và giới hạn của bằng chứng là gì?** Bốn đóng góp sau bám theo tài liệu kỹ thuật đã khảo sát; chúng không được tự nâng thành tuyên bố mới ở cấp nghiên cứu khoa học.

## 8.1. Điều phối yêu cầu và thời hạn bằng hàng đợi trên Server

**Bài toán:** Nhiều Player gửi đáp án sát thời hạn, đồng thời timer phát yêu cầu đóng câu. Nếu mỗi luồng tự thao tác trên trạng thái Game thì thứ tự hoàn tất có thể khiến một Answer đến hợp lệ vẫn bị bỏ hoặc cùng câu bị xử lý không nhất quán.

**Cách làm dễ sai:** Dựa vào đồng hồ Client, hoặc kiểm tra thời hạn tại lúc worker bắt đầu xử lý mà không giữ lại thời điểm yêu cầu đã được Server đưa vào hàng đợi. Ví dụ, Answer được hàng đợi tiếp nhận ở giây 29,990 của câu 30 giây, nhưng worker bắt đầu xử lý ở giây 30,020. Lấy mốc 30,020 để loại Answer có thể làm thay đổi kết quả chỉ vì hệ thống bận.

**Cơ chế của MultiGame:** Mỗi Game có hàng đợi điều phối các công việc nghiệp vụ có liên quan, bao gồm Answer và công việc timer. Server ghi nhận thời điểm tiếp nhận bằng đồng hồ đơn điệu và thứ tự để kiểm tra deadline. Worker xử lý theo trình tự đã xác lập; câu chỉ được chấm và công bố sau khi các điều kiện đóng câu và ghi dữ liệu được bảo đảm. Các Game riêng có thể sử dụng nhóm worker chung mà không trộn trạng thái nghiệp vụ.

**Giá trị kỹ thuật:** Tăng tính nhất quán của quyết định chấp nhận Answer trước deadline trong những thứ tự thực thi đã được kiểm tra; giảm phụ thuộc vào việc luồng hệ điều hành nào chạy trước. Đây là giải pháp thiết kế đồng thời và kiểm soát thời gian, không phải cơ chế bảo đảm mọi Client nhìn thấy câu hỏi cùng đúng một mili giây.

**Bằng chứng và giới hạn:** Tài liệu nguồn mô tả các kịch bản race/deadline đã được kiểm thử, nhưng chưa có baseline độc lập để đo riêng tác động của Game Queue so với một kiến trúc khác. Không được kết luận hàng đợi luôn nhanh hơn hay hệ thống không bao giờ xảy ra lỗi đồng thời.

## 8.2. Phục hồi trạng thái và chống lặp thao tác khi kết nối không ổn định

**Bài toán:** Người chơi gửi Answer hoặc Spin; Server đã xử lý nhưng ACK bị mất. Người chơi không biết thao tác có thành công hay không. Sau đó WebSocket bị ngắt, Client mở kết nối mới và có thể đã bỏ lỡ một vài sự kiện.

**Cách làm dễ sai:** Gửi một yêu cầu mới hoàn toàn mỗi lần không nhận ACK, dẫn đến nguy cơ thao tác bị hiểu thành lần thực hiện thứ hai. Hoặc chỉ nối lại WebSocket rồi tiếp tục chờ Event mới, khiến Client thiếu thông tin về câu hỏi/đáp án/trạng thái đã thay đổi trong thời gian offline.

**Cơ chế của MultiGame gồm ba phần bổ trợ nhau:**

1. **Receipt/idempotency:** Một yêu cầu có mã nhận diện và nội dung xác định. Khi Client retry **cùng yêu cầu**, Server có thể trả lại xác nhận của lần đã commit trong phạm vi thời gian lưu receipt, không tạo thêm tác động mới. Cùng mã nhưng đổi nội dung phải bị từ chối.
2. **Reconnect và full snapshot có kiểm soát quyền:** Khi kết nối lại, Client lấy trạng thái hiện hành mà mình được phép xem, thay vì giả định rằng các Event trong lúc mất mạng sẽ được phát lại đầy đủ. Reconnect không đặt lại deadline và không cho trả lời lại câu đã đóng.
3. **Quản lý gửi theo socket:** Kết nối chậm có hàng đợi gửi và giới hạn tài nguyên; công việc nghiệp vụ không phải chờ vô hạn cho một Client, giúp các thao tác quan trọng của những người khác tiếp tục tiến triển theo điều kiện đã thử.

**Tình huống minh họa:** A gửi Answer với requestId X. Server đã lưu, nhưng ACK không tới máy A. Sau reconnect, A gửi lại đúng requestId X với nội dung ban đầu; trong phạm vi receipt còn hiệu lực, Server trả lại kết quả đã nhớ. Đồng thời, snapshot cho A biết câu hiện tại và Answer đã được chấp nhận. Hai cơ chế giải quyết hai việc khác nhau: **receipt giải thích số phận của yêu cầu cũ**, còn **snapshot tái đồng bộ trạng thái hiện tại**.

**Bằng chứng:** Thí nghiệm đối chứng đã lưu ngày 10/10/2026 cố ý loại bỏ receipt/full snapshot cho thấy các tiêu chí khôi phục ở phương án đối chứng đạt **0/30**, trong khi phương án có cơ chế phục hồi đạt **30/30** trên năm kịch bản được liệt kê chi tiết ở mục 9.3. Tuy nhiên, hai phương án đều không quan sát thấy ghi dữ liệu trùng trong tập kiểm tra durable invariants; không được nói rằng phương án đối chứng đã cộng điểm trùng. Thử nghiệm chạy trên **loopback**, không phải đo mất gói Wi-Fi thực tế.

**Giới hạn:** Receipt lưu trong bộ nhớ có thời hạn, nên không phải giao thức exactly-once vô điều kiện. Reconnect không khôi phục câu đang mở sau khi toàn bộ tiến trình Server restart; thời gian phản hồi cũng không được chứng minh luôn thấp hơn phương án đối chứng.

## 8.3. Quyền quản trị vẫn có hiệu lực khi trận đang diễn ra

**Bài toán:** ADMIN ban hoặc kick một tài khoản đúng lúc người đó gửi Answer, timer đóng câu hoặc Server đang chấm điểm. Quyền truy cập thay đổi trong khi dữ kiện nghiệp vụ vẫn đang được xử lý.

**Cách làm dễ sai:** Chỉ ẩn nút gameplay trên giao diện của người bị ban, trong khi phiên WebSocket đã mở vẫn có thể gửi command. Một sai lầm khác là xóa toàn bộ dữ liệu người chơi ngay khi ban, làm mất Answer đã commit hoặc tác động nhầm những dữ kiện của câu đã mở. Nếu dọn dẹp kết nối/phòng bằng thao tác không phân biệt phạm vi, một sự kiện đến muộn còn có thể ảnh hưởng trạng thái của Game khác.

**Cơ chế của MultiGame:** Server thực thi kiểm tra ADMIN và kiểm tra quyền của người chơi, ghi nhận trạng thái cấm ở cấp tài khoản, xử lý tác động đến phòng/Game theo quy tắc và thứ tự nghiệp vụ, đồng thời giữ dữ kiện câu đã mở đã được ghi nhận hợp lệ. Với Kick, phạm vi xử lý là Game được chỉ định; với Ban, phạm vi là tài khoản và các phiên, kết nối, phòng, Game liên quan theo chính sách. Những thông điệp chưa bắt đầu gửi tiếp tục chịu kiểm tra quyền; không suy diễn rằng gói tin đã gửi trước khi thu hồi quyền có thể bị thu hồi từ mạng.

**Tình huống minh họa:** A gửi Answer hợp lệ cho câu thứ 7; gần như đồng thời ADMIN ban A. Nếu Answer đã được chấp nhận theo thứ tự xử lý, hệ thống không được tự xóa dữ kiện đó. Sau khi lệnh ban có hiệu lực, các yêu cầu mới của A phải chịu kiểm tra và từ chối tương ứng. Đây là bài toán duy trì tính đúng đắn khi **thao tác quản trị xen vào các công việc Game**, không phải đơn thuần lập trình một trang CRUD tài khoản.

**Giá trị kỹ thuật và giới hạn:** Khả năng bảo vệ những bất biến nghiệp vụ khi quyền thay đổi đồng thời với gameplay là đóng góp phù hợp với học phần. Các nhận định phải gắn với những trường hợp concurrency đã thực sự kiểm thử; không được tuyên bố hệ thống chống mọi tấn công, đạt bảo mật tuyệt đối hoặc không bao giờ gặp deadlock. Khi mô tả điều kiện Kick/Ban cụ thể ở cấp API, cần đối chiếu source kỹ thuật gốc.

## 8.4. Lưu kết quả và cấp thành tích nhất quán

**Bài toán:** Game đã kết thúc và ghi kết quả nhưng bước xét Award có thể chưa hoàn tất hoặc phải retry. Nếu quy trình xử lý không được phân tách, UI có thể hiểu nhầm bảng điểm là cúp chính thức, còn thao tác thử lại có nguy cơ ghi nhiều phần thưởng.

**Cách làm dễ sai:** Vừa hiển thị Winner là tự động cộng một cúp vào hồ sơ; hoặc mỗi lần tải trang Final Results lại thực hiện tính thưởng, không phân biệt lần đã quyết định với lần cần xử lý tiếp. Những cách này khiến dữ liệu thống kê dài hạn phụ thuộc không đúng vào lượt xem giao diện hoặc số lần gọi lại.

**Cơ chế của MultiGame:** Server commit dữ liệu kết quả trận trước. Bộ xử lý Award kiểm tra điều kiện và đưa ra quyết định thưởng bằng một bước dữ liệu riêng, có chính sách xử lý lại an toàn. Hồ sơ và bảng xếp hạng chính thức tổng hợp từ dữ liệu Award đã được quyết định phù hợp; việc sửa bộ câu hỏi nguồn sau trận không được làm thay đổi bản câu hỏi đã dùng để chấm trong Game đó.

**Tình huống minh họa:** Game #30 đã FINISHED, bảng điểm hiển thị bình thường, nhưng Award còn ở trạng thái chờ. Lần xử lý Award tiếp theo phải dựa trên dữ liệu Game đã lưu, không chấm lại mọi Answer, không sinh cúp mới nếu quyết định trước đó đã được commit. Việc chờ Award không đồng nghĩa Game chưa có kết quả.

**Giá trị kỹ thuật và giới hạn:** Bảo đảm tính nhất quán và retry an toàn cho phần thưởng là đóng góp về **độ tin cậy của dữ liệu**, không phải chứng minh xử lý nhanh hơn. Chưa có benchmark độc lập về thời gian xét thưởng; những điều kiện ngưỡng chính thức cần tra tài liệu nguồn khi đưa vào bảng nghiệp vụ.

## 8.5. Tổ chức nhiều trận trong cùng một phòng mà không trộn trạng thái

Một Room có thể tổ chức liên tiếp nhiều Game, nhưng mỗi Game có định danh, bản câu hỏi, điểm, tài nguyên và dữ kiện riêng. Điều cần phòng ngừa không chỉ là việc người dùng nhìn thấy kết quả cũ: còn có nguy cơ **ACK của một request cũ, timer cũ hoặc thao tác dọn dẹp cũ** được diễn giải nhầm là sự kiện của trận mới nếu không giữ đúng phạm vi Game.

Ví dụ, phòng P đã chơi xong Game #1 và bắt đầu Game #2. Một xác nhận Answer của Game #1 đến muộn không được biến thành Answer của Game #2. Lịch sử Game #1 vẫn phải đọc được, trong khi Gameplay hiện tại của Game #2 sử dụng dữ liệu riêng. Tài liệu kỹ thuật có kịch bản kiểm thử ba Game liên tiếp trong một Room, nhưng chưa chứng minh tải lớn hay vận hành liên tục nhiều giờ.

**Khuyến nghị cho người viết báo cáo:** Chọn bốn nội dung từ mục 8.1 đến 8.4 làm trục trình bày đóng góp kỹ thuật; mục 8.5 là ví dụ bổ trợ. Gọi các nội dung này là **giải pháp thiết kế và triển khai có kiểm chứng**, không tự tuyên bố là phát minh thuật toán mới khi chưa so sánh với công trình nghiên cứu khác.

# PHẦN 9. KIỂM THỬ VÀ THỰC NGHIỆM

## 9.1. Mục đích kiểm thử

Kiểm thử của hệ thống tập trung vào hai nhóm vấn đề. Nhóm thứ nhất xác nhận chức năng và luật chơi hoạt động theo yêu cầu: tài khoản, phòng chơi, các loại câu hỏi, thời gian, đáp án, điểm, kết quả và lịch sử. Nhóm thứ hai kiểm tra tính đúng đắn khi xảy ra tình huống khó: nhiều yêu cầu đồng thời, gửi trùng, mất kết nối, lệnh quản trị xen vào gameplay, giao dịch bị lỗi hoặc tiến trình xử lý thưởng cần thử lại.

Một số bài kiểm thử sử dụng HTTP và WebSocket thật cùng cơ sở dữ liệu MySQL thử nghiệm. Những bài kiểm tra race condition khác dùng đồng hồ hoặc bộ lập lịch có thể điều khiển để tạo chính xác thứ tự cần kiểm tra. Vì vậy, không nên gọi toàn bộ kết quả này là thí nghiệm trên mạng LAN vật lý hay phép đo tốc độ Internet.

## 9.2. Kết quả kiểm thử Java đã lưu của T18

| Nhóm kiểm thử | Số lượt thực thi thành công | Thất bại | Bỏ qua |
|---|---:|---:|---:|
| Nhóm kiểm thử Surefire | 354 | 0 | 0 |
| Nhóm kiểm thử Failsafe | 244 | 0 | 0 |
| **Tổng** | **598** | **0** | **0** |

Dữ liệu trên được thống kê từ **66 bộ báo cáo kiểm thử** thuộc lượt T18 đã lưu. Đây là **598 lượt thực thi**, có thể gồm những lần chạy tham số hóa của cùng một phương thức kiểm thử; không phải 598 người chơi và cũng không phải 598 phương thức khác nhau. Một số kiểm thử giao diện/browser được lưu thành bộ kết quả riêng và không được cộng vào số liệu Java trên.

Kết quả giúp nhóm có căn cứ khi nói hệ thống đã vượt qua các kịch bản kiểm thử ghi nhận ở thời điểm đó. Nó không chứng minh tất cả tình huống đều đúng hoặc toàn bộ source mới nhất đã được chạy lại trong lần khảo sát tài liệu này.

## 9.3. So sánh khả năng khôi phục yêu cầu và trạng thái

Thí nghiệm lịch sử ngày **10/10/2026** dùng mô hình chạy trên cùng một máy (loopback), so sánh hai phương án: phương án có cơ chế nhớ xác nhận và khôi phục đầy đủ trạng thái; phương án đối chứng được **cố ý bỏ hai cơ chế này** nhưng vẫn giữ các biện pháp chặn dữ liệu trùng cơ bản. Mục tiêu là đánh giá tác dụng của phần cơ chế bị bỏ, không phải so sánh với trò chơi thương mại khác.

| Kịch bản được thử | Phương án đối chứng | Phương án có cơ chế phục hồi |
|---|---:|---:|
| Lấy lại xác nhận Answer khi ACK bị bỏ qua | 0/30 | 30/30 |
| Lấy lại xác nhận Spin khi ACK bị bỏ qua | 0/30 | 30/30 |
| Khôi phục trạng thái sau khi gửi Answer | 0/30 | 30/30 |
| Khôi phục trạng thái trong giai đoạn chọn tài nguyên | 0/30 | 30/30 |
| Gửi lại yêu cầu trong tình huống trận đã kết thúc | 0/30 | 30/30 |

Các tỷ lệ trên là số lần đạt **tiêu chí khôi phục riêng của từng kịch bản**, không phải số lần hoàn thành cả trận. Trong hai kịch bản reconnect, phương án đối chứng có 30 trường hợp trạng thái không khớp còn phương án có khôi phục đầy đủ có 0. Đồng thời, cả hai phương án đều không quan sát thấy việc ghi tác động dữ liệu trùng trong tập kiểm tra durable invariants đã ghi. Do đó, **không được diễn giải rằng phương án đối chứng đã cộng điểm trùng**.

Kết quả hỗ trợ nhận định rằng việc lưu xác nhận đã commit và gửi lại trạng thái phù hợp khi reconnect cải thiện khả năng phục hồi trong những kịch bản được thiết kế. Vì chạy trên loopback và dùng tình huống ACK bị bỏ qua có kiểm soát, nó **không phải** một phép thử mất gói Wi-Fi thực tế.

## 9.4. Số liệu độ trễ và ý nghĩa của chúng

Trong thí nghiệm cùng ngày 10/10, một phòng được thử với mức ba và bốn Client. Thời gian phía Client được tính từ lúc gửi thao tác tới khi có phản hồi tương ứng; thời gian phía Server gồm việc chờ hàng đợi và xử lý nội bộ đến lúc công việc hoàn tất. Đây là hai phạm vi đo khác nhau và không phải phép đo một chiều trên đường truyền mạng.

| Số Client | Phương án | Trung vị thời gian phản hồi Client (ms) | Mốc 95% thời gian phản hồi Client (ms) |
|---|---|---:|---:|
| 3 | Đối chứng | 29,149 | 62,901 |
| 3 | Có cơ chế phục hồi | 32,739 | 71,558 |
| 4 | Đối chứng | 44,444 | 81,629 |
| 4 | Có cơ chế phục hồi | 42,718 | 83,513 |

*Cách đọc:* “Trung vị” (p50) là mức giữa của mẫu đo; p95 là mốc phân vị 95% theo phương pháp mà thí nghiệm đã sử dụng. Các con số trong bảng dùng dấu phẩy thập phân theo cách viết tiếng Việt; đơn vị là mili giây.

Kết quả **không cho thấy phương án có cơ chế phục hồi luôn nhanh hơn**. Với ba Client, cả p50 và p95 phía Client đều cao hơn phương án đối chứng; với bốn Client, p50 thấp hơn nhưng p95 vẫn cao hơn. Thí nghiệm lịch sử ngày 06/10 còn có mức 20 Client với p95 xử lý phía Server của phương án có cơ chế phục hồi cao hơn đối chứng (**451,814 ms so với 285,865 ms**). Không được ghép hai ngày đo thành cùng một lần chạy vì cấu hình và snapshot lịch sử khác nhau.

Điều có thể rút ra là **cơ chế phục hồi đem lại lợi ích rõ hơn về tính tin cậy trong các tình huống đã kiểm tra, còn đánh đổi về độ trễ cần tiếp tục đo và tối ưu**. Chưa có đủ dữ liệu để xác định năng lực phục vụ tối đa, độ trễ giữa nhiều thiết bị thật hoặc nguyên nhân chính xác của những mẫu chậm.

## 9.5. Những thực nghiệm vẫn còn thiếu

Tài liệu nguồn chưa có bằng chứng thực hiện đầy đủ một buổi chơi trên **hai hoặc ba máy vật lý khác nhau qua LAN**; các lần chạy nhiều browser context trên cùng máy không thay thế được bằng chứng này. Nhóm còn cần đo hoặc quan sát cụ thể: độ chênh nhận cùng sự kiện giữa các Client, thời gian giao diện phục hồi sau reconnect, tải nhiều phòng đồng thời, tài nguyên CPU/RAM và hiệu quả khi thử trong thời gian dài.

Nếu chưa thực hiện các phép đo trên, báo cáo phải ghi đúng là **chưa đánh giá được**, không tự điền số minh họa. Không dùng dữ liệu sáu trận mô phỏng để dựng giao diện như dữ liệu benchmark về mạng.

# PHẦN 10. ĐÁNH GIÁ HỆ THỐNG VÀ GIỚI HẠN

## 10.1. Những kết quả đạt được theo phạm vi bằng chứng hiện có

MultiGame đã triển khai luồng sử dụng từ quản lý tài khoản, tạo phòng, chơi nhiều chế độ và nhận kết quả đến lịch sử, thành tích và chơi tiếp trong cùng phòng. Các cơ chế Server-authoritative, hàng đợi theo trận, xác nhận thao tác, khôi phục khi mất kết nối và kiểm soát quyền ADMIN được thiết kế để bảo vệ trạng thái khi có nhiều Client tương tác.

Điểm mạnh có thể trình bày là không chỉ xây giao diện chơi trắc nghiệm, mà còn giải quyết những vấn đề điển hình của hệ thống mạng: thứ tự xử lý yêu cầu, tính hợp lệ của đáp án trước deadline, kết nối gián đoạn, thông báo đến muộn và tính nhất quán giữa dữ liệu đã lưu với thông tin hiển thị. Những nhận định này có bằng chứng từ code và kết quả kiểm thử lịch sử ở tài liệu kỹ thuật gốc.

## 10.2. Những giới hạn cần trình bày trung thực

Hệ thống sử dụng một tiến trình Server. Các hàng đợi Game và thông tin kết nối đang hoạt động được quản lý trong bộ nhớ; vì vậy, khởi động lại máy chủ không thể tiếp tục nguyên vẹn câu hỏi đang mở. Kết quả, lịch sử và những dữ kiện đã lưu ở MySQL có cơ chế quản lý riêng nhưng không đồng nghĩa toàn bộ trạng thái runtime được khôi phục.

Hệ thống dùng một cơ sở dữ liệu MySQL, một số đoạn xử lý có thể phải chờ DB; ảnh và video được lưu trên filesystem cục bộ. Hệ thống chưa có cơ sở để khẳng định chạy tốt dưới tải Internet lớn, có khả năng tự chuyển dự phòng, đạt độ trễ cố định hoặc có bảo mật tuyệt đối. Các thí nghiệm độ trễ hiện còn hạn chế về số lượt, cấu hình một phòng và môi trường loopback.

Những giới hạn trên không làm mất giá trị của đồ án; điều cần thiết là phân biệt **giới hạn của thiết kế thực tế** với **chức năng có triển khai nhưng chưa được đo hoặc demo trong điều kiện cụ thể**.

## 10.3. Hướng phát triển tiếp theo

Một số hướng phù hợp là tổ chức demo LAN nhiều thiết bị và lưu chứng cứ, lặp lại phép đo trên phiên bản cuối, đo độ trễ cập nhật giữa các Client, đánh giá khi nhiều phòng hoạt động đồng thời và theo dõi mức sử dụng tài nguyên. Khi có dữ liệu, nhóm mới có thể phân tích chính xác điểm nghẽn và lựa chọn phương án tối ưu.

Nếu sau này muốn hỗ trợ tiếp tục trận đang diễn ra sau khi Server restart hoặc chạy nhiều Server, cần thiết kế thêm cơ chế lưu trạng thái runtime bền vững và điều phối các phiên phân tán. Đây là công việc tương lai, **không phải chức năng đã hoàn thành** của phạm vi hiện tại.

# PHẦN 11. GỢI Ý TRÌNH BÀY TRONG BÁO CÁO VÀ BUỔI BẢO VỆ

## 11.1. Nội dung nên đưa vào thân báo cáo

Từ bản thuyết minh này, nhóm có thể xây dựng bài báo cáo theo cấu trúc giảng viên yêu cầu: giới thiệu bài toán; các khái niệm và công trình liên quan; giải pháp đề xuất; kiến trúc hệ thống; thiết kế truyền thông; cơ chế đồng thời và khôi phục; triển khai; thực nghiệm; thảo luận; đóng góp; hạn chế và kết luận.

Thân báo cáo nên có **một sơ đồ kiến trúc Client–Server**, **một sơ đồ tuần tự từ gửi Answer tới nhận kết quả**, **một sơ đồ reconnect hoặc xử lý ADMIN**, **một sơ đồ cơ sở dữ liệu rút gọn** và **một số bảng thực nghiệm có phần phân tích bằng lời**. Các sơ đồ phải được vẽ dựa đúng luồng đã triển khai; không cần chép danh sách toàn bộ class Java vào phần thuyết minh.

Tên endpoint, cấu trúc JSON đầy đủ, tên class/method, danh sách từng bộ test, migration, mã commit và đường dẫn file không cần xuất hiện dày đặc trong thân báo cáo. Nếu giảng viên yêu cầu chứng minh chi tiết, người viết tra tài liệu kỹ thuật gốc và chọn trích dẫn ngắn hoặc chuyển sang phụ lục.

## 11.2. Các tình huống demo có giá trị nhất

Một kịch bản demo phù hợp gồm ba tài khoản Player độc lập. Host tạo và mở phòng; các Client tham gia qua mã phòng, nhận cùng diễn biến; Server mở câu; các Player gửi đáp án gần nhau; kết quả được phát sau bước chấm điểm. Sau đó thử ngắt kết nối một Player để quan sát việc giữ thời hạn và khôi phục trạng thái; tiếp tục thử thao tác Kick/Ban trên tài khoản thử nghiệm và chứng minh Server từ chối yêu cầu trái quyền, kể cả khi không sử dụng nút giao diện thông thường.

Khi trận kết thúc, trình bày lịch sử, thành tích nếu đủ điều kiện và khả năng chơi trận mới trong cùng phòng. Cần có bằng chứng riêng nếu muốn ghi đã demo trên mạng LAN vật lý; thử nhiều tab hoặc nhiều cửa sổ trên một máy chỉ xác minh những phiên Client độc lập trong cùng môi trường.

## 11.3. Những câu hỏi cần giải thích được

**Tại sao không để Client tự chấm điểm?** Vì dữ liệu và thời gian phía Client có thể bị sửa, còn nhiều Client cần một quyết định thống nhất. Server giữ câu hỏi, luật và kết quả chính thức.

**Tại sao dùng WebSocket?** Vì trận đấu cần Server chủ động gửi câu hỏi, thay đổi trạng thái và kết quả tới nhiều người theo thời gian thực. REST vẫn phù hợp với đăng nhập, cấu hình và đọc dữ liệu.

**Nếu hai người gửi đáp án sát hạn thì xử lý thế nào?** Server ghi nhận thời gian và thứ tự tiếp nhận khi đưa vào hàng đợi Game; timer cũng tuân theo cơ chế điều phối; việc xử lý muộn không tự thay đổi thời điểm đã ghi nhận.

**Nếu đã lưu Answer nhưng chưa nhận ACK thì sao?** Client có thể gửi lại đúng yêu cầu cũ để nhận xác nhận đã lưu, trong phạm vi cơ chế nhận diện và thời hạn lưu receipt; không ghi đáp án thứ hai.

**Mất kết nối có làm mất điểm không?** Các dữ kiện đã commit còn trong cơ sở dữ liệu. Khi reconnect, Client lấy trạng thái hiện tại; nhưng hệ thống không bảo đảm lưu một yêu cầu chưa hề đến Server.

**ADMIN thực sự có quyền gì?** Quyền quản trị được Server kiểm tra cho cả HTTP và các thao tác liên quan. Người dùng không thể vượt qua kiểm tra chỉ bằng cách gọi thẳng API hay tự tạo thông điệp WebSocket.

**Điểm mới kỹ thuật là gì?** Những đóng góp phù hợp để trình bày là phối hợp hàng đợi và deadline phía Server, khôi phục request/trạng thái, kiểm soát ADMIN dưới concurrency và quyết định phần thưởng nhất quán. Đây là các giải pháp triển khai có bài kiểm thử, chưa đủ bằng chứng để tuyên bố một thuật toán nghiên cứu hoàn toàn mới.

**Khi thầy hỏi “novelty là gì, có phải thuật toán mới không?”** Có thể trả lời rằng nhóm không tuyên bố phát minh một thuật toán mới. Đóng góp của đồ án là cách tổ chức và phối hợp các cơ chế đã biết để giải quyết một bài toán mạng cụ thể: chấp nhận Answer đúng hạn dù worker xử lý trễ; retry không tạo tác động mới; reconnect tái đồng bộ trạng thái; lệnh ADMIN có hiệu lực trong khi trận đang chạy; và kết quả/Award không bị ghi trùng. Những điểm này được giải thích chi tiết ở Phần 8 và được giới hạn bởi bằng chứng thử nghiệm ở Phần 9.

# PHẦN 12. NHỮNG THÔNG TIN NHÓM CẦN BỔ SUNG TRƯỚC KHI VIẾT BÁO CÁO CHÍNH THỨC

Tài liệu nguồn hiện chưa thay thế được bốn loại thông tin cần thu thập hoặc hoàn thiện riêng:

1. **Tài liệu tham khảo học thuật thực tế** cho phần kiến thức nền và công trình liên quan (HTTP, WebSocket, mô hình Client–Server, concurrency, reliability). Không tự tạo tên bài báo hoặc trích dẫn không có thật.
2. **Hình giao diện và sơ đồ đã xuất thành hình** để đưa vào Word. Hình phải phản ánh màn hình/cơ chế thật, không dùng ảnh mô phỏng chưa được chú thích.
3. **Biên bản thực nghiệm LAN trên thiết bị vật lý** nếu nhóm muốn khẳng định đã chạy qua Wi-Fi/LAN. Ghi rõ thiết bị, cấu hình, số người chơi, kịch bản và kết quả quan sát.
4. **Thông tin hành chính của nhóm và báo cáo** như thành viên, phân công, mục lục chính thức, định dạng nộp và những yêu cầu cụ thể của giảng viên.

**Nguyên tắc sử dụng:** Người viết báo cáo có thể lấy mạch diễn giải, tình huống và cách giải thích cơ chế từ tài liệu này rồi biên soạn theo đúng cấu trúc của giảng viên. Khi cần đưa con số, công thức, điều kiện thưởng hoặc một nhận định khó kiểm chứng vào bài, phải đối chiếu bản `REPORT_SOURCE_EVIDENCE(2).md`. Không tự sửa số liệu cho “đẹp” và không biến kết quả kiểm thử lịch sử thành tuyên bố đã đo trên bản cuối.

---

## GHI CHÚ VỀ THUẬT NGỮ

- **Client:** Ứng dụng chạy trong trình duyệt của người sử dụng.
- **Server:** Ứng dụng máy chủ tiếp nhận yêu cầu, kiểm tra luật và phát trạng thái chính thức.
- **REST/HTTP:** Cơ chế gọi API theo yêu cầu và phản hồi.
- **WebSocket:** Kết nối duy trì hai chiều, cho phép Client và Server gửi thông tin chủ động.
- **ACK:** Thông báo xác nhận một yêu cầu đã được chấp nhận; không đồng nghĩa đáp án đúng.
- **Event:** Thông báo về thay đổi trạng thái từ Server.
- **Deadline:** Thời hạn xử lý hợp lệ của một giai đoạn hoặc câu hỏi.
- **Concurrency:** Nhiều công việc/yêu cầu diễn ra trong cùng khoảng thời gian, có thể tác động đến trạng thái chung.
- **Race condition:** Kết quả không đúng hoặc không ổn định khi nhiều thao tác phụ thuộc vào thứ tự chạy ngoài kiểm soát.
- **Queue:** Hàng đợi công việc để sắp xếp xử lý.
- **Commit:** Thời điểm giao dịch cơ sở dữ liệu đã ghi nhận thay đổi thành công.
- **Idempotency:** Cơ chế để gửi lại cùng thao tác không tạo thêm hiệu ứng nghiệp vụ ngoài ý muốn.
- **Reconnect:** Thiết lập lại kết nối và lấy trạng thái cần thiết sau gián đoạn.
- **Snapshot:** Bản trạng thái hoặc nội dung được chụp ở một thời điểm, dùng để xử lý nhất quán.
- **FIFO:** Thứ tự vào trước được xử lý/gửi trước trong phạm vi hàng đợi áp dụng.
- **Baseline/Ablation:** Phương án đối chứng trong thí nghiệm; ở đây là phương án cố ý bỏ cơ chế receipt/full snapshot nhằm đo tác dụng của chính các cơ chế đó.
- **p50/p95:** Các mốc phân vị thống kê của tập thời gian đo, không phải thời gian cố định cho mọi lần xử lý.
